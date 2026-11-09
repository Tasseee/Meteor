package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

/**
 * Opt-in PvP trapper flow. It warns the selected player before sending /tpahere,
 * waits for that player to arrive, builds a 1x2 interior trap from full-cube
 * blocks in the hotbar, and only attacks while the target is enclosed and a
 * vanilla critical-hit window is available.
 *
 * The server must support /msg and /tpahere. No packet spoofing or anti-cheat
 * bypass is performed; server-side placement/attack rules remain authoritative.
 */
public final class TrapperBot extends Module {
    private enum Phase { IDLE, WARNING, WAITING_FOR_ARRIVAL, BUILDING, FIGHTING, COOLDOWN }

    private final NumberSetting inviteDelay = add(new NumberSetting("Warn-Vorlauf (Ticks)", 10, 5, 100, 1));
    private final NumberSetting arrivalTimeout = add(new NumberSetting("Wartezeit (Sek.)", 60, 10, 180, 5));
    private final NumberSetting cooldownSeconds = add(new NumberSetting("Spieler-Cooldown (Sek.)", 15, 5, 300, 5));
    private final NumberSetting fightRange = add(new NumberSetting("Kampfreichweite", 3.0, 2.5, 4.5, 0.1));
    private final BoolSetting sendWarning = add(new BoolSetting("Warnung vor TPAHere", true));
    private final BoolSetting buildTrap = add(new BoolSetting("1x2-Falle bauen", true));
    private final BoolSetting requireTrap = add(new BoolSetting("Nur in Falle angreifen", true));
    private final BoolSetting criticalOnly = add(new BoolSetting("Nur Crits", true));
    public final ListSetting blacklist = add(new ListSetting("Blacklist"));

    private final Random random = new Random();
    private final Set<String> attemptedThisSession = new HashSet<>();
    private Phase phase = Phase.IDLE;
    private String targetName = "";
    private int timer;
    private int buildIndex;
    private int buildDelay;
    private int buildPasses;
    private int previousSlot = -1;
    private int blockSlot = -1;
    private PlayerEntity target;
    private List<BlockPos> buildOrder = List.of();

    public TrapperBot() {
        super("TrapperBot", "Warnt Spieler, sendet /tpahere und kaempft nach Annahme mit Crits.", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        phase = Phase.IDLE;
        timer = 0;
        targetName = "";
        attemptedThisSession.clear();
        buildPasses = 0;
    }

    @Override
    protected void onDisable() {
        releaseControls();
        restoreSlot();
        resetTarget();
        phase = Phase.IDLE;
    }

    @Override
    public void onWorldLeave() {
        resetSession();
    }

    /** Clears all per-connection state when leaving a server/world. */
    public void resetSession() {
        releaseControls();
        restoreSlot();
        resetTarget();
        attemptedThisSession.clear();
        buildPasses = 0;
        phase = Phase.IDLE;
    }

    public String status() {
        return phase.name() + (targetName.isEmpty() ? "" : " | " + targetName);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.getNetworkHandler() == null || mc.interactionManager == null
                || mc.currentScreen != null || !player.isAlive()) {
            releaseControls();
            return;
        }

        switch (phase) {
            case IDLE -> chooseAndWarn();
            case WARNING -> sendRequest();
            case WAITING_FOR_ARRIVAL -> waitForArrival();
            case BUILDING -> buildOneBlock();
            case FIGHTING -> fightTarget();
            case COOLDOWN -> {
                releaseControls();
                if (--timer <= 0) phase = Phase.IDLE;
            }
        }
    }

    private void chooseAndWarn() {
        List<String> candidates = new ArrayList<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String name = entry.getProfile().name();
            if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9_]{3,16}")) continue;
            if (name.equalsIgnoreCase(mc.player.getName().getString()) || blacklist.has(name)) continue;
            if (attemptedThisSession.contains(name.toLowerCase(java.util.Locale.ROOT))) continue;
            if (isFriendName(name)) continue;
            candidates.add(name);
        }
        if (candidates.isEmpty()) {
            timer = 40;
            phase = Phase.COOLDOWN;
            return;
        }
        Collections.shuffle(candidates, random);
        targetName = candidates.get(0);
        attemptedThisSession.add(targetName.toLowerCase(java.util.Locale.ROOT));

        // Give the recipient an explicit warning before the teleport request.
        if (sendWarning.get()) {
            mc.getNetworkHandler().sendChatCommand("msg " + targetName
                    + " Achtung: PvP-/Trapper-Event. Du koenntest in eine Falle geraten. Nur annehmen, wenn du kaempfen moechtest.");
        }
        timer = sendWarning.get() ? inviteDelay.getInt() : 0;
        phase = Phase.WARNING;
    }

    private void sendRequest() {
        if (timer-- > 0) return;
        mc.getNetworkHandler().sendChatCommand("tpahere " + targetName);
        timer = arrivalTimeout.getInt() * 20;
        phase = Phase.WAITING_FOR_ARRIVAL;
    }

    private void waitForArrival() {
        target = findWorldPlayer(targetName);
        if (target != null && target.isAlive() && target.squaredDistanceTo(mc.player) <= 25.0) {
            restoreSlot();
            timer = 100;
            buildOrder = createTrapOrder(target.getBlockPos());
            buildIndex = 0;
            buildDelay = 0;
            buildPasses = 0;
            if (buildTrap.get() && (blockSlot = findBuildBlock()) >= 0) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(blockSlot);
                phase = Phase.BUILDING;
            } else {
                if (buildTrap.get() && requireTrap.get()) {
                    mc.player.sendMessage(Text.literal("[Nightfall] TrapperBot: Keine geeigneten vollen Bloecke in der Hotbar; Crit-Angriff wartet auf eine Falle."), false);
                }
                phase = Phase.FIGHTING;
            }
            return;
        }
        if (--timer <= 0) startCooldown(20 * 20);
    }

    private void buildOneBlock() {
        if (target == null || !target.isAlive() || target.getEntityWorld() != mc.world) {
            startCooldown(20 * 20);
            return;
        }
        if (buildDelay-- > 0) return;
        if (buildIndex >= buildOrder.size()) {
            if (isEnclosed(target)) {
                restoreSlot();
                phase = Phase.FIGHTING;
            } else if (++buildPasses < 3 && findBuildBlock() >= 0) {
                buildOrder = createTrapOrder(target.getBlockPos());
                buildIndex = 0;
                buildDelay = 8;
                blockSlot = findBuildBlock();
                mc.player.getInventory().setSelectedSlot(blockSlot);
            } else {
                restoreSlot();
                mc.player.sendMessage(Text.literal("[Nightfall] TrapperBot: Falle konnte nicht vollstaendig gebaut werden; Ziel wird nicht angegriffen."), false);
                startCooldown(20 * 20);
            }
            return;
        }
        BlockPos place = buildOrder.get(buildIndex++);
        if (mc.world.getBlockState(place).isReplaceable()) placeAgainstNeighbour(place);
        buildDelay = 3; // limit placement rate and allow the server to acknowledge blocks
    }

    private boolean placeAgainstNeighbour(BlockPos place) {
        if (blockSlot < 0 || blockSlot > 8) return false;
        ClientPlayerEntity player = mc.player;
        for (Direction face : Direction.values()) {
            BlockPos support = place.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            BlockHitResult result = new BlockHitResult(hit, face, support, false);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, result);
            player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private void fightTarget() {
        if (target == null || !target.isAlive() || target.getEntityWorld() != mc.world) target = findWorldPlayer(targetName);
        if (target == null || !target.isAlive()) {
            restoreSlot();
            startCooldown(cooldownSeconds.getInt() * 20);
            return;
        }

        ClientPlayerEntity player = mc.player;
        if (player.distanceTo(target) > fightRange.get() + 1.0) {
            // The target left the trap area; do not chase across the world.
            releaseControls();
            if (--timer <= 0) startCooldown(20 * 20);
            return;
        }
        timer = 100;

        Vec3d eye = player.getEyePos();
        double dx = target.getX() - eye.x;
        double dy = target.getBodyY(0.5) - eye.y;
        double dz = target.getZ() - eye.z;
        player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

        boolean trapped = isEnclosed(target);
        if (requireTrap.get() && !trapped) {
            // Rebuild missing pieces slowly rather than attacking an untrapped player.
            buildOrder = createTrapOrder(target.getBlockPos());
            buildIndex = 0;
            buildDelay = 0;
            buildPasses = 0;
            if (buildTrap.get() && findBuildBlock() >= 0) {
                blockSlot = findBuildBlock();
                if (previousSlot < 0) previousSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(blockSlot);
                phase = Phase.BUILDING;
            }
            return;
        }

        mc.options.sprintKey.setPressed(false);
        player.setSprinting(false);
        if (player.isOnGround()) {
            mc.options.jumpKey.setPressed(true);
        } else {
            mc.options.jumpKey.setPressed(false);
        }

        boolean critReady = !player.isOnGround() && player.fallDistance > 0.0f && !player.isClimbing()
                && !player.isTouchingWater() && !player.hasVehicle() && !player.isSprinting()
                && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
        if (player.distanceTo(target) <= fightRange.get() && player.canSee(target)
                && player.getAttackCooldownProgress(0.5f) >= 0.95f
                && (!criticalOnly.get() || critReady)) {
            mc.interactionManager.attackEntity(player, target);
            player.swingHand(Hand.MAIN_HAND);
        }
    }

    private List<BlockPos> createTrapOrder(BlockPos feet) {
        List<BlockPos> positions = new ArrayList<>(9);
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        // Four walls at feet level, then four at head level, with a roof.
        for (Direction side : sides) positions.add(feet.offset(side));
        for (Direction side : sides) positions.add(feet.up().offset(side));
        positions.add(feet.up(2));
        return positions;
    }

    private boolean isEnclosed(PlayerEntity entity) {
        BlockPos feet = entity.getBlockPos();
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (mc.world.getBlockState(feet.offset(side)).getCollisionShape(mc.world, feet.offset(side)).isEmpty()) return false;
            BlockPos headSide = feet.up().offset(side);
            if (mc.world.getBlockState(headSide).getCollisionShape(mc.world, headSide).isEmpty()) return false;
        }
        BlockPos roof = feet.up(2);
        return !mc.world.getBlockState(roof).getCollisionShape(mc.world, roof).isEmpty();
    }

    private int findBuildBlock() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;
            BlockState state = blockItem.getBlock().getDefaultState();
            if (state.getCollisionShape(mc.world, mc.player.getBlockPos()).isEmpty()) continue;
            if (!state.isFullCube(mc.world, mc.player.getBlockPos())) continue;
            return slot;
        }
        return -1;
    }

    private PlayerEntity findWorldPlayer(String name) {
        if (name == null || name.isEmpty() || mc.world == null) return null;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && p.getName().getString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private boolean isFriendName(String name) {
        return ModuleManager.get(KillAura.class).friends.has(name);
    }

    private void startCooldown(int ticks) {
        releaseControls();
        restoreSlot();
        timer = Math.max(1, ticks);
        phase = Phase.COOLDOWN;
    }

    private void resetTarget() {
        target = null;
        targetName = "";
        buildOrder = List.of();
        buildIndex = 0;
        buildPasses = 0;
        blockSlot = -1;
        previousSlot = -1;
        timer = 0;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
        blockSlot = -1;
    }

    private void releaseControls() {
        if (mc.options == null) return;
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }
}

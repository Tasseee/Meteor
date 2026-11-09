package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds an upper obsidian enclosure and keeps a cobweb at the target's head. */
public final class ObiTrapBot extends Module {
    private final StringSetting serverFilter = add(new StringSetting("Server-Filter (leer = alle)", "", 64));
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 7, 3, 12, 1));
    private final NumberSetting placeRange = add(new NumberSetting("Platzier-Reichweite", 5.0, 2.5, 6, 0.5));
    private final NumberSetting placeDelay = add(new NumberSetting("Bau-Intervall (Ticks)", 1, 1, 10, 1));
    private final NumberSetting attackRange = add(new NumberSetting("Angriffs-Reichweite", 3.2, 2.5, 4.5, 0.1));
    private final BoolSetting attack = add(new BoolSetting("Nach Falle angreifen", true));

    private PlayerEntity target;
    private BlockPos anchor;
    private List<BlockPos> order = List.of();
    private int index, timer, previousSlot = -1;

    public ObiTrapBot() {
        super("ObiTrapBot", "Baut eine obere Obsidian-Falle ohne Fuß-Ring und hält ein Cobweb am Kopf des Ziels.", Category.COMBAT);
    }

    @Override protected void onDisable() { reset(); }
    @Override public void onWorldLeave() { reset(); }

    private void reset() {
        restoreSlot();
        target = null;
        anchor = null;
        order = List.of();
        index = 0;
        timer = 0;
    }

    private boolean serverAllowed() {
        String filter = serverFilter.get().trim().toLowerCase(Locale.ROOT);
        if (filter.isEmpty()) return true;
        var entry = mc.getCurrentServerEntry();
        return entry != null && entry.address != null && entry.address.toLowerCase(Locale.ROOT).contains(filter);
    }

    private PlayerEntity findTarget(ClientPlayerEntity p) {
        PlayerEntity best = null;
        double bestDistance = targetRange.get() * targetRange.get();
        var friends = ModuleManager.get(KillAura.class).friends;
        for (PlayerEntity candidate : mc.world.getPlayers()) {
            if (candidate == p || !candidate.isAlive() || candidate.isSpectator()
                || friends.has(candidate.getName().getString())) continue;
            double d = p.squaredDistanceTo(candidate);
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return best;
    }

    /** Only the upper side ring and roof are built; the feet-level ring is deliberately omitted. */
    private List<BlockPos> trapOrder(BlockPos feet) {
        List<BlockPos> out = new ArrayList<>(5);
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            out.add(feet.up().offset(side));
        }
        out.add(feet.up(2));
        return out;
    }

    private boolean enclosed(PlayerEntity e) {
        BlockPos feet = e.getBlockPos();
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos upper = feet.up().offset(side);
            if (mc.world.getBlockState(upper).getCollisionShape(mc.world, upper).isEmpty()) return false;
        }
        BlockPos roof = feet.up(2);
        return !mc.world.getBlockState(roof).getCollisionShape(mc.world, roof).isEmpty();
    }

    private boolean place(BlockPos pos) {
        ClientPlayerEntity p = mc.player;
        if (placeAgainstSupport(pos)) return true;

        // Fallback for servers that reject air placement: place a permanent
        // support block directly below the requested upper block, then retry
        // the requested position on the next tick. The support is intentionally
        // left in place; no cleanup/removal is performed.
        BlockPos scaffold = pos.down();
        if (mc.world.getBlockState(scaffold).isReplaceable()) {
            placeAgainstSupport(scaffold);
        }
        return false;
    }

    private boolean placeAgainstSupport(BlockPos pos) {
        ClientPlayerEntity p = mc.player;
        for (Direction face : Direction.values()) {
            BlockPos support = pos.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(
                face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5
            );
            if (p.getEyePos().distanceTo(hit) > placeRange.get()) continue;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
            p.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.player != null) InvUtil.select(previousSlot);
        previousSlot = -1;
    }

    private void aimAt(PlayerEntity victim) {
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double dx = victim.getX() - eye.x, dy = victim.getBodyY(0.5) - eye.y, dz = victim.getZ() - eye.z;
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || !serverAllowed()) {
            restoreSlot();
            return;
        }

        PlayerEntity next = findTarget(p);
        if (next == null) {
            restoreSlot();
            target = null;
            anchor = null;
            index = 0;
            timer = 0;
            return;
        }

        if (target != next || anchor == null || !anchor.equals(next.getBlockPos())) {
            restoreSlot();
            target = next;
            anchor = next.getBlockPos();
            order = trapOrder(anchor);
            index = 0;
            timer = 0;
        }

        if (!enclosed(target)) {
            int obsidian = InvUtil.ensureHotbar(Items.OBSIDIAN);
            if (obsidian < 0) {
                if (timer == 0) p.sendMessage(Text.literal("[Nightfall] ObiTrapBot: Kein Obsidian im Inventar/Hotbar."), false);
                timer = Math.max(timer, 40);
                return;
            }
            if (timer > 0) { timer--; return; }
            if (previousSlot < 0) previousSlot = InvUtil.selected();
            InvUtil.select(obsidian);
            while (index < order.size()) {
                BlockPos pos = order.get(index);
                if (!mc.world.getBlockState(pos).isReplaceable()) { index++; continue; }
                if (place(pos)) { index++; timer = placeDelay.getInt(); }
                else timer = 3;
                return;
            }
            index = 0;
            timer = 5;
            return;
        }

        // Once the upper enclosure is complete, repeatedly restore the cobweb in the target's head space.
        BlockPos head = target.getBlockPos().up();
        if (!mc.world.getBlockState(head).isOf(Blocks.COBWEB)) {
            int cobweb = InvUtil.ensureHotbar(Items.COBWEB);
            if (cobweb < 0) {
                restoreSlot();
                if (timer == 0) p.sendMessage(Text.literal("[Nightfall] ObiTrapBot: Kein Cobweb im Inventar/Hotbar."), false);
                timer = 40;
                return;
            }
            if (timer > 0) { timer--; return; }
            if (previousSlot < 0) previousSlot = InvUtil.selected();
            InvUtil.select(cobweb);
            if (mc.world.getBlockState(head).isReplaceable() && place(head)) {
                timer = placeDelay.getInt();
            } else {
                timer = 3;
            }
            return;
        }

        restoreSlot();
        if (!attack.get() || p.distanceTo(target) > attackRange.get() || !p.canSee(target)
            || p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        int sword = InvUtil.findBestSword();
        if (sword < 0) return;
        InvUtil.select(sword);
        aimAt(target);
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
    }
}

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
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds a compact obsidian enclosure around a target using normal block interactions. */
public final class ObiTrapBot extends Module {
    private final StringSetting serverFilter = add(new StringSetting("Server-Filter (leer = alle)", "", 64));
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 7, 3, 12, 1));
    private final NumberSetting placeRange = add(new NumberSetting("Platzier-Reichweite", 5.0, 2.5, 6, 0.5));
    private final NumberSetting placeDelay = add(new NumberSetting("Bau-Intervall (Ticks)", 1, 1, 10, 1));
    private final NumberSetting attackRange = add(new NumberSetting("Angriffs-Reichweite", 3.2, 2.5, 4.5, 0.1));
    private final BoolSetting cobwebs = add(new BoolSetting("Cobwebs innen platzieren", true));
    private final BoolSetting attack = add(new BoolSetting("Nach Falle angreifen", true));

    private PlayerEntity target;
    private BlockPos anchor;
    private List<BlockPos> order = List.of();
    private List<BlockPos> webOrder = List.of();
    private int index, webIndex, timer, attempts, previousSlot = -1;
    private boolean obsidianComplete, websComplete;

    public ObiTrapBot() {
        super("ObiTrapBot", "Baut eine passgenaue Obsidian-Falle mit optionalen Cobwebs.", Category.COMBAT);
    }

    @Override protected void onDisable() { reset(); }
    @Override public void onWorldLeave() { reset(); }

    private void reset() {
        restoreSlot();
        target = null;
        anchor = null;
        order = List.of();
        webOrder = List.of();
        index = webIndex = timer = attempts = 0;
        obsidianComplete = websComplete = false;
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
            if (d < bestDistance) { bestDistance = d; best = candidate; }
        }
        return best;
    }

    /** 3x3 footprint ring on both body levels, plus a complete 3x3 roof. */
    private List<BlockPos> trapOrder(BlockPos feet) {
        List<BlockPos> out = new ArrayList<>(25);
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    out.add(feet.add(x, y, z));
                }
            }
        }
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++)
                out.add(feet.add(x, 2, z));
        return out;
    }

    private List<BlockPos> cobwebOrder(BlockPos feet) {
        return List.of(feet, feet.up());
    }

    private boolean place(BlockPos pos) {
        ClientPlayerEntity p = mc.player;
        for (Direction face : Direction.values()) {
            BlockPos support = pos.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (p.getEyePos().distanceTo(hit) > placeRange.get()) continue;
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, support, false));
            p.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private int ensureBlock(Item item) { return InvUtil.ensureHotbar(item); }

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

    @Override public void onTick() {
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
            index = webIndex = 0;
            obsidianComplete = websComplete = false;
            return;
        }

        if (target != next || anchor == null || !anchor.equals(next.getBlockPos())) {
            restoreSlot();
            target = next;
            anchor = next.getBlockPos();
            order = trapOrder(anchor);
            webOrder = cobwebOrder(anchor);
            index = webIndex = timer = attempts = 0;
            obsidianComplete = websComplete = false;
        }

        if (!obsidianComplete) {
            int slot = ensureBlock(Items.OBSIDIAN);
            if (slot < 0) {
                if (timer == 0) p.sendMessage(Text.literal("[Nightfall] ObiTrapBot: Kein Obsidian im Inventar/Hotbar."), false);
                timer = Math.max(timer, 40);
                return;
            }
            if (timer > 0) { timer--; return; }
            if (previousSlot < 0) previousSlot = InvUtil.selected();
            InvUtil.select(slot);

            while (index < order.size()) {
                BlockPos pos = order.get(index);
                if (!mc.world.getBlockState(pos).isReplaceable()) {
                    index++;
                    attempts = 0;
                    continue;
                }
                if (place(pos)) {
                    index++;
                    attempts = 0;
                    timer = placeDelay.getInt();
                } else if (++attempts >= 3) {
                    index++;
                    attempts = 0;
                    timer = 2;
                } else {
                    timer = 3;
                }
                return;
            }
            // Each planned position has been handled exactly once; don't restart the build loop.
            obsidianComplete = true;
            timer = 0;
            return;
        }

        if (cobwebs.get() && !websComplete) {
            int slot = ensureBlock(Items.COBWEB);
            if (slot >= 0) {
                if (timer > 0) { timer--; return; }
                if (previousSlot < 0) previousSlot = InvUtil.selected();
                InvUtil.select(slot);
                while (webIndex < webOrder.size()) {
                    BlockPos pos = webOrder.get(webIndex++);
                    if (!mc.world.getBlockState(pos).isReplaceable()) continue;
                    place(pos);
                    timer = placeDelay.getInt();
                    return;
                }
            }
            websComplete = true;
        } else {
            websComplete = true;
        }

        restoreSlot();
        if (!attack.get() || p.distanceTo(target) > attackRange.get()
            || !p.canSee(target) || p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        int sword = InvUtil.findBestSword();
        if (sword < 0) return;
        InvUtil.select(sword);
        aimAt(target);
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Setzt End Crystals auf Obsidian/Bedrock neben dem naechsten Gegner und zerstoert sie.
 * Schaden ist eine Schaetzung (siehe CombatUtil). Selbstschutz: kein Platzieren/Zerstoeren, wenn der geschaetzte
 * Eigenschaden zu hoch ist oder toedlich sein koennte. Normale Interaktions-Pakete, keine Tarnung.
 */
public class CrystalAura extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 10, 4, 16, 1));
    private final NumberSetting placeRange  = add(new NumberSetting("Platzier-Reichweite", 4.5, 2, 6, 0.5));
    private final NumberSetting breakRange  = add(new NumberSetting("Zerstoer-Reichweite", 4.5, 2, 6, 0.5));
    private final NumberSetting minDamage   = add(new NumberSetting("Min. Schaden", 6, 1, 36, 1));
    private final NumberSetting maxSelf     = add(new NumberSetting("Max. Eigenschaden", 6, 1, 20, 1));
    private final NumberSetting placeDelay  = add(new NumberSetting("Platzier-Delay (Ticks)", 2, 0, 20, 1));
    private final NumberSetting breakDelay  = add(new NumberSetting("Zerstoer-Delay (Ticks)", 1, 0, 20, 1));
    private final BoolSetting place         = add(new BoolSetting("Platzieren", true));
    private final BoolSetting breakC        = add(new BoolSetting("Zerstoeren", true));
    private final BoolSetting autoSwitch    = add(new BoolSetting("Auto-Switch", true));
    private int placeT, breakT;

    public CrystalAura() { super("CrystalAura", "Platziert und zerstoert End Crystals bei Gegnern (Schaden geschaetzt).", Category.COMBAT); }

    private boolean selfSafe(double dmg, ClientPlayerEntity p) {
        return dmg <= maxSelf.get() && dmg < CombatUtil.effectiveHealth(p) - 1.0;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (placeT > 0) placeT--;
        if (breakT > 0) breakT--;
        PlayerEntity t = CombatUtil.nearestPlayer(targetRange.get());
        if (t == null) return;

        if (breakC.get() && breakT == 0) {
            EndCrystalEntity best = null; double bestScore = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof EndCrystalEntity c) || !c.isAlive() || p.distanceTo(c) > breakRange.get()) continue;
                Vec3d pos = c.getPos();
                double self = CombatUtil.explosionDamage(pos, p, 6.0);
                if (!selfSafe(self, p)) continue;
                double dt = CombatUtil.explosionDamage(pos, t, 6.0);
                if (dt < 1.0) continue;
                if (dt - self > bestScore) { bestScore = dt - self; best = c; }
            }
            if (best != null) {
                mc.interactionManager.attackEntity(p, best);
                p.swingHand(Hand.MAIN_HAND);
                breakT = breakDelay.getInt();
                return;
            }
        }

        if (place.get() && placeT == 0) {
            Hand hand = p.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND
                    : p.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND : null;
            int slot = -1;
            if (hand == null) {
                if (!autoSwitch.get()) return;
                slot = InvUtil.findHotbar(Items.END_CRYSTAL);
                if (slot < 0) return;
            }
            BlockPos base = p.getBlockPos();
            int r = (int) Math.ceil(placeRange.get());
            BlockPos bestPos = null; double bestScore = 0;
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                BlockPos bp = base.add(x, y, z);
                var st = mc.world.getBlockState(bp);
                if (!(st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.BEDROCK))) continue;
                BlockPos up = bp.up();
                if (!mc.world.getBlockState(up).isAir() || !mc.world.getBlockState(up.up()).isAir()) continue;
                Vec3d top = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
                if (p.getEyePos().distanceTo(top) > placeRange.get()) continue;
                if (t.getPos().distanceTo(top) > 12) continue;
                Box clear = new Box(bp.getX(), bp.getY() + 1, bp.getZ(), bp.getX() + 1, bp.getY() + 3, bp.getZ() + 1);
                if (!mc.world.getOtherEntities(null, clear).isEmpty()) continue;
                double dt = CombatUtil.explosionDamage(top, t, 6.0);
                double self = CombatUtil.explosionDamage(top, p, 6.0);
                if (dt < minDamage.get() || !selfSafe(self, p)) continue;
                if (dt - self > bestScore) { bestScore = dt - self; bestPos = bp; }
            }
            if (bestPos != null) {
                if (hand == null) { InvUtil.select(slot); hand = Hand.MAIN_HAND; }
                Vec3d hit = new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
                mc.interactionManager.interactBlock(p, hand, new BlockHitResult(hit, Direction.UP, bestPos, false));
                p.swingHand(hand);
                placeT = placeDelay.getInt();
            }
        }
    }
}

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

/** CrystalAura mit getrenntem Crystal-Break und zielbasiertem Placement. */
public class CrystalAura extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 10, 4, 16, 1));
    private final NumberSetting placeRange  = add(new NumberSetting("Platzier-Reichweite", 4.5, 2, 6, 0.5));
    private final NumberSetting breakRange  = add(new NumberSetting("Zerstoer-Reichweite", 4.5, 2, 6, 0.5));
    private final NumberSetting placeDelay  = add(new NumberSetting("Platzier-Delay (Ticks)", 0, 0, 20, 1));
    private final NumberSetting breakDelay  = add(new NumberSetting("Zerstoer-Delay (Ticks)", 0, 0, 20, 1));
    private final BoolSetting place         = add(new BoolSetting("Platzieren", true));
    private final BoolSetting breakC        = add(new BoolSetting("Zerstoeren", true));
    private final BoolSetting autoSwitch    = add(new BoolSetting("Auto-Switch", true));
    private int placeT, breakT;

    @Override public void onWorldLeave() { placeT = 0; breakT = 0; }
    @Override protected void onDisable() { placeT = 0; breakT = 0; }

    public CrystalAura() {
        super("CrystalAura", "Platziert und zerstoert End Crystals.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (placeT > 0) placeT--;
        if (breakT > 0) breakT--;

        // Break darf nicht davon abhaengen, ob gerade ein Spieler als Ziel gefunden wurde.
        if (breakC.get() && breakT == 0) {
            EndCrystalEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof EndCrystalEntity c) || !c.isAlive() || p.distanceTo(c) > breakRange.get()) continue;
                double distance = c.squaredDistanceTo(p);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = c;
                }
            }
            if (best != null) {
                mc.interactionManager.attackEntity(p, best);
                p.swingHand(Hand.MAIN_HAND);
                breakT = breakDelay.getInt();
                return;
            }
        }

        if (!place.get() || placeT > 0) return;
        PlayerEntity target = CombatUtil.nearestPlayer(targetRange.get());
        if (target == null) return;

        Hand hand = p.getOffHandStack().isOf(Items.END_CRYSTAL) ? Hand.OFF_HAND
                : p.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND : null;
        if (hand == null) {
            if (!autoSwitch.get()) return;
            int slot = InvUtil.ensureHotbar(Items.END_CRYSTAL);
            if (slot < 0) return;
            InvUtil.select(slot);
            hand = Hand.MAIN_HAND;
        }

        BlockPos origin = p.getBlockPos();
        int r = (int) Math.ceil(placeRange.get());
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos bp = origin.add(x, y, z);
                    if (p.getEyePos().distanceTo(Vec3d.ofCenter(bp)) > placeRange.get() + 1.0) continue;
                    var state = mc.world.getBlockState(bp);
                    if (!(state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))) continue;
                    BlockPos up = bp.up();
                    if (!mc.world.getBlockState(up).isAir() || !mc.world.getBlockState(up.up()).isAir()) continue;

                    Box crystalSpace = new Box(bp.getX(), bp.getY() + 1, bp.getZ(),
                            bp.getX() + 1, bp.getY() + 3, bp.getZ() + 1);
                    if (!mc.world.getOtherEntities(null, crystalSpace).isEmpty()) continue;

                    Vec3d crystalPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
                    double targetDistance = target.squaredDistanceTo(crystalPos);
                    if (targetDistance > 12.0 * 12.0) continue;
                    if (targetDistance < bestDistance) {
                        bestDistance = targetDistance;
                        bestPos = bp.toImmutable();
                    }
                }
            }
        }

        if (bestPos == null) return;
        Vec3d hit = new Vec3d(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hit, Direction.UP, bestPos, false);
        mc.interactionManager.interactBlock(p, hand, hitResult);
        p.swingHand(hand);
        placeT = placeDelay.getInt();
    }
}

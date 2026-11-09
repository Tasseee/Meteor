package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Respawn-Anker-Falle ueber dem Kopf des naechsten Gegners: setzen, mit Glowstone laden, mit anderem Item zuenden.
 * Funktioniert nur ausserhalb des Nethers (dort explodiert der Anker nicht). Eigenschutz per Schadensschaetzung.
 */
public class AutoAnchor extends Module {
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 8, 3, 12, 1));
    private final NumberSetting range       = add(new NumberSetting("Reichweite", 4.5, 2, 6, 0.5));
    private final NumberSetting delay       = add(new NumberSetting("Delay (Ticks)", 2, 1, 20, 1));
    private final NumberSetting maxSelf     = add(new NumberSetting("Max. Eigenschaden", 6, 1, 20, 1));
    private int timer;

    @Override public void onWorldLeave() { timer = 0; }

    public AutoAnchor() { super("AutoAnchor", "Setzt, laedt und zuendet Respawn-Anker ueber dem Gegner.", Category.COMBAT); }

    private void use(ClientPlayerEntity p, BlockPos pos, Direction face, Vec3d hit) {
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        p.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (mc.world.getRegistryKey().getValue().getPath().equals("the_nether")) return;
        if (timer > 0) { timer--; return; }
        PlayerEntity t = CombatUtil.nearestPlayer(targetRange.get());
        if (t == null) return;

        BlockPos cand = t.getBlockPos().up(2);
        Vec3d center = Vec3d.ofCenter(cand);
        if (p.getEyePos().distanceTo(center) > range.get()) return;
        double self = CombatUtil.explosionDamage(center, p, 5.0);
        BlockState st = mc.world.getBlockState(cand);

        if (st.isOf(Blocks.RESPAWN_ANCHOR)) {
            if (self > maxSelf.get() || self >= CombatUtil.effectiveHealth(p) - 1.0) return;
            int charges = st.get(RespawnAnchorBlock.CHARGES);
            if (charges < 1) {
                int g = InvUtil.ensureHotbar(Items.GLOWSTONE);
                if (g < 0) return;
                InvUtil.select(g);
            } else {
                int other = -1;
                for (int i = 0; i < 9; i++) if (!InvUtil.hotbarStack(i).isOf(Items.GLOWSTONE)) { other = i; break; }
                if (other < 0) return;
                InvUtil.select(other);
            }
            use(p, cand, Direction.UP, center);
            timer = delay.getInt();
            return;
        }
        if (!st.isAir()) return;
        int a = InvUtil.ensureHotbar(Items.RESPAWN_ANCHOR);
        if (a < 0) return;
        for (Direction d : Direction.values()) {
            BlockPos nb = cand.offset(d);
            if (mc.world.getBlockState(nb).getCollisionShape(mc.world, nb).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3d hit = Vec3d.ofCenter(nb).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            InvUtil.select(a);
            use(p, nb, face, hit);
            timer = delay.getInt();
            return;
        }
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Targeted crop helper: harvests a mature vanilla crop under the crosshair and replants it
 * when the matching seed is already in the hotbar. It never scans or breaks a whole field.
 */
public final class AutoFarm extends Module {
    private final BoolSetting replant = add(new BoolSetting("Automatisch nachpflanzen", true));
    private BlockPos pendingCrop;
    private BlockPos pendingSoil;
    private Item pendingSeed;
    private int breakTicks;
    private int cooldown;

    public AutoFarm() {
        super("AutoFarm", "Erntet nur die reife Pflanze unter dem Fadenkreuz und pflanzt optional nach.", Category.WORLD);
    }

    @Override protected void onEnable() { clearPending(); cooldown = 0; }
    @Override protected void onDisable() { clearPending(); cooldown = 0; }
    @Override public void onWorldLeave() { clearPending(); cooldown = 0; }

    private void clearPending() {
        pendingCrop = null;
        pendingSoil = null;
        pendingSeed = null;
        breakTicks = 0;
    }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) cooldown--;

        if (pendingCrop != null) {
            if (mc.world.getBlockState(pendingCrop).isAir()) {
                if (replant.get()) plantAgain();
                clearPending();
                cooldown = 4;
            } else if (++breakTicks <= 80) {
                mc.interactionManager.updateBlockBreakingProgress(pendingCrop, Direction.UP);
            } else {
                clearPending();
            }
            return;
        }
        if (cooldown > 0 || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMature(state)) return;

        if (state.isOf(Blocks.WHEAT)) pendingSeed = Items.WHEAT_SEEDS;
        else if (state.isOf(Blocks.CARROTS)) pendingSeed = Items.CARROT;
        else if (state.isOf(Blocks.POTATOES)) pendingSeed = Items.POTATO;
        else if (state.isOf(Blocks.BEETROOTS)) pendingSeed = Items.BEETROOT_SEEDS;
        else return;

        pendingCrop = pos.toImmutable();
        pendingSoil = pos.down().toImmutable();
        breakTicks = 0;
        mc.interactionManager.attackBlock(pos, hit.getSide());
        mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide());
    }

    private void plantAgain() {
        if (pendingSeed == null || pendingSoil == null || mc.world == null || mc.player == null) return;
        if (!mc.world.getBlockState(pendingSoil).isOf(Blocks.FARMLAND)
                || !mc.world.getBlockState(pendingCrop).isAir()) return;
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (!mc.player.getInventory().getStack(slot).isOf(pendingSeed)) continue;
            mc.player.getInventory().setSelectedSlot(slot);
            BlockHitResult placeHit = new BlockHitResult(
                    Vec3d.ofCenter(pendingSoil).add(0, 0.5, 0), Direction.UP, pendingSoil, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, placeHit);
            mc.player.getInventory().setSelectedSlot(oldSlot);
            return;
        }
    }
}

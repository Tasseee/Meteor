package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FirstPersonStyle;
import dev.nightfall.modules.GhostHand;
import dev.nightfall.modules.WeaponAnimations;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local first-person-only transform hook for the player's 3D arm and held item. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique private int nightfall$matrixDepth;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void nightfall$beforeFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch,
                                                Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                                MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                                CallbackInfo ci) {
        try {
            if (hand == Hand.MAIN_HAND && item.isEmpty() && ModuleManager.get(GhostHand.class).isEnabled()) {
                ci.cancel();
                return;
            }
        } catch (Throwable ignored) { }
        boolean styleEnabled = false, weaponEnabled = false;
        try { styleEnabled = ModuleManager.get(FirstPersonStyle.class).isEnabled(); } catch (Throwable ignored) { }
        try { weaponEnabled = ModuleManager.get(WeaponAnimations.class).isEnabled(); } catch (Throwable ignored) { }
        if (!styleEnabled && !weaponEnabled) return;
        matrices.push();
        nightfall$matrixDepth++;
        boolean mainArmLeft = player.getMainArm() == Arm.LEFT;
        boolean leftHand = hand == Hand.MAIN_HAND ? mainArmLeft : !mainArmLeft;
        try {
            if (styleEnabled) ModuleManager.get(FirstPersonStyle.class).transform(matrices, leftHand);
            if (weaponEnabled) ModuleManager.get(WeaponAnimations.class).transform(matrices, swingProgress, leftHand);
        } catch (Throwable ignored) {
            // Never let cosmetic transforms interfere with vanilla item rendering.
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void nightfall$afterFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch,
                                               Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                               MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                               CallbackInfo ci) {
        if (nightfall$matrixDepth > 0) {
            matrices.pop();
            nightfall$matrixDepth--;
        }
    }
}

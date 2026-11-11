package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FirstPersonStyle;
import dev.nightfall.modules.WeaponAnimations;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.entity.LivingEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Local first-person-only transform hook for the player's 3D arm and held item. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique private int nightfall$matrixDepth;
    @Unique private final Deque<Boolean> nightfall$blockScaleFrames = new ArrayDeque<>();

    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack nightfall$swapRenderedItem(ItemStack original) {
        try {
            FirstPersonStyle style = ModuleManager.get(FirstPersonStyle.class);
            if (style != null && style.isEnabled()) return style.swapRenderedStack(original);
        } catch (Throwable ignored) { }
        return original;
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void nightfall$beforeFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch,
                                                Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                                MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                                CallbackInfo ci) {
        boolean styleEnabled = false, weaponEnabled = false;
        try { styleEnabled = ModuleManager.get(FirstPersonStyle.class).isEnabled(); } catch (Throwable ignored) { }
        try { weaponEnabled = ModuleManager.get(WeaponAnimations.class).isEnabled(); } catch (Throwable ignored) { }
        if (!styleEnabled && !weaponEnabled) return;
        matrices.push();
        nightfall$matrixDepth++;
        boolean mainArmLeft = player.getMainArm() == Arm.LEFT;
        boolean leftHand = hand == Hand.MAIN_HAND ? mainArmLeft : !mainArmLeft;
        try {
            if (styleEnabled) ModuleManager.get(FirstPersonStyle.class).transform(matrices, leftHand, item);

            // Lower swords slightly without changing other held items.
            if (item != null && !item.isEmpty() && Registries.ITEM.getId(item.getItem()).getPath().endsWith("_sword")) {
                matrices.translate(0.0, -0.09, 0.0);
            }

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

    /**
     * Scale only the queued first-person block model. The hand/arm render happens
     * in renderFirstPersonItem and must not inherit the block-only scale.
     */
    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD")
    )
    private void nightfall$beforeHeldBlockRender(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                 MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                                 CallbackInfo ci) {
        boolean scaled = false;
        try {
            FirstPersonStyle style = ModuleManager.get(FirstPersonStyle.class);
            if (style != null && style.isEnabled() && context.isFirstPerson() && style.scalesHeldBlock(stack)) {
                matrices.push();
                float blockScale = style.blockScaleValue();
                matrices.scale(blockScale, blockScale, blockScale);
                scaled = true;
            }
        } catch (Throwable ignored) {
            // Cosmetic item scaling must never interrupt vanilla rendering.
        }
        nightfall$blockScaleFrames.push(scaled);
    }

    @Inject(
            method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("RETURN")
    )
    private void nightfall$afterHeldBlockRender(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
                                                CallbackInfo ci) {
        if (!nightfall$blockScaleFrames.isEmpty() && nightfall$blockScaleFrames.pop()) {
            matrices.pop();
        }
    }
}

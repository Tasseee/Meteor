package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FirstPersonStyle;
import dev.nightfall.modules.WeaponAnimations;
import net.minecraft.class_11659;
import net.minecraft.class_1268;
import net.minecraft.class_1306;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import net.minecraft.class_742;
import net.minecraft.class_759;
import net.minecraft.class_7923;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_759.class})
public abstract class HeldItemRendererMixin {
   @Unique
   private int nightfall$matrixDepth;

   @ModifyVariable(
      method = {"method_3228"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private class_1799 nightfall$swapRenderedItem(class_1799 original) {
      try {
         FirstPersonStyle style = ModuleManager.get(FirstPersonStyle.class);
         if (style != null && style.isEnabled()) {
            return style.swapRenderedStack(original);
         }
      } catch (Throwable var3) {
      }

      return original;
   }

   @Inject(
      method = {"method_3228"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nightfall$beforeFirstPersonItem(
      class_742 player,
      float tickProgress,
      float pitch,
      class_1268 hand,
      float swingProgress,
      class_1799 item,
      float equipProgress,
      class_4587 matrices,
      class_11659 queue,
      int light,
      CallbackInfo ci
   ) {
      boolean styleEnabled = false;
      boolean weaponEnabled = false;

      try {
         styleEnabled = ModuleManager.get(FirstPersonStyle.class).isEnabled();
      } catch (Throwable var19) {
      }

      try {
         weaponEnabled = ModuleManager.get(WeaponAnimations.class).isEnabled();
      } catch (Throwable var18) {
      }

      if (styleEnabled || weaponEnabled) {
         matrices.method_22903();
         this.nightfall$matrixDepth++;
         boolean mainArmLeft = player.method_6068() == class_1306.field_6182;
         boolean leftHand = hand == class_1268.field_5808 ? mainArmLeft : !mainArmLeft;

         try {
            if (styleEnabled) {
               ModuleManager.get(FirstPersonStyle.class).transform(matrices, leftHand);
            }

            if (item != null && !item.method_7960() && item.method_7909() instanceof class_1747) {
               matrices.method_22905(0.5F, 0.5F, 0.5F);
            }

            if (item != null && !item.method_7960() && class_7923.field_41178.method_10221(item.method_7909()).method_12832().endsWith("_sword")) {
               matrices.method_22904(0.0, -0.09, 0.0);
            }

            if (weaponEnabled) {
               ModuleManager.get(WeaponAnimations.class).transform(matrices, swingProgress, leftHand);
            }
         } catch (Throwable var17) {
         }
      }
   }

   @Inject(
      method = {"method_3228"},
      at = {@At("RETURN")}
   )
   private void nightfall$afterFirstPersonItem(
      class_742 player,
      float tickProgress,
      float pitch,
      class_1268 hand,
      float swingProgress,
      class_1799 item,
      float equipProgress,
      class_4587 matrices,
      class_11659 queue,
      int light,
      CallbackInfo ci
   ) {
      if (this.nightfall$matrixDepth > 0) {
         matrices.method_22909();
         this.nightfall$matrixDepth--;
      }
   }
}

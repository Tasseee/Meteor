package dev.nightfall.mixin;

import dev.nightfall.modules.FakeName;
import net.minecraft.class_10055;
import net.minecraft.class_1007;
import net.minecraft.class_11659;
import net.minecraft.class_12075;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the local name to the player's 3D nameplate (F5 and FreeLook render path). */
@Mixin(class_1007.class)
public abstract class FakeNamePlayerRendererMixin {
   @Inject(
      method = "method_4213(Lnet/minecraft/class_10055;Lnet/minecraft/class_4587;Lnet/minecraft/class_11659;Lnet/minecraft/class_12075;)V",
      at = @At("HEAD")
   )
   private void nightfall$replaceLocalNameplate(
      class_10055 state,
      class_4587 matrices,
      class_11659 queue,
      class_12075 camera,
      CallbackInfo ci
   ) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || state == null || state.field_53528 != mc.field_1724.method_5628()) return;

      String replacement = FakeName.activeName();
      if (replacement.isBlank()) return;

      state.field_53525 = FakeName.replaceName(state.field_53525, mc.field_1724.method_5477().method_10851().toString());
      // The account name is a plain player-name string; use it to replace only that text node.
      if (state.field_53525 != null) {
         state.field_53525 = FakeName.replaceName(state.field_53525, mc.field_1724.method_5477().getString());
      }
   }
}
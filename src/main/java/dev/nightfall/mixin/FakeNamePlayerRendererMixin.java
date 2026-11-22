package dev.nightfall.mixin;

import dev.nightfall.modules.FakeName;
import net.minecraft.class_10017;
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
      method = "method_74935(Lnet/minecraft/class_11890;D)Z",
      at = @At("RETURN"),
      cancellable = true
   )
   private void nightfall$showFakeNameplateForLocalPlayer(
      net.minecraft.class_11890 player,
      double squaredDistance,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir
   ) {
      class_310 mc = class_310.method_1551();
      if (player == mc.field_1724 && FakeName.hasVisualOverride()) {
         cir.setReturnValue(true);
      }
   }

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

      if (!FakeName.hasVisualOverride()) return;

      // Both fields participate in the render-state name pipeline in 1.21.11.
      // Keep each text component's style and any prefix/suffix siblings intact.
      String accountName = mc.field_1724.method_5477().getString();
      state.field_53525 = FakeName.replaceName(state.field_53525, accountName);
      class_10017 baseState = (class_10017)(Object)state;
      if (baseState.field_53337 != null) {
         baseState.field_53337 = FakeName.replaceName(baseState.field_53337, accountName);
      }
   }
}
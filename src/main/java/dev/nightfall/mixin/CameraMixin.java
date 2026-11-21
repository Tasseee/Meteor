package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import dev.nightfall.modules.FreecamController;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_4184.class})
public abstract class CameraMixin {
   @Shadow
   public abstract void method_19327(double x, double y, double z);

   @Shadow
   public abstract void method_19325(float yaw, float pitch);

   // Force Camera.update down its third-person path while FreeLook is active.
   // The second boolean parameter (inverseView) is deliberately left untouched;
   // FreeLook selects front/back view through GameOptions#setPerspective.
   @ModifyVariable(
      method = {"method_19321"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean nightfall$forceThirdPerson(boolean thirdPerson) {
      return thirdPerson || FreecamController.isActive() || FreeLook.isActive();
   }

   @Inject(method = {"method_19318"}, at = {@At("HEAD")}, cancellable = true)
   private void nightfall$freeLookNoClip(float desired, CallbackInfoReturnable<Float> cir) {
      FreeLook freeLook = FreeLook.active();
      if (freeLook != null && freeLook.isNoClip()) {
         cir.setReturnValue(freeLook.getDistance());
      }
   }

   @ModifyVariable(
      method = {"method_19318"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private float nightfall$freeLookDistance(float desired) {
      FreeLook freeLook = FreeLook.active();
      if (freeLook == null || freeLook.isNoClip()) return desired;
      return freeLook.getDistance();
   }

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void nightfall$applyCameraState(
      class_1937 area, class_1297 focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci
   ) {
      if (FreecamController.isActive()) {
         class_243 pos = FreecamController.interpolatedPosition(tickDelta);
         this.method_19327(pos.field_1352, pos.field_1351, pos.field_1350);
         this.method_19325(
            FreecamController.interpolatedYaw(tickDelta), FreecamController.interpolatedPitch(tickDelta)
         );
         return;
      }

      FreeLook freeLook = FreeLook.active();
      if (freeLook != null) {
         this.method_19325(freeLook.getCameraYaw(), freeLook.getCameraPitch());
      }
   }
}

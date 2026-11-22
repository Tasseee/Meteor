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

@Mixin({class_4184.class})
public abstract class CameraMixin {
   @Shadow
   public abstract void method_19327(double var1, double var3, double var5);

   @Shadow
   public abstract void method_19325(float var1, float var2);

   @ModifyVariable(
      method = {"method_19321"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean nightfall$renderLocalPlayer(boolean thirdPerson) {
      return thirdPerson || FreecamController.isActive() || FreeLook.isActive();
   }

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void nightfall$applyFreecamState(
      class_1937 area, class_1297 focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci
   ) {
      if (FreecamController.isActive()) {
         class_243 pos = FreecamController.interpolatedPosition(tickDelta);
         this.method_19327(pos.field_1352, pos.field_1351, pos.field_1350);
         this.method_19325(
            FreecamController.interpolatedYaw(tickDelta),
            FreecamController.interpolatedPitch(tickDelta)
         );
      } else if (FreeLook.isActive()) {
         float yaw = FreeLook.cameraYaw();
         float pitch = FreeLook.cameraPitch();
         this.method_19325(yaw, pitch);

         double distance = FreeLook.cameraDistance();
         if (distance > 0.0 && focusedEntity != null) {
            // Camera.update already receives tickDelta; derive the eye point from the
            // interpolated entity position so the FreeLook camera does not jump at 20 TPS.
            class_243 currentEye = focusedEntity.method_33571();
            class_243 renderPos = focusedEntity.method_30950(tickDelta);
            double eyeOffsetY = currentEye.field_1351 - focusedEntity.method_23318();
            class_243 eye = new class_243(
               renderPos.field_1352,
               renderPos.field_1351 + eyeOffsetY,
               renderPos.field_1350
            );
            class_243 position = FreeLook.cameraPosition(eye, yaw, pitch);
            this.method_19327(position.field_1352, position.field_1351, position.field_1350);
         }
      }
   }
}

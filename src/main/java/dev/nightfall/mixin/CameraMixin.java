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
   public abstract void method_19327(double x, double y, double z);

   @Shadow
   public abstract void method_19325(float yaw, float pitch);

   @ModifyVariable(
      method = {"method_19321"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean nightfall$renderLocalPlayer(boolean thirdPerson) {
      return thirdPerson || FreecamController.isActive() || FreeLook.isActive();
   }

   @ModifyVariable(
      method = {"method_19321"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private boolean nightfall$frontPerspective(boolean inverseView) {
      FreeLook freeLook = FreeLook.active();
      return inverseView || (freeLook != null && freeLook.isFrontPerspective());
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
            FreecamController.interpolatedYaw(tickDelta), FreecamController.interpolatedPitch(tickDelta)
         );
         return;
      }

      FreeLook freeLook = FreeLook.active();
      if (freeLook != null) {
         float yaw = freeLook.getCameraYaw();
         float pitch = freeLook.getCameraPitch();

         // In front perspective, the camera sits in front of the player and must face back toward them.
         if (freeLook.isFrontPerspective()) {
            yaw += 180.0F;
            pitch = -pitch;
         }

         this.method_19325(yaw, pitch);
      }
   }
}

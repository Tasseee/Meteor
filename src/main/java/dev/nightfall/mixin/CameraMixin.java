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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({class_4184.class})
public abstract class CameraMixin {
   @Shadow
   public abstract void method_19327(double x, double y, double z);

   @Shadow
   public abstract void method_19325(float yaw, float pitch);

   // Set FreeLook rotation before vanilla computes the third-person camera offset.
   @ModifyArgs(
      method = {"method_19321"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4184;method_19325(FF)V",
         ordinal = 0
      )
   )
   private void nightfall$freeLookRotation(Args args) {
      FreeLook freeLook = FreeLook.active();
      if (freeLook == null) return;

      args.set(0, freeLook.getCameraYaw());
      args.set(1, freeLook.getCameraPitch());
   }

   @ModifyVariable(
      method = {"method_19321"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private boolean nightfall$renderLocalPlayer(boolean thirdPerson) {
      return thirdPerson || FreecamController.isActive();
   }

   @Inject(method = {"method_19321"}, at = {@At("TAIL")})
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
      }
   }
}

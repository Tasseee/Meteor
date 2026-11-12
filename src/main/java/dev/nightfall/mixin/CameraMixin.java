package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import dev.nightfall.modules.Freecam;
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
      return thirdPerson || Freecam.isActive();
   }

   @Inject(
      method = {"method_19321"},
      at = {@At("TAIL")}
   )
   private void nightfall$applyCameraState(
      class_1937 area, class_1297 focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci
   ) {
      if (Freecam.isActive()) {
         class_243 pos = Freecam.interpolatedPosition(tickDelta);
         this.method_19327(pos.field_1352, pos.field_1351, pos.field_1350);
         this.method_19325(Freecam.interpolatedYaw(tickDelta), Freecam.interpolatedPitch(tickDelta));
      } else if (FreeLook.isActive()) {
         this.method_19325(FreeLook.cameraYaw(), FreeLook.cameraPitch());
      }
   }
}

package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import net.minecraft.class_310;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_312.class)
public abstract class FreeLookMouseMixin {
   @Shadow @Final private class_310 field_1779;
   @Shadow private double field_1789;
   @Shadow private double field_1787;

   @Inject(method = "method_1606", at = @At("HEAD"), cancellable = true)
   private void nightfall$freeLookMouse(double timeDelta, CallbackInfo ci) {
      if (!FreeLook.isActive() || this.field_1779.field_1724 == null) return;
      FreeLook.updateMouse(this.field_1789, this.field_1787);
      this.field_1789 = 0.0;
      this.field_1787 = 0.0;
      ci.cancel();
   }
}

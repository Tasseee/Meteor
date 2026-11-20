package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import net.minecraft.class_1297;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_1297.class})
public abstract class EntityMixin {
   @Inject(method = {"method_5872"}, at = {@At("HEAD")}, cancellable = true)
   private void nightfall$freeLookChangeDirection(double dx, double dy, CallbackInfo ci) {
      if ((Object) this != class_310.method_1551().field_1724) return;

      FreeLook freeLook = FreeLook.active();
      if (freeLook == null) return;

      freeLook.changeLook(dx, dy);
      ci.cancel();
   }
}

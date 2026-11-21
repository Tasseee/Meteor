package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_312.class})
public abstract class MouseMixin {
   @Inject(method = {"method_1598"}, at = {@At("HEAD")}, cancellable = true)
   private void nightfall$freeLookZoom(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (FreeLook.handleScroll(vertical)) ci.cancel();
   }
}

package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.WebHit;
import net.minecraft.class_1297;
import net.minecraft.class_239;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.class_757.class)
public abstract class GameRendererMixin {
   @Redirect(
      // In Minecraft 1.21.11 the old findCrosshairTarget method was folded
      // into updateCrosshairTarget (method_3190).
      method = "method_3190",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_5745(DFZ)Lnet/minecraft/class_239;"
      ),
      require = 0
   )
   private class_239 nightfall$webHitRaycast(class_1297 entity, double maxDistance, float tickDelta, boolean includeFluids) {
      class_239 original = entity.method_5745(maxDistance, tickDelta, includeFluids);
      try {
         WebHit module = ModuleManager.get(WebHit.class);
         if (module.isEnabled()) return module.adjust(entity, original, maxDistance, tickDelta);
      } catch (Throwable ignored) {
      }
      return original;
   }
}

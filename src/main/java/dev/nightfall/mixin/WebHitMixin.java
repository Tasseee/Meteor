package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.WebHit;
import net.minecraft.class_1297;
import net.minecraft.class_239;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(class_1297.class)
public abstract class WebHitMixin {
   @Redirect(
      method = "method_5745",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_1297;method_5745(DFZ)Lnet/minecraft/class_239;"
      )
   )
   private class_239 nightfall$noop(class_1297 entity, double maxDistance, float tickDelta, boolean includeFluids) {
      return entity.method_5745(maxDistance, tickDelta, includeFluids);
   }
}

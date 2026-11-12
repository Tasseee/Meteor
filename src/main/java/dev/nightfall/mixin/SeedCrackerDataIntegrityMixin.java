package dev.nightfall.mixin;

import dev.nightfall.seed.SeedIntegrity;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {DataStorage.class},
   remap = false
)
abstract class SeedCrackerDataIntegrityMixin {
   @Inject(
      method = {"addBaseData"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void nightfall$validateIncoming(@Coerce Object data, @Coerce Object event, CallbackInfoReturnable<Boolean> cir) {
      if (!SeedIntegrity.acceptIncoming(data, this)) {
         cir.setReturnValue(false);
      }
   }
}

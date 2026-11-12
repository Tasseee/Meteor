package dev.nightfall.mixin;

import dev.nightfall.modules.SeedCrackerProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"kaptainwutax/seedcrackerX/cracker/storage/ProgressListener"},
   remap = false
)
abstract class SeedCrackerProgressMixin {
   @Inject(
      method = {"<init>(F)V"},
      at = {@At("TAIL")},
      remap = false
   )
   private void nightfall$startProgress(float initial, CallbackInfo ci) {
      SeedCrackerProgress.reset(initial);
   }

   @Inject(
      method = {"addPercent(FZ)V"},
      at = {@At("TAIL")},
      remap = false
   )
   private void nightfall$addProgress(float percent, boolean debug, CallbackInfo ci) {
      SeedCrackerProgress.addPercent(percent);
   }
}

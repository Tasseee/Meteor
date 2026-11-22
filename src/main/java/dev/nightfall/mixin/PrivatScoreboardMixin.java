package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_329;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla right-side scoreboard while the Privat fake-stats HUD is active.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @Inject(method = "method_55803", at = @At("HEAD"), cancellable = true)
   private void nightfall$hideVanillaScoreboard(class_332 context, class_9779 tickCounter, CallbackInfo ci) {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         if (privat != null && privat.isEnabled()) {
            ci.cancel();
         }
      } catch (Throwable ignored) {
      }
   }
}

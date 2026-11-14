package dev.nightfall.mixin;

import dev.nightfall.util.OwnMeleeHitTracker;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_636;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_636.class})
abstract class MeleeHitMixin {
   @Inject(
      method = {"method_2918"},
      at = {@At("HEAD")}
   )
   private void nightfall$recordOwnMeleeAttack(class_1657 player, class_1297 target, CallbackInfo ci) {
      OwnMeleeHitTracker.recordAttack(player, target);
   }
}

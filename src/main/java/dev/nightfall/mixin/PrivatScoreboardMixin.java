package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_266;
import net.minecraft.class_269;
import net.minecraft.class_9011;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.class_329.class)
abstract class PrivatScoreboardMixin {
   private static final ThreadLocal<class_9011> NIGHTFALL_SCOREBOARD_ENTRY = new ThreadLocal<>();

   /**
    * Minecraft builds each sidebar row from a ScoreboardEntry. Capturing that
    * entry lets the score replacement use the real row label instead of the
    * fragile draw-order index.
    */
   @Inject(
      method = "method_55439",
      at = @At("HEAD")
   )
   private void nightfall$captureEntry(
      class_269 scoreboard,
      net.minecraft.class_9022 numberFormat,
      class_9011 entry,
      CallbackInfoReturnable<?> cir
   ) {
      NIGHTFALL_SCOREBOARD_ENTRY.set(entry);
   }

   @ModifyArg(
      method = "method_55439",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_329$class_9016;<init>(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;I)V"
      ),
      index = 1
   )
   private class_2561 nightfall$customizeScoreValue(class_2561 original) {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         class_9011 entry = NIGHTFALL_SCOREBOARD_ENTRY.get();

         if (privat == null || !privat.isEnabled() || original == null || entry == null) {
            return original;
         }

         String label = entry.method_55387().getString();
         String replacement = privat.getScoreboardValue(label, original.getString());

         if (replacement == null || replacement.isBlank() || replacement.equals(original.getString())) {
            return original;
         }

         return class_2561.method_43470(replacement).method_10862(original.method_10866());
      } catch (Throwable ignored) {
         return original;
      } finally {
         NIGHTFALL_SCOREBOARD_ENTRY.remove();
      }
   }
}

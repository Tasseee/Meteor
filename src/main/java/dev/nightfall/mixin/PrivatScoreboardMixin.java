package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_266;
import net.minecraft.class_332;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps the real server sidebar, but masks numeric score values while Privat is enabled.
 */
@Mixin(net.minecraft.class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArg(
      method = "method_1757",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_332;method_51439(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)V"
      ),
      index = 1,
      require = 0
   )
   private class_2561 nightfall$maskScoreValue(class_2561 original) {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         if (privat == null || !privat.isEnabled() || original == null) return original;

         String value = original.getString().trim();
         if (!value.matches("[+-]?[0-9][0-9.,]*")) return original;

         return class_2561.method_43470("████")
            .method_10862(original.method_10866().method_36139(0xFF555555));
      } catch (Throwable ignored) {
         return original;
      }
   }
}

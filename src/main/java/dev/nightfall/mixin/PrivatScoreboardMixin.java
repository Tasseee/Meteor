package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Keeps the real server sidebar and replaces only its displayed numeric score values.
 * The labels/player names stay exactly as sent by the server.
 */
@Mixin(net.minecraft.class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArg(
      method = {"method_1757", "method_55803"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_332;method_51439(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)V"
      ),
      index = 1,
      require = 0
   )
   private class_2561 nightfall$zeroScoreText(class_2561 original) {
      if (!privatEnabled() || original == null) return original;

      String text = original.getString();
      if (!containsDigit(text)) return original;

      // Only scoreboard values are numeric here. Keep suffixes such as M, ms, d, h and s.
      String zeroed = text.replaceAll("[0-9]+(?:[.,][0-9]+)?", "0");
      if (zeroed.equals(text)) return original;

      return class_2561.method_43470(zeroed).method_10862(original.method_10866());
   }

   private static boolean privatEnabled() {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         return privat != null && privat.isEnabled();
      } catch (Throwable ignored) {
         return false;
      }
   }

   private static boolean containsDigit(String text) {
      for (int i = 0; i < text.length(); i++) {
         if (Character.isDigit(text.charAt(i))) return true;
      }
      return false;
   }
}

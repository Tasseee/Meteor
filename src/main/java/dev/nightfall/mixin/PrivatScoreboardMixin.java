package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Obfuscates only the right-hand score Text in Minecraft 1.21.11's sidebar.
 * In displayScoreboardSidebar, Text draw calls are: title (0), row name (1),
 * score value (2). This injection targets only the score value.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArg(
      method = "method_1757(Lnet/minecraft/class_332;Lnet/minecraft/class_266;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_332;method_51439(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)I",
         ordinal = 2
      ),
      index = 1,
      require = 1
   )
   private class_2561 nightfall$obfuscateRightScore(class_2561 originalValue) {
      if (originalValue == null || originalValue.getString().isEmpty()
         || !containsDigit(originalValue.getString()) || !privatEnabled()) {
         return originalValue;
      }

      // §k / Formatting.OBFUSCATED: preserve the original Text and all its
      // colors/components, changing only the rendered glyphs.
      // The underlying text and measured width remain unchanged, so the
      // right-aligned score does not move to the left.
      return originalValue.method_27662()
         .method_10862(originalValue.method_10866().method_36141(true));
   }

   private static boolean privatEnabled() {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         return privat != null && privat.isEnabled();
      } catch (Throwable ignored) {
         return false;
      }
   }

   private static boolean containsDigit(String value) {
      for (int i = 0; i < value.length(); i++) {
         if (Character.isDigit(value.charAt(i))) return true;
      }
      return false;
   }
}

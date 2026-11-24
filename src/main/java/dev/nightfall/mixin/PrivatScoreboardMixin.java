package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import net.minecraft.class_329;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Obfuscates only the right-hand score strings in Minecraft's real sidebar.
 *
 * In 1.21.11 the sidebar draws its left entry labels and title as Text, but
 * draws the score/value column through DrawContext.drawText(String). Redirecting
 * this String overload avoids changing labels, title, or any other HUD text.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @Redirect(
      method = "method_1757(Lnet/minecraft/class_332;Lnet/minecraft/class_266;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_332;method_51433(Lnet/minecraft/class_327;Ljava/lang/String;IIIZ)V",
         ordinal = 0
      ),
      require = 1
   )
   private void nightfall$obfuscateRightScore(
      class_332 context,
      class_327 textRenderer,
      String value,
      int x,
      int y,
      int color,
      boolean shadow
   ) {
      if (value == null || value.isEmpty() || !containsDigit(value) || !privatEnabled()) {
         context.method_51433(textRenderer, value, x, y, color, shadow);
         return;
      }

      // §k / Formatting.OBFUSCATED: keep the exact original characters and
      // therefore their measured width; only their rendered glyphs change.
      class_2561 text = class_2561.method_43470(value);
      class_2561 obfuscated = text.method_27662()
         .method_10862(text.method_10866().method_36141(true));

      // Draw at the exact same x/y/color as vanilla. Since obfuscated glyphs
      // keep the source character advance width, the right-aligned values do
      // not shift left or disturb the sidebar layout.
      context.method_51439(textRenderer, obfuscated, x, y, color, shadow);
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

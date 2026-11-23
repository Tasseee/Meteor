package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_9779;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the real server sidebar untouched and replaces only the numeric score
 * text on the right side. The label/name on the left is never modified.
 */
@Mixin(net.minecraft.class_329.class)
abstract class PrivatScoreboardMixin {
   private static int nightfall$scoreIndex;

   @Inject(method = {"method_1757", "method_55803"}, at = @At("HEAD"), require = 0)
   private void nightfall$resetScoreIndex(class_332 context, class_9779 tickCounter, CallbackInfo ci) {
      nightfall$scoreIndex = 0;
   }

   @ModifyArg(
      method = {"method_1757", "method_55803"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_332;method_51439(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)V"
      ),
      index = 1,
      require = 0
   )
   private class_2561 nightfall$customizeScoreValue(class_2561 original) {
      try {
         Privat privat = ModuleManager.get(Privat.class);
         if (privat == null || !privat.isEnabled() || original == null) return original;

         String text = original.getString().trim();
         // The vanilla sidebar draws each score as a separate Text object. Only
         // numeric Text objects are scores; labels/names are left completely intact.
         if (!containsDigit(text) || text.length() > 32) return original;

         int index = nightfall$scoreIndex++;
         String replacement = privat.getScoreboardValue(index, text);
         if (replacement == null || replacement.isBlank()) return original;

         return class_2561.method_43470(replacement).method_10862(original.method_10866());
      } catch (Throwable ignored) {
         return original;
      }
   }

   private static boolean containsDigit(String text) {
      for (int i = 0; i < text.length(); i++) {
         if (Character.isDigit(text.charAt(i))) return true;
      }
      return false;
   }
}

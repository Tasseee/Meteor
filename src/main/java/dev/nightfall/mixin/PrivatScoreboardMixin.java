package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Obfuscates only the score Text used by Minecraft's actual sidebar rows.
 *
 * In 1.21.11 method_55439 is the SidebarEntry factory used by the sidebar
 * pipeline. Its constructor receives (name, score, scoreWidth); index 1 is
 * therefore the rendered right-hand value, never the left-hand label.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArg(
      method = "method_55439(Lnet/minecraft/class_269;Lnet/minecraft/class_9022;Lnet/minecraft/class_9011;)Lnet/minecraft/class_329$class_9016;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_329$class_9016;<init>(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;I)V"
      ),
      index = 1,
      require = 1
   )
   private static class_2561 nightfall$obfuscateSidebarScore(class_2561 originalValue) {
      if (originalValue == null || originalValue.getString().isEmpty()) return originalValue;

      try {
         Privat privat = ModuleManager.get(Privat.class);
         if (privat == null || !privat.isEnabled()) return originalValue;
      } catch (Throwable ignored) {
         return originalValue;
      }

      // Minecraft's native §k effect is represented by Style.obfuscated.
      // Copy the original Text and preserve its font, color, and other styling.
      return originalValue.method_27662()
         .method_10862(originalValue.method_10866().method_36141(true));
   }
}

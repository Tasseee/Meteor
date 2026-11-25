package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Obfuscates the actual score Text while Minecraft creates each sidebar row.
 * The SidebarEntry constructor arguments are (label, score, scoreWidth).
 *
 * We modify only argument 1. The original scoreWidth (argument 2) is left
 * untouched, so the value keeps the exact vanilla right-aligned position.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArgs(
      method = "method_55439(Lnet/minecraft/class_269;Lnet/minecraft/class_9022;Lnet/minecraft/class_9011;)Lnet/minecraft/class_329$class_9016;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_329$class_9016;<init>(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;I)V"
      ),
      require = 1
   )
   private void nightfall$obfuscateSidebarScore(Args args) {
      Privat privat;
      try {
         privat = ModuleManager.get(Privat.class);
      } catch (Throwable ignored) {
         return;
      }

      if (privat == null || !privat.isEnabled()) return;

      class_2561 originalValue = (class_2561) args.get(1);
      if (originalValue == null) return;

      String raw = originalValue.getString();
      if (raw == null || raw.isEmpty() || !containsDigit(raw)) return;

      // §k is stored as the native Text Style obfuscated flag. Keep the
      // original component, colors and exact scoreWidth to avoid left-shifting.
      class_2561 obfuscated = originalValue.method_27662()
         .method_10862(originalValue.method_10866().method_36141(true));
      args.set(1, obfuscated);
   }

   private static boolean containsDigit(String value) {
      for (int i = 0; i < value.length(); i++) {
         if (Character.isDigit(value.charAt(i))) return true;
      }
      return false;
   }
}

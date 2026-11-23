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
 * Applies Minecraft's real obfuscated text style to the value column of
 * every vanilla sidebar row. The label/title and sidebar layout are untouched.
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
   private void nightfall$obfuscateSidebarValue(Args args) {
      Privat privat;

      try {
         privat = ModuleManager.get(Privat.class);
      } catch (Throwable ignored) {
         return;
      }

      if (privat == null || !privat.isEnabled()) return;

      // SidebarEntry constructor arguments are: left label, right value, width.
      // Obfuscate the actual Text style, not a literal "§k" string. This works
      // with server-provided colored Text and resource-pack glyph fonts too.
      class_2561 value = (class_2561) args.get(1);
      if (value == null || value.getString().isEmpty()) return;

      class_2561 obfuscatedValue = value.method_27662()
         .method_10862(value.method_10866().method_36141(true));
      args.set(1, obfuscatedValue);
   }
}

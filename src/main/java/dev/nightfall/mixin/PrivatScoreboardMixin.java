package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_329;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Rewrites only the value column of the real Minecraft sidebar.
 * The original sidebar position, labels and title stay untouched.
 */
@Mixin(class_329.class)
abstract class PrivatScoreboardMixin {
   @ModifyArgs(
      method = "method_55439(Lnet/minecraft/class_269;Lnet/minecraft/class_9022;Lnet/minecraft/class_9011;)Lnet/minecraft/class_329$class_9016;",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_329$class_9016;<init>(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;I)V"
      ),
      require = 0
   )
   private void nightfall$customizeScoreboardEntry(Args args) {
      Privat privat;

      try {
         privat = ModuleManager.get(Privat.class);
      } catch (Throwable ignored) {
         return;
      }

      if (privat == null || !privat.isEnabled()) return;

      class_2561 label = (class_2561) args.get(0);
      class_2561 originalValue = (class_2561) args.get(1);

      if (label == null || originalValue == null) return;

      String replacement = privat.getScoreboardValue(label.getString(), originalValue.getString());
      if (replacement == null || replacement.isBlank()) return;
      if (replacement.equals(originalValue.getString())) return;

      class_2561 changedValue = class_2561.method_43470(replacement)
         .method_10862(originalValue.method_10866());

      args.set(1, changedValue);

      // SidebarEntry stores the value width for right-alignment.
      // Recalculate it so the replacement keeps the original scoreboard layout.
      try {
         class_310 mc = class_310.method_1551();
         int width = mc.field_1772.method_27525(changedValue);
         args.set(2, width);
      } catch (Throwable ignored) {
         // Keeping the original width is still preferable to breaking the sidebar.
      }
   }
}

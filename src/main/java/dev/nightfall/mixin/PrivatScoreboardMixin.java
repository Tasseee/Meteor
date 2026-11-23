package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_269;
import net.minecraft.class_9011;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(net.minecraft.class_329.class)
abstract class PrivatScoreboardMixin {
   private static final ThreadLocal<class_9011> NIGHTFALL_SCOREBOARD_ENTRY = new ThreadLocal<>();

   @Inject(method = "method_55439", at = @At("HEAD"))
   private void nightfall$captureEntry(
      class_269 scoreboard,
      net.minecraft.class_9022 numberFormat,
      class_9011 entry,
      CallbackInfoReturnable<?> cir
   ) {
      NIGHTFALL_SCOREBOARD_ENTRY.set(entry);
   }

   @ModifyArgs(
      method = "method_55439",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_329$class_9016;<init>(Lnet/minecraft/class_2561;Lnet/minecraft/class_2561;I)V"
      )
   )
   private void nightfall$customizeScoreboardRow(Args args) {
      try {
         Privat module = ModuleManager.get(Privat.class);
         class_9011 entry = NIGHTFALL_SCOREBOARD_ENTRY.get();
         class_2561 labelComponent = (class_2561)args.get(0);
         class_2561 originalValue = (class_2561)args.get(1);

         if (module == null || !module.isEnabled() || entry == null
            || labelComponent == null || originalValue == null) {
            return;
         }

         String label = entry.method_55387().getString();
         String normalized = label == null
            ? "" : label.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);

         if (normalized.equals("rang") || normalized.equals("rank")
            || normalized.equals("clan") || normalized.equals("name")) {
            args.set(0, class_2561.method_43470(""));
            args.set(1, class_2561.method_43470(""));
            return;
         }

         String replacement = module.getScoreboardValue(label, originalValue.getString());
         if (replacement != null && !replacement.isBlank()
            && !replacement.equals(originalValue.getString())) {
            args.set(1, class_2561.method_43470(replacement).method_10862(originalValue.method_10866()));
         }
      } catch (Throwable ignored) {
         // Do not interrupt Minecraft's scoreboard rendering if a row is unusual.
      } finally {
         NIGHTFALL_SCOREBOARD_ENTRY.remove();
      }
   }
}

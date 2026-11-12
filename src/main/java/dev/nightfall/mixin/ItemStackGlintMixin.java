package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import net.minecraft.class_1799;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1799.class})
public abstract class ItemStackGlintMixin {
   @Inject(
      method = {"method_7958"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nightfall$forceItemGlint(CallbackInfoReturnable<Boolean> cir) {
      try {
         class_1799 stack = (class_1799)(Object)this;
         if (ModuleManager.get(ItemGlint.class).forceGlint(stack)) {
            cir.setReturnValue(true);
         }
      } catch (Throwable var3) {
      }
   }
}

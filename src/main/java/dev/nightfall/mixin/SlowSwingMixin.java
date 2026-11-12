package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SlowSwing;
import net.minecraft.class_1309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({class_1309.class})
abstract class SlowSwingMixin {
   @Inject(
      method = {"method_6028"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void nightfall$slowLocalSwing(CallbackInfoReturnable<Integer> cir) {
      class_310 client = class_310.method_1551();
      if (client.field_1724 == (class_1309)(Object)this) {
         try {
            SlowSwing module = ModuleManager.get(SlowSwing.class);
            if (module.isEnabled()) {
               cir.setReturnValue(module.adjustDuration((Integer)cir.getReturnValue()));
            }
         } catch (Throwable var4) {
         }
      }
   }
}

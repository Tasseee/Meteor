package dev.nightfall.mixin;

import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_355;
import net.minecraft.class_640;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only the local player's rendered tab-list name. */
@Mixin(class_355.class)
public abstract class PrivatPlayerListMixin {
   @Inject(
      method = "method_1918(Lnet/minecraft/class_640;)Lnet/minecraft/class_2561;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void nightfall$replaceLocalTabName(class_640 entry, CallbackInfoReturnable<class_2561> cir) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || entry == null || entry.method_2966() == null) return;
      if (!entry.method_2966().id().equals(mc.field_1724.method_5667())) return;

      if (!Privat.hasVisualOverride()) return;

      class_2561 current = cir.getReturnValue();
      class_2561 changed = Privat.replaceName(current, entry.method_2966().name());
      if (changed != current) cir.setReturnValue(changed);
   }
}
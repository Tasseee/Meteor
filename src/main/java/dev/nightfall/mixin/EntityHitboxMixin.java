package dev.nightfall.mixin;

import dev.nightfall.modules.Hitbox;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1297.class)
public abstract class EntityHitboxMixin {
   @Inject(method = "method_5829", at = @At("RETURN"), cancellable = true)
   private void nightfall$expandEntityHitbox(CallbackInfoReturnable<class_238> cir) {
      class_238 original = cir.getReturnValue();
      class_238 expanded = Hitbox.expand((class_1297)(Object)this, original);
      if (expanded != original) cir.setReturnValue(expanded);
   }
}

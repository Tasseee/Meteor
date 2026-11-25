package dev.nightfall.mixin;

import dev.nightfall.modules.Hitbox;
import net.minecraft.class_1297;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1297.class)
public abstract class EntityHitboxMixin {
   // Minecraft uses getTargetingMargin() when deciding whether an entity ray intersects.
   // Expanding this value affects targeting, unlike only changing the box used by debug rendering.
   @Inject(method = "method_5871", at = @At("RETURN"), cancellable = true)
   private void nightfall$expandTargetingMargin(CallbackInfoReturnable<Float> cir) {
      float original = cir.getReturnValue();
      float expanded = Hitbox.adjustTargetingMargin((class_1297)(Object)this, original);
      if (expanded != original) cir.setReturnValue(expanded);
   }
}

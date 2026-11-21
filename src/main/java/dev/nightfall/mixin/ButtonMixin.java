package dev.nightfall.mixin;

import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_4264;
import net.minecraft.class_4185;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the Nightfall background to standard buttons only.
 * Coordinates and dimensions are read through ClickableWidget's public API;
 * no inherited fields are shadowed on PressableWidget.
 */
@Mixin(class_4264.class)
abstract class ButtonMixin {
   @Unique private float nightfall$hover;
   @Unique private long nightfall$lastFrameNanos;

   @Inject(method = "method_75794(Lnet/minecraft/class_332;)V", at = @At("HEAD"), cancellable = true)
   private void nightfall$buttonBackground(class_332 context, CallbackInfo ci) {
      if (!((Object)this instanceof class_4185)) return;

      class_339 widget = (class_339)(Object)this;
      long now = System.nanoTime();
      float elapsed = this.nightfall$lastFrameNanos == 0L ? 1.0F / 60.0F
         : Math.min(0.1F, Math.max(0.0F, (now - this.nightfall$lastFrameNanos) / 1_000_000_000.0F));
      this.nightfall$lastFrameNanos = now;

      float target = widget.field_22763 && widget.method_49606() ? 1.0F : 0.0F;
      float blend = 1.0F - (float)Math.exp(-14.0F * elapsed);
      this.nightfall$hover += (target - this.nightfall$hover) * blend;

      int x = widget.method_46426();
      int y = widget.method_46427();
      int width = widget.method_25368();
      int height = widget.method_25364();
      int fill = widget.field_22763
         ? CleanUi.lerp(CleanUi.BTN, CleanUi.BTN_HOVER, this.nightfall$hover)
         : CleanUi.BTN_OFF;
      int border = widget.field_22763
         ? CleanUi.lerp(CleanUi.BORDER, CleanUi.ACCENT, this.nightfall$hover)
         : CleanUi.OUTLINE;

      CleanUi.roundRect(context, x - 1, y - 1, width + 2, height + 2, CleanUi.RADIUS + 1, border);
      CleanUi.roundRect(context, x, y, width, height, CleanUi.RADIUS, fill);
      // Keep vanilla label rendering, narration, focus and input behavior intact.
      ci.cancel();
   }
}

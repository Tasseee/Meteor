package dev.nightfall.mixin;

import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_4264;
import net.minecraft.class_4185;
import net.minecraft.class_2561;
import net.minecraft.class_327;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Einheitlicher Stil fuer normale Buttons. Slider, Checkboxen und andere Widgets
 * behalten ihre jeweilige Vanilla-Darstellung.
 */
@Mixin(class_4264.class)
abstract class ButtonMixin {
   @Shadow public int field_22760;
   @Shadow public int field_22761;
   @Shadow public int field_22758;
   @Shadow public int field_22759;
   @Shadow public boolean field_22763;
   @Shadow public class_2561 field_22754;

   @Unique private float nightfall$hover;
   @Unique private long nightfall$lastFrameNanos;

   @Inject(method = "method_48579(Lnet/minecraft/class_332;IIF)V", at = @At("HEAD"), cancellable = true)
   private void nightfall$button(class_332 context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!((Object)this instanceof class_4185)) return;

      long now = System.nanoTime();
      float elapsed = this.nightfall$lastFrameNanos == 0L ? 1.0F / 60.0F
         : Math.min(0.1F, Math.max(0.0F, (now - this.nightfall$lastFrameNanos) / 1_000_000_000.0F));
      this.nightfall$lastFrameNanos = now;

      float target = this.field_22763 && ((class_339)(Object)this).method_49606() ? 1.0F : 0.0F;
      float blend = 1.0F - (float)Math.exp(-14.0F * elapsed);
      this.nightfall$hover += (target - this.nightfall$hover) * blend;

      int x = this.field_22760;
      int y = this.field_22761;
      int width = this.field_22758;
      int height = this.field_22759;
      int fill = this.field_22763
         ? CleanUi.lerp(CleanUi.BTN, CleanUi.BTN_HOVER, this.nightfall$hover)
         : CleanUi.BTN_OFF;
      int border = this.field_22763
         ? CleanUi.lerp(CleanUi.BORDER, CleanUi.ACCENT, this.nightfall$hover)
         : CleanUi.OUTLINE;

      CleanUi.roundRect(context, x - 1, y - 1, width + 2, height + 2, CleanUi.RADIUS + 1, border);
      CleanUi.roundRect(context, x, y, width, height, CleanUi.RADIUS, fill);

      class_327 textRenderer = class_310.method_1551().field_1772;
      context.method_27534(textRenderer, this.field_22754, x + width / 2, y + (height - 8) / 2,
         this.field_22763 ? CleanUi.TEXT : CleanUi.TEXT_OFF);
      ci.cancel();
   }
}

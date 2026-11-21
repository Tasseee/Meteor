package dev.nightfall.mixin;

import net.minecraft.class_332;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt den Hintergrund aller normalen Screens und des Titelbildschirms.
 * Gilt auch fuer Menues, die waehrend einer laufenden Welt geoeffnet werden.
 */
@Mixin(class_437.class)
abstract class ScreenBackgroundMixin {
   @Shadow public int field_22789;
   @Shadow public int field_22790;

   @Inject(method = "method_25420", at = @At("HEAD"), cancellable = true)
   private void nightfall$menuBackground(class_332 context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      CleanUi.background(context, this.field_22789, this.field_22790);
      ci.cancel();
   }

   @Inject(method = "method_57728", at = @At("HEAD"), cancellable = true)
   private void nightfall$titleBackground(class_332 context, float delta, CallbackInfo ci) {
      CleanUi.background(context, this.field_22789, this.field_22790);
      ci.cancel();
   }
}

package dev.nightfall.mixin;

import dev.nightfall.gui.CleanUi;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces backgrounds only on menus outside an active world.
 * In-game screens (inventory, containers, pause menu, etc.) retain vanilla rendering.
 */
@Mixin(class_437.class)
abstract class ScreenBackgroundMixin {
   @Shadow public int field_22789;
   @Shadow public int field_22790;

   @Unique
   private static boolean nightfall$worldIsOpen() {
      class_310 client = class_310.method_1551();
      return client != null && client.field_1687 != null;
   }

   @Inject(method = "method_25420", at = @At("HEAD"), cancellable = true)
   private void nightfall$menuBackground(class_332 context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (nightfall$worldIsOpen()) return;

      CleanUi.background(context, this.field_22789, this.field_22790);
      ci.cancel();
   }

   @Inject(method = "method_57728", at = @At("HEAD"), cancellable = true)
   private void nightfall$titleBackground(class_332 context, float delta, CallbackInfo ci) {
      if (nightfall$worldIsOpen()) return;

      CleanUi.background(context, this.field_22789, this.field_22790);
      ci.cancel();
   }
}

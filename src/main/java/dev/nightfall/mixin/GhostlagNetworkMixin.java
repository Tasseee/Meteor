package dev.nightfall.mixin;

import dev.nightfall.modules.Ghostlag;
import dev.nightfall.modules.GhostlagManager;
import net.minecraft.class_2547;
import net.minecraft.class_2596;
import net.minecraft.class_2602;
import net.minecraft.class_2535;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2535.class)
abstract class GhostlagNetworkMixin {
   @Inject(method = "method_10759", at = @At("HEAD"), cancellable = true)
   private static void nightfall$interceptPlayerMovement(class_2596<?> packet, class_2547 listener, CallbackInfo ci) {
      if (!(listener instanceof class_2602) || !Ghostlag.isMovementPacket(packet)) return;

      Ghostlag module;
      try {
         module = dev.nightfall.module.ModuleManager.get(Ghostlag.class);
      } catch (IllegalStateException ignored) {
         return;
      }
      if (!module.isEnabled()) return;

      ci.cancel();
      class_310.method_1551().execute(() -> GhostlagManager.handle(packet, listener));
   }
}
package dev.nightfall.mixin;

import dev.nightfall.modules.FakeLag;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import io.netty.channel.ChannelFutureListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2535.class)
abstract class FakeLagNetworkMixin {
   @Inject(method = "method_10752", at = @At("HEAD"), cancellable = true)
   private void nightfall$delayMovement(class_2596<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
      FakeLag.intercept((class_2535)(Object)this, packet, ci);
   }
}

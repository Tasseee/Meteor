package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Fog;
import net.minecraft.class_758;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(class_758.class)
public abstract class FogRendererMixin {
   @ModifyArgs(
      method = "method_3211",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_758;method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
      )
   )
   private void nightfall$modifyFog(Args args) {
      try {
         Fog fog = ModuleManager.get(Fog.class);
         if (!fog.isEnabled()) return;

         float envStart = (float) args.get(3);
         float envEnd = (float) args.get(4);
         float renderStart = (float) args.get(5);
         float renderEnd = (float) args.get(6);
         float skyEnd = (float) args.get(7);
         float cloudEnd = (float) args.get(8);

         args.set(3, fog.envStart(envStart));
         args.set(4, fog.envEnd(envEnd));
         args.set(5, fog.renderStart(renderStart));
         args.set(6, fog.renderEnd(renderEnd));
         args.set(7, fog.skyEnd(skyEnd));
         args.set(8, fog.skyEnd(cloudEnd));

         Vector4f color = (Vector4f) args.get(2);
         fog.modifyColor(color);
      } catch (Throwable ignored) {
      }
   }
}

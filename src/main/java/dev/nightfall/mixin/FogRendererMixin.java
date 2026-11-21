package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Fog;
import net.minecraft.class_758;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_758.class)
public abstract class FogRendererMixin {
   // ModuleManager.get() scans the complete module list. Cache the singleton
   // so the six fog hooks do not repeat that scan on every fog update.
   private static Fog cachedFog;

   private static final String APPLY = "method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

   @Inject(method = APPLY, at = @At("HEAD"))
   private void nightfall$color(java.nio.ByteBuffer buffer, int pos, Vector4f color,
      float envStart, float envEnd, float rdStart, float rdEnd, float skyEnd, float cloudEnd,
      CallbackInfo ci) {
      try {
         Fog f = enabledFog();
         if (f != null) f.modifyColor(color);
      } catch (Throwable ignored) {
      }
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private float nightfall$envStart(float v) {
      Fog f = enabledFog();
      return f != null ? f.envStart(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 1)
   private float nightfall$envEnd(float v) {
      Fog f = enabledFog();
      return f != null ? f.envEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 2)
   private float nightfall$renderStart(float v) {
      Fog f = enabledFog();
      return f != null ? f.renderStart(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float nightfall$renderEnd(float v) {
      Fog f = enabledFog();
      return f != null ? f.renderEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 4)
   private float nightfall$skyEnd(float v) {
      Fog f = enabledFog();
      return f != null ? f.skyEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 5)
   private float nightfall$cloudEnd(float v) {
      Fog f = enabledFog();
      return f != null ? f.skyEnd(v) : v;
   }

   private Fog enabledFog() {
      Fog f = cachedFog;
      if (f == null) {
         try {
            f = ModuleManager.get(Fog.class);
            cachedFog = f;
         } catch (Throwable ignored) {
            return null;
         }
      }
      return f.isEnabled() ? f : null;
   }
}

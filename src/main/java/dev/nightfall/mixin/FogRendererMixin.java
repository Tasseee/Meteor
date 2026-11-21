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
   private static final String APPLY = "method_71110(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V";

   @Inject(method = APPLY, at = @At("HEAD"))
   private void nightfall$color(java.nio.ByteBuffer buffer, int pos, Vector4f color,
      float envStart, float envEnd, float rdStart, float rdEnd, float skyEnd, float cloudEnd,
      CallbackInfo ci) {
      try {
         Fog f = ModuleManager.get(Fog.class);
         if (f.isEnabled()) f.modifyColor(color);
      } catch (Throwable ignored) {
      }
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private float nightfall$envStart(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.envStart(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 1)
   private float nightfall$envEnd(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.envEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 2)
   private float nightfall$renderStart(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.renderStart(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float nightfall$renderEnd(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.renderEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 4)
   private float nightfall$skyEnd(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.skyEnd(v) : v;
   }

   @ModifyVariable(method = APPLY, at = @At("HEAD"), argsOnly = true, ordinal = 5)
   private float nightfall$cloudEnd(float v) {
      Fog module = fog();
      return module != null && module.isEnabled() ? module.skyEnd(v) : v;
   }

   private Fog fog() {
      try {
         return ModuleManager.get(Fog.class);
      } catch (Throwable ignored) {
         return null;
      }
   }
}

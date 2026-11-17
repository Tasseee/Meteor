package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.GhostSkin;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.class_10055;
import net.minecraft.class_1007;
import net.minecraft.class_11659;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3883;
import net.minecraft.class_3887;
import net.minecraft.class_4587;
import net.minecraft.class_591;
import net.minecraft.class_765;

public final class GhostSkinFeatureRenderer extends class_3887<class_10055, class_591> {
   public GhostSkinFeatureRenderer(class_3883 context) {
      super(context);
   }

   public static void register() {
      LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
         if (entityRenderer instanceof class_1007 playerRenderer) {
            registrationHelper.register(new GhostSkinFeatureRenderer(playerRenderer));
         }
      });
   }

   @Override
   public void method_4199(
      class_4587 matrices,
      class_11659 queue,
      int light,
      class_10055 state,
      float limbAngle,
      float limbDistance
   ) {
      GhostSkin module;
      try {
         module = ModuleManager.get(GhostSkin.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled()) return;

      class_310 mc = class_310.method_1551();
      // Never render this overlay on other players. Identity is checked by UUID.
      if (mc.field_1724 == null || state.field_53528 != mc.field_1724.method_5628()) return;
      if (state.field_53520 == null || state.field_53520.comp_1626() == null) return;

      class_2960 texture = state.field_53520.comp_1626().comp_3627();
      if (texture == null) return;

      double time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9;
      float pulseFactor = 1.0F + module.pulse() * (float)Math.sin(time * 3.0F);
      class_591 model = this.method_17165();

      // Multiple expanded copies create the soft 3D energy shell from the archive.
      int shells = module.shells();
      for (int i = shells; i >= 1; i--) {
         float fade = 1.0F - 0.65F * (i - 1) / Math.max(1, shells);
         float wave = module.wave() * 0.014F * (float)Math.sin(time * 2.2 + i * 0.9);
         float spread = 1.0F + module.shellSpread() * i;
         float layerAlpha = module.shellAlpha() * pulseFactor * fade;
         submit(
            matrices, queue, state, model, texture,
            spread + wave, spread - wave * 0.8F, spread + wave,
            layerAlpha, module.color(i * 0.04F)
         );
      }

      // Main translucent skin layer.
      submit(matrices, queue, state, model, texture, 1.01F, 1.01F, 1.01F,
         module.alpha() * pulseFactor, module.color());

      // Optional outer aura retained as a separate control.
      if (module.halo()) {
         float haloScale = module.haloScale() * (1.0F + 0.02F * (float)Math.sin(time * 2.0F));
         submit(matrices, queue, state, model, texture, haloScale, haloScale, haloScale,
            module.haloAlpha() * pulseFactor, module.color(0.12F));
      }
   }

   private void submit(
      class_4587 matrices,
      class_11659 queue,
      class_10055 state,
      class_591 model,
      class_2960 texture,
      float scaleX,
      float scaleY,
      float scaleZ,
      float alpha,
      int rgb
   ) {
      int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      int argb = a << 24 | (rgb & 0xFFFFFF);
      matrices.method_22903();
      try {
         matrices.method_22904(0.0, 0.75, 0.0);
         matrices.method_22905(scaleX, scaleY, scaleZ);
         matrices.method_22904(0.0, -0.75, 0.0);
         this.method_23196(model, texture, matrices, queue, class_765.field_32767, state, argb, 0);
      } finally {
         matrices.method_22909();
      }
   }
}
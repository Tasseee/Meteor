package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.GlassChams;
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

/**
 * Uses Minecraft's own player model/state so crouching, swimming, elytra and
 * limb animation stay in sync with the real player renderer.
 */
public final class GlassChamsFeatureRenderer extends class_3887<class_10055, class_591> {
   public GlassChamsFeatureRenderer(class_3883 context) {
      super(context);
   }

   public static void register() {
      LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
         if (entityRenderer instanceof class_1007 playerRenderer) {
            helper.register(new GlassChamsFeatureRenderer(playerRenderer));
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
      GlassChams module;
      try {
         module = ModuleManager.get(GlassChams.class);
      } catch (Throwable ignored) {
         return;
      }

      if (!module.shouldRenderPlayer(state.field_53528)) return;
      if (state.field_53520 == null || state.field_53520.comp_1626() == null) return;

      class_2960 texture = state.field_53520.comp_1626().comp_3627();
      if (texture == null) return;

      double time = System.nanoTime() / 1_000_000_000.0;
      float pulse = module.shimmer() ? 0.82F + 0.18F * (float)Math.sin(time * 2.4) : 1.0F;
      class_591 model = this.method_17165();

      int layers = module.glowLayers();
      for (int i = layers; i >= 1; i--) {
         float fade = 1.0F - 0.62F * (i - 1) / Math.max(1, layers);
         float spread = 1.0F + module.glowSpread() * i;
         float wave = module.shimmer() ? 0.008F * (float)Math.sin(time * 2.0 + i * 0.8) : 0.0F;
         submit(
            matrices, queue, state, model, texture,
            spread + wave, spread - wave * 0.7F, spread + wave,
            module.glowAlpha() * pulse * fade,
            module.color(i * 0.045F)
         );
      }

      float alpha = module.coverSkin() ? 1.0F : module.fillAlpha() * pulse;
      submit(matrices, queue, state, model, texture, 1.005F, 1.005F, 1.005F,
         alpha, module.color());
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

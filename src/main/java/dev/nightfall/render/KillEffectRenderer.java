package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.KillEffect;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/**
 * Premium filled-mesh rendering for KillEffect.
 * Uses layered translucent geometry, never the legacy line pipeline.
 */
public final class KillEffectRenderer {
   private static final int RING_SEGMENTS = 64;
   private static final int CYLINDER_SEGMENTS = 32;

   private KillEffectRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(KillEffectRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      KillEffect module;
      try {
         module = ModuleManager.get(KillEffect.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled() || module.effects().isEmpty()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
      long now = System.nanoTime();

      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         for (KillEffect.Effect effect : module.effects()) {
            double progress = (now - effect.startNanos()) / (double)module.durationNanos();
            if (progress < 0.0 || progress >= 1.0) continue;

            double scale = module.scale();
            if (module.ringEnabled()) drawShockwave(fills, matrix, module, effect, progress, now, scale);
            if (module.pillarEnabled()) drawLightPillar(fills, matrix, module, effect, progress, now, scale);
            if (module.sparkCount() > 0) drawSparkBurst(fills, matrix, module, effect, progress, now, scale);
            if (module.soulsEnabled()) drawSoulRibbons(fills, matrix, module, effect, progress, now, scale);
         }
      } finally {
         matrices.method_22909();
      }
   }

   // Three concentric bands, each with a soft inner edge and a sharp luminous rim.
   private static void drawShockwave(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                     double progress, long now, double scale) {
      double phase = Math.min(1.0, progress / 0.58);
      if (phase >= 1.0) return;
      double eased = 1.0 - Math.pow(1.0 - phase, 3.0);
      double radius = (0.35 + eased * 6.2) * scale;
      double fade = Math.pow(1.0 - phase, 1.25);
      double y = fx.pos().field_1351 + 0.035 + Math.sin(progress * Math.PI) * 0.025;

      drawGroundBand(out, m, mod, fx, now, radius + 0.10 * scale, 0.82 * scale, y,
         (float)(0.13 * fade), 0.25);
      drawGroundBand(out, m, mod, fx, now, radius, 0.30 * scale, y + 0.008,
         (float)(0.72 * fade), 0.0);
      drawGroundBand(out, m, mod, fx, now, radius * 0.68, 0.055 * scale, y + 0.014,
         (float)(0.78 * fade), 0.42);

      // Small radial glyphs make the wave read as an energy seal rather than a plain ring.
      int glyphs = 18;
      for (int i = 0; i < glyphs; i++) {
         double angle = i * Math.PI * 2.0 / glyphs + progress * 0.7;
         double r = radius * (0.76 + 0.035 * Math.sin(progress * 8.0 + i));
         double length = (0.16 + 0.08 * Math.sin(i * 1.7)) * scale;
         double cx = fx.pos().field_1352 + Math.cos(angle) * r;
         double cz = fx.pos().field_1350 + Math.sin(angle) * r;
         drawGroundSlash(out, m, cx, cz, angle, length, 0.028 * scale, y + 0.022,
            rgba(mod.color(i / (double)glyphs, fx.seed(), now), (float)(0.82 * fade)));
      }
   }

   private static void drawGroundBand(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                      long now, double radius, double width, double y, float alpha, double colorOffset) {
      if (radius <= 0.0 || alpha <= 0.005F) return;
      double inner = Math.max(0.0, radius - width * 0.5);
      double outer = radius + width * 0.5;
      for (int i = 0; i < RING_SEGMENTS; i++) {
         double a0 = i * Math.PI * 2.0 / RING_SEGMENTS;
         double a1 = (i + 1) * Math.PI * 2.0 / RING_SEGMENTS;
         int rgb = mod.color((i / (double)RING_SEGMENTS + colorOffset) % 1.0, fx.seed(), now);
         float[] soft = rgba(rgb, alpha * 0.16F);
         float[] bright = rgba(rgb, alpha);
         quad(out, m,
            (float)(fx.pos().field_1352 + Math.cos(a0) * inner), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * inner),
            (float)(fx.pos().field_1352 + Math.cos(a0) * outer), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * outer),
            (float)(fx.pos().field_1352 + Math.cos(a1) * outer), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * outer),
            (float)(fx.pos().field_1352 + Math.cos(a1) * inner), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * inner),
            soft, bright, bright, soft);
      }
   }

   private static void drawGroundSlash(class_4588 out, Matrix4f m, double x, double z, double angle,
                                       double length, double halfWidth, double y, float[] color) {
      double tx = -Math.sin(angle) * length * 0.5;
      double tz = Math.cos(angle) * length * 0.5;
      double nx = Math.cos(angle) * halfWidth;
      double nz = Math.sin(angle) * halfWidth;
      quad(out, m,
         (float)(x - tx - nx), (float)y, (float)(z - tz - nz),
         (float)(x - tx + nx), (float)y, (float)(z - tz + nz),
         (float)(x + tx + nx), (float)y, (float)(z + tz + nz),
         (float)(x + tx - nx), (float)y, (float)(z + tz - nz), color);
   }

   // A soft outer shell, coloured middle shell, bright core and two rotating helix ribbons.
   private static void drawLightPillar(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                       double progress, long now, double scale) {
      double rise = Math.min(1.0, progress / 0.32);
      double height = (1.0 - Math.pow(1.0 - rise, 3.0)) * 8.2 * scale;
      if (height < 0.04) return;
      double fade = Math.min(1.0, progress * 13.0) * Math.pow(1.0 - progress, 1.45);
      double[] radii = {0.58, 0.34, 0.13};
      double[] strengths = {0.12, 0.24, 0.76};
      int slices = 12;

      for (int layer = 0; layer < radii.length; layer++) {
         double r = radii[layer] * scale * (1.0 - 0.38 * progress);
         for (int j = 0; j < slices; j++) {
            double y0 = fx.pos().field_1351 + height * j / slices;
            double y1 = fx.pos().field_1351 + height * (j + 1) / slices;
            double verticalFade = Math.pow(1.0 - (j + 0.5) / slices, 1.10);
            float alpha = (float)(strengths[layer] * fade * verticalFade);
            if (alpha < 0.008F) continue;

            for (int k = 0; k < CYLINDER_SEGMENTS; k++) {
               double a0 = k * Math.PI * 2.0 / CYLINDER_SEGMENTS;
               double a1 = (k + 1) * Math.PI * 2.0 / CYLINDER_SEGMENTS;
               int rgb = mod.color((j + 0.5) / slices, fx.seed(), now);
               float[] bottom = rgba(rgb, alpha * 0.62F);
               float[] top = rgba(rgb, alpha * 0.10F);
               quad(out, m,
                  (float)(fx.pos().field_1352 + Math.cos(a0) * r), (float)y0, (float)(fx.pos().field_1350 + Math.sin(a0) * r),
                  (float)(fx.pos().field_1352 + Math.cos(a1) * r), (float)y0, (float)(fx.pos().field_1350 + Math.sin(a1) * r),
                  (float)(fx.pos().field_1352 + Math.cos(a1) * r), (float)y1, (float)(fx.pos().field_1350 + Math.sin(a1) * r),
                  (float)(fx.pos().field_1352 + Math.cos(a0) * r), (float)y1, (float)(fx.pos().field_1350 + Math.sin(a0) * r),
                  bottom, bottom, top, top);
            }
         }
      }

      // Twin twisting energy ribbons orbit the central beam.
      for (int strand = 0; strand < 2; strand++) {
         for (int i = 0; i < 48; i++) {
            double t0 = i / 48.0, t1 = (i + 1) / 48.0;
            double a0 = t0 * Math.PI * 6.0 + progress * 8.0 + strand * Math.PI;
            double a1 = t1 * Math.PI * 6.0 + progress * 8.0 + strand * Math.PI;
            double r0 = (0.24 + 0.08 * Math.sin(t0 * Math.PI * 4.0)) * scale;
            double r1 = (0.24 + 0.08 * Math.sin(t1 * Math.PI * 4.0)) * scale;
            double y0 = fx.pos().field_1351 + height * t0, y1 = fx.pos().field_1351 + height * t1;
            double x0 = fx.pos().field_1352 + Math.cos(a0) * r0, z0 = fx.pos().field_1350 + Math.sin(a0) * r0;
            double x1 = fx.pos().field_1352 + Math.cos(a1) * r1, z1 = fx.pos().field_1350 + Math.sin(a1) * r1;
            double w0 = 0.045 * scale, w1 = 0.045 * scale;
            float alpha = (float)(0.55 * fade * (1.0 - 0.32 * t0));
            if (alpha < 0.015F) continue;
            float[] color = rgba(mod.color(t0, fx.seed(), now), alpha);
            strip(out, m, x0, y0, z0, Math.cos(a0) * w0, 0.0, Math.sin(a0) * w0,
               x1, y1, z1, Math.cos(a1) * w1, 0.0, Math.sin(a1) * w1, color);
         }
      }
   }

   // Deterministic ballistic sparks with visible directional streaks and crisp star glints.
   private static void drawSparkBurst(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                      double progress, long now, double scale) {
      double seconds = (now - fx.startNanos()) / 1.0E9;
      for (int i = 0; i < mod.sparkCount(); i++) {
         double seed = i * 12.9898 + (fx.seed() % 1000) * 0.137;
         double angle = frac(Math.sin(seed) * 43758.5453) * Math.PI * 2.0;
         double speed = (2.0 + 4.0 * frac(Math.sin(seed * 1.7) * 12345.678)) * scale;
         double up = (3.0 + 5.0 * frac(Math.sin(seed * 2.3) * 9876.543)) * scale;
         double life = 0.55 + 0.45 * frac(Math.sin(seed * 3.1) * 777.77);
         double local = progress / life;
         if (local >= 1.0) continue;

         double vx = Math.cos(angle) * speed, vz = Math.sin(angle) * speed;
         double x = fx.pos().field_1352 + vx * seconds;
         double z = fx.pos().field_1350 + vz * seconds;
         double y = Math.max(fx.pos().field_1351 + 0.025, fx.pos().field_1351 + 0.72 + up * seconds - 7.0 * seconds * seconds);
         float alpha = (float)(Math.min(1.0, local * 10.0) * Math.pow(1.0 - local, 1.15));
         if (alpha < 0.018F) continue;

         double size = (0.035 + 0.035 * frac(Math.sin(seed * 2.9) * 777.7)) * scale;
         double trailTime = Math.max(0.0, seconds - 0.085);
         double tx = fx.pos().field_1352 + vx * trailTime;
         double tz = fx.pos().field_1350 + vz * trailTime;
         double ty = Math.max(fx.pos().field_1351 + 0.025, fx.pos().field_1351 + 0.72 + up * trailTime - 7.0 * trailTime * trailTime);
         double sideX = -Math.sin(angle) * size * 0.72, sideZ = Math.cos(angle) * size * 0.72;
         float[] tail = rgba(mod.color(frac(Math.sin(seed * 5.3) * 321.9), fx.seed(), now), alpha * 0.08F);
         float[] head = rgba(mod.color(frac(Math.sin(seed * 5.3) * 321.9), fx.seed(), now), alpha * 0.72F);
         strip(out, m, tx, ty, tz, sideX, 0.0, sideZ, x, y, z, sideX * 0.30, 0.0, sideZ * 0.30, tail, tail, head, head);

         int rgb = mod.color(frac(Math.sin(seed * 5.3) * 321.9), fx.seed(), now);
         float[] white = rgba(0xFFFFFF, alpha * 0.94F);
         float[] glow = rgba(rgb, alpha * 0.60F);
         // Crossed vertical glints, plus a small horizontal flash.
         quad(out, m,
            (float)(x - size), (float)(y - size * 2.6), (float)z,
            (float)x, (float)(y - size * 0.35), (float)z,
            (float)(x + size), (float)(y + size * 2.6), (float)z,
            (float)x, (float)(y + size * 0.35), (float)z, glow, white, glow, white);
         quad(out, m,
            (float)x, (float)(y - size * 2.6), (float)(z - size),
            (float)x, (float)(y - size * 0.35), (float)z,
            (float)x, (float)(y + size * 2.6), (float)(z + size),
            (float)x, (float)(y + size * 0.35), (float)z, glow, white, glow, white);
         quad(out, m,
            (float)(x - size), (float)y, (float)(z - size),
            (float)x, (float)(y + size * 0.28), (float)z,
            (float)(x + size), (float)y, (float)(z + size),
            (float)x, (float)(y - size * 0.28), (float)z, glow, white, glow, white);
      }
   }

   // Three tapered ribbon trails twist around the point where the entity died.
   private static void drawSoulRibbons(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                       double progress, long now, double scale) {
      double fade = Math.min(1.0, progress * 8.0) * Math.pow(1.0 - progress, 1.05);
      for (int strand = 0; strand < 3; strand++) {
         double offset = strand * Math.PI * 2.0 / 3.0 + (fx.seed() % 31) * 0.01;
         for (int i = 0; i < 34; i++) {
            double tail0 = i / 34.0, tail1 = (i + 1) / 34.0;
            double t0 = progress - tail0 * 0.34, t1 = progress - tail1 * 0.34;
            if (t1 < 0.0) continue;
            t0 = Math.max(0.0, t0);
            double a0 = t0 * 9.0 + offset, a1 = t1 * 9.0 + offset;
            double r0 = (0.42 + 0.88 * t0) * scale, r1 = (0.42 + 0.88 * t1) * scale;
            double x0 = fx.pos().field_1352 + Math.cos(a0) * r0;
            double z0 = fx.pos().field_1350 + Math.sin(a0) * r0;
            double x1 = fx.pos().field_1352 + Math.cos(a1) * r1;
            double z1 = fx.pos().field_1350 + Math.sin(a1) * r1;
            double y0 = fx.pos().field_1351 + 0.18 + t0 * 9.0 * scale;
            double y1 = fx.pos().field_1351 + 0.18 + t1 * 9.0 * scale;
            double width0 = (0.105 - 0.065 * tail0) * scale;
            double width1 = (0.105 - 0.065 * tail1) * scale;
            float alpha = (float)(fade * Math.pow(1.0 - tail0, 1.35) * 0.72);
            if (alpha < 0.012F) continue;

            float[] edge0 = rgba(mod.color(t0, fx.seed(), now), alpha * 0.12F);
            float[] core0 = rgba(mod.color(t0, fx.seed(), now), alpha);
            float[] edge1 = rgba(mod.color(t1, fx.seed(), now), alpha * 0.08F);
            strip(out, m, x0, y0, z0, Math.cos(a0) * width0, 0.0, Math.sin(a0) * width0,
               x1, y1, z1, Math.cos(a1) * width1, 0.0, Math.sin(a1) * width1, edge0, core0, edge1, edge1);

            // A narrow bright filament inside each wide ribbon gives it a glassy core.
            double inner0 = width0 * 0.22, inner1 = width1 * 0.22;
            float[] filament = rgba(0xFFFFFF, alpha * 0.42F);
            strip(out, m, x0, y0, z0, Math.cos(a0) * inner0, 0.0, Math.sin(a0) * inner0,
               x1, y1, z1, Math.cos(a1) * inner1, 0.0, Math.sin(a1) * inner1, filament);
         }
      }
   }

   private static void strip(class_4588 out, Matrix4f m,
      double x0, double y0, double z0, double nx0, double ny0, double nz0,
      double x1, double y1, double z1, double nx1, double ny1, double nz1, float[] color) {
      strip(out, m, x0, y0, z0, nx0, ny0, nz0, x1, y1, z1, nx1, ny1, nz1,
         color, color, color, color);
   }

   private static void strip(class_4588 out, Matrix4f m,
      double x0, double y0, double z0, double nx0, double ny0, double nz0,
      double x1, double y1, double z1, double nx1, double ny1, double nz1,
      float[] c0, float[] c1, float[] c2, float[] c3) {
      quad(out, m,
         (float)(x0 + nx0), (float)(y0 + ny0), (float)(z0 + nz0),
         (float)(x0 - nx0), (float)(y0 - ny0), (float)(z0 - nz0),
         (float)(x1 - nx1), (float)(y1 - ny1), (float)(z1 - nz1),
         (float)(x1 + nx1), (float)(y1 + ny1), (float)(z1 + nz1), c0, c1, c2, c3);
   }

   private static void quad(class_4588 out, Matrix4f m,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, float[] color) {
      quad(out, m, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, color, color, color, color);
   }

   private static void quad(class_4588 out, Matrix4f m,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3,
      float[] c0, float[] c1, float[] c2, float[] c3) {
      vertex(out, m, x0, y0, z0, c0);
      vertex(out, m, x1, y1, z1, c1);
      vertex(out, m, x2, y2, z2, c2);
      vertex(out, m, x3, y3, z3, c3);
   }

   private static void vertex(class_4588 out, Matrix4f m, float x, float y, float z, float[] color) {
      out.method_22918(m, x, y, z).method_22915(color[0], color[1], color[2], color[3]);
   }

   private static float[] rgba(int rgb, float alpha) {
      return new float[] {
         (rgb >> 16 & 255) / 255.0F,
         (rgb >> 8 & 255) / 255.0F,
         (rgb & 255) / 255.0F,
         Math.max(0.0F, Math.min(1.0F, alpha))
      };
   }

   private static double frac(double value) {
      return value - Math.floor(value);
   }
}

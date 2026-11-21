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
 * Original-shaped KillEffect renderer.
 * Retains the reference animation's timing, proportions and trajectories,
 * while using layered filled meshes for a cleaner luminous finish.
 */
public final class KillEffectRenderer {
   private static final int RING_SEGMENTS = 56;
   private static final int PILLAR_SIDES = 10;
   private static final int PILLAR_SLICES = 14;

   private KillEffectRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(KillEffectRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      KillEffect module;
      try { module = ModuleManager.get(KillEffect.class); }
      catch (Throwable ignored) { return; }
      if (!module.isEnabled() || module.effects().isEmpty()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 out = context.consumers().method_73477(class_12249.method_76019());
      long now = System.nanoTime();

      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         for (KillEffect.Effect effect : module.effects()) {
            double progress = (now - effect.startNanos()) / (double)module.durationNanos();
            if (progress < 0.0 || progress >= 1.0) continue;
            double scale = module.scale();

            if (module.ringEnabled()) drawShockwave(out, matrix, module, effect, progress, now, scale);
            if (module.pillarEnabled()) drawPillar(out, matrix, module, effect, progress, now, scale);
            if (module.sparkCount() > 0) drawSparks(out, matrix, module, effect, progress, now, scale);
            if (module.soulsEnabled()) drawSouls(out, matrix, module, effect, progress, now, scale);
         }
      } finally {
         matrices.method_22909();
      }
   }

   // Original fast-expanding ground ring with one main band and a restrained soft halo.
   private static void drawShockwave(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                     double progress, long now, double scale) {
      double phase = Math.min(1.0, progress / 0.55);
      if (phase >= 1.0) return;
      double eased = 1.0 - Math.pow(1.0 - phase, 3.0);
      double outer = eased * 6.0 * scale + 0.4;
      double width = (0.9 - 0.5 * phase) * scale;
      double inner = Math.max(0.0, outer - width);
      double fade = Math.pow(1.0 - phase, 1.3);
      double y = fx.pos().field_1351 + 0.03;

      for (int i = 0; i < RING_SEGMENTS; i++) {
         double a0 = i * Math.PI * 2.0 / RING_SEGMENTS;
         double a1 = (i + 1) * Math.PI * 2.0 / RING_SEGMENTS;
         int rgb = mod.color(i / (double)RING_SEGMENTS, fx.seed(), now);
         float[] glow = rgba(rgb, (float)(0.12 * fade));
         float[] main = rgba(rgb, (float)(0.85 * fade));
         quad(out, m,
            (float)(fx.pos().field_1352 + Math.cos(a0) * inner), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * inner),
            (float)(fx.pos().field_1352 + Math.cos(a0) * outer), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * outer),
            (float)(fx.pos().field_1352 + Math.cos(a1) * outer), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * outer),
            (float)(fx.pos().field_1352 + Math.cos(a1) * inner), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * inner),
            glow, main, main, glow);
      }

      // A very fine second edge adds a luminous outline without changing the original ring shape.
      drawThinRing(out, m, mod, fx, outer + 0.035 * scale, y + 0.012,
         (float)(0.36 * fade), now, scale);
      drawThinRing(out, m, mod, fx, outer - width * 0.82, y + 0.009,
         (float)(0.22 * fade), now, scale);
      drawThinRing(out, m, mod, fx, outer + 0.085 * scale, y + 0.006,
         (float)(0.12 * fade), now, scale);
   }

   private static void drawThinRing(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                    double radius, double y, float alpha, long now, double scale) {
      double half = 0.018 * scale;
      for (int i = 0; i < RING_SEGMENTS; i++) {
         double a0 = i * Math.PI * 2.0 / RING_SEGMENTS;
         double a1 = (i + 1) * Math.PI * 2.0 / RING_SEGMENTS;
         float[] c = rgba(mod.color(i / (double)RING_SEGMENTS, fx.seed(), now), alpha);
         quad(out, m,
            (float)(fx.pos().field_1352 + Math.cos(a0) * (radius - half)), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * (radius - half)),
            (float)(fx.pos().field_1352 + Math.cos(a0) * (radius + half)), (float)y, (float)(fx.pos().field_1350 + Math.sin(a0) * (radius + half)),
            (float)(fx.pos().field_1352 + Math.cos(a1) * (radius + half)), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * (radius + half)),
            (float)(fx.pos().field_1352 + Math.cos(a1) * (radius - half)), (float)y, (float)(fx.pos().field_1350 + Math.sin(a1) * (radius - half)), c);
      }
   }

   // The original two-cylinder pillar: broad translucent shell and thin bright core.
   private static void drawPillar(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                  double progress, long now, double scale) {
      double rise = Math.min(1.0, progress / 0.30);
      double height = (1.0 - Math.pow(1.0 - rise, 3.0)) * 9.0 * scale;
      if (height < 0.05) return;
      double fade = Math.min(1.0, progress * 14.0) * Math.pow(1.0 - progress, 1.5);
      double[] radii = {0.18 * scale, 0.55 * scale, 0.82 * scale};
      double[] strengths = {0.85, 0.25, 0.075};

      for (int layer = 0; layer < radii.length; layer++) {
         double radius = radii[layer] * (1.0 - 0.45 * progress);
         for (int slice = 0; slice < PILLAR_SLICES; slice++) {
            double y0 = fx.pos().field_1351 + height * slice / PILLAR_SLICES;
            double y1 = fx.pos().field_1351 + height * (slice + 1) / PILLAR_SLICES;
            double vertical = Math.pow(1.0 - (slice + 0.5) / PILLAR_SLICES, 1.15);
            float alpha = (float)(strengths[layer] * fade * vertical);
            if (alpha < 0.01F) continue;

            int rgb = mod.color((slice + 0.5) / PILLAR_SLICES, fx.seed(), now);
            if (layer == 0) rgb = brighten(rgb, 0.5);
            float[] bottom = rgba(rgb, alpha);
            float[] top = rgba(rgb, alpha * 0.12F);
            for (int side = 0; side < PILLAR_SIDES; side++) {
               double a0 = side * Math.PI * 2.0 / PILLAR_SIDES;
               double a1 = (side + 1) * Math.PI * 2.0 / PILLAR_SIDES;
               quad(out, m,
                  (float)(fx.pos().field_1352 + Math.cos(a0) * radius), (float)y0, (float)(fx.pos().field_1350 + Math.sin(a0) * radius),
                  (float)(fx.pos().field_1352 + Math.cos(a1) * radius), (float)y0, (float)(fx.pos().field_1350 + Math.sin(a1) * radius),
                  (float)(fx.pos().field_1352 + Math.cos(a1) * radius), (float)y1, (float)(fx.pos().field_1350 + Math.sin(a1) * radius),
                  (float)(fx.pos().field_1352 + Math.cos(a0) * radius), (float)y1, (float)(fx.pos().field_1350 + Math.sin(a0) * radius),
                  bottom, bottom, top, top);
            }
         }
      }

      // Six fine helices climb through the pillar, each with a tapered luminous filament.
      double seconds = (now - fx.startNanos()) / 1.0E9;
      for (int strand = 0; strand < 6; strand++) {
         double offset = strand * Math.PI * 2.0 / 6.0;
         for (int segment = 0; segment < 28; segment++) {
            double t0 = segment / 28.0;
            double t1 = (segment + 1) / 28.0;
            double a0 = seconds * 1.65 + offset + t0 * Math.PI * 5.0;
            double a1 = seconds * 1.65 + offset + t1 * Math.PI * 5.0;
            double r0 = 0.62 * scale * (1.0 - 0.18 * progress);
            double r1 = 0.62 * scale * (1.0 - 0.18 * progress);
            double x0 = fx.pos().field_1352 + Math.cos(a0) * r0;
            double z0 = fx.pos().field_1350 + Math.sin(a0) * r0;
            double x1 = fx.pos().field_1352 + Math.cos(a1) * r1;
            double z1 = fx.pos().field_1350 + Math.sin(a1) * r1;
            double y0 = fx.pos().field_1351 + height * t0;
            double y1 = fx.pos().field_1351 + height * t1;
            double w0 = 0.014 * scale * (1.0 - 0.55 * t0);
            double w1 = 0.014 * scale * (1.0 - 0.55 * t1);
            float strandAlpha = (float)(0.30 * fade * Math.pow(1.0 - t0, 0.5));
            int strandColor = mod.color(t0, fx.seed() + strand * 31L, now);
            strip(out, m, x0, y0, z0, Math.cos(a0) * w0, 0.0, Math.sin(a0) * w0,
               x1, y1, z1, Math.cos(a1) * w1, 0.0, Math.sin(a1) * w1,
               rgba(strandColor, strandAlpha));
         }
      }
   }

   // Original ballistic motion, gravity and per-spark lifetime are retained.
   private static void drawSparks(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                  double progress, long now, double scale) {
      int count = mod.sparkCount();
      double seconds = (now - fx.startNanos()) / 1.0E9;
      double gravity = 14.0;
      for (int k = 0; k < count; k++) {
         double seed = k * 12.9898 + (fx.seed() % 1000) * 0.137;
         double angle = frac(Math.sin(seed) * 43758.5453) * Math.PI * 2.0;
         double speed = (2.0 + 4.0 * frac(Math.sin(seed * 1.7) * 12345.678)) * scale;
         double up = (3.0 + 5.0 * frac(Math.sin(seed * 2.3) * 9876.543)) * scale;
         double life = 0.55 + 0.45 * frac(Math.sin(seed * 3.1) * 777.77);
         double local = progress / life;
         if (local >= 1.0) continue;

         double x = fx.pos().field_1352 + Math.cos(angle) * speed * seconds;
         double z = fx.pos().field_1350 + Math.sin(angle) * speed * seconds;
         double y = fx.pos().field_1351 + 0.9 + up * seconds - 0.5 * gravity * seconds * seconds;
         y = Math.max(y, fx.pos().field_1351 + 0.02);

         float alpha = (float)(Math.min(1.0, local * 12.0) * Math.pow(1.0 - local, 1.2));
         if (alpha < 0.02F) continue;
         int rgb = mod.color(frac(Math.sin(seed * 5.3) * 321.9), fx.seed(), now);
         double size = (0.05 + 0.05 * frac(Math.sin(seed * 2.9) * 777.7)) * scale;

         // Faint motion streak behind the crisp spark core.
         double previous = Math.max(0.0, seconds - 0.065);
         double tx = fx.pos().field_1352 + Math.cos(angle) * speed * previous;
         double tz = fx.pos().field_1350 + Math.sin(angle) * speed * previous;
         double ty = fx.pos().field_1351 + 0.9 + up * previous - 0.5 * gravity * previous * previous;
         ty = Math.max(ty, fx.pos().field_1351 + 0.02);
         double sideX = -Math.sin(angle) * size * 0.30;
         double sideZ = Math.cos(angle) * size * 0.30;
         strip(out, m, tx, ty, tz, sideX * 1.8, 0.0, sideZ * 1.8, x, y, z, sideX * 0.45, 0.0, sideZ * 0.45,
            rgba(rgb, alpha * 0.075F));
         strip(out, m, tx, ty, tz, sideX * 0.65, 0.0, sideZ * 0.65, x, y, z, sideX * 0.16, 0.0, sideZ * 0.16,
            rgba(brighten(rgb, 0.60), alpha * 0.30F));

         // Rotating nested cubes preserve the original spark silhouette while adding depth.
         double spin = seconds * (7.0 + (k % 5) * 1.7) + seed;
         drawRotatedCube(out, m, x, y, z, size * 1.55, spin * 0.72, spin * 0.48, rgba(rgb, alpha * 0.18F));
         drawRotatedCube(out, m, x, y, z, size, spin, spin * 0.63, rgba(rgb, alpha));
         drawRotatedCube(out, m, x, y, z, size * 0.32, -spin * 1.35, spin, rgba(0xFFFFFF, alpha * 0.86F));
      }
   }

   // The original three upward corkscrew trails, rendered as glowing particle beads.
   private static void drawSouls(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx,
                                 double progress, long now, double scale) {
      int wisps = 3;
      int trail = 26;
      double fade = Math.min(1.0, progress * 8.0) * Math.pow(1.0 - progress, 1.1);
      for (int w = 0; w < wisps; w++) {
         double offset = w * Math.PI * 2.0 / wisps;
         for (int k = 0; k < trail; k++) {
            double s = k / (double)trail;
            double t = progress - s * 0.30;
            if (t < 0.0) continue;

            double y = fx.pos().field_1351 + 0.2 + t * 10.0 * scale;
            double angle = t * 9.0 + offset;
            double radius = (0.45 + 0.9 * t) * scale;
            double x = fx.pos().field_1352 + Math.cos(angle) * radius;
            double z = fx.pos().field_1350 + Math.sin(angle) * radius;
            double alpha = fade * Math.pow(1.0 - s, 1.4);
            if (alpha < 0.02) continue;

            int rgb = mod.color(s, fx.seed(), now);
            rgb = brighten(rgb, 0.35 * (1.0 - s));
            double size = (0.11 - 0.07 * s) * scale;
            double spin = t * 5.0 + offset;
            drawRotatedCube(out, m, x, y, z, size * 1.5, spin * 0.7, spin * 0.5, rgba(rgb, (float)(alpha * 0.16)));
            drawRotatedCube(out, m, x, y, z, size, spin, spin * 0.8, rgba(rgb, (float)alpha));
            drawRotatedCube(out, m, x, y, z, size * 0.30, -spin, spin * 1.2, rgba(0xFFFFFF, (float)(alpha * 0.75)));
         }
      }
   }

   private static final int[][] CUBE_FACES = {
      {0,0,0, 0,0,1, 0,1,1, 0,1,0},
      {1,0,1, 1,0,0, 1,1,0, 1,1,1},
      {0,0,0, 1,0,0, 1,0,1, 0,0,1},
      {0,1,1, 1,1,1, 1,1,0, 0,1,0},
      {0,0,0, 0,1,0, 1,1,0, 1,0,0},
      {1,0,1, 1,1,1, 0,1,1, 0,0,1}
   };

   private static void drawRotatedCube(class_4588 out, Matrix4f m, double x, double y, double z,
                                       double size, double yaw, double pitch, float[] color) {
      double cy = Math.cos(yaw), sy = Math.sin(yaw);
      double cp = Math.cos(pitch), sp = Math.sin(pitch);
      for (int[] face : CUBE_FACES) {
         for (int v = 0; v < 4; v++) {
            double dx = face[v * 3] == 1 ? size : -size;
            double dy = face[v * 3 + 1] == 1 ? size : -size;
            double dz = face[v * 3 + 2] == 1 ? size : -size;
            double pitchedY = dy * cp - dz * sp;
            double pitchedZ = dy * sp + dz * cp;
            double px = dx * cy + pitchedZ * sy;
            double rz = -dx * sy + pitchedZ * cy;
            out.method_22918(m, (float)(x + px), (float)(y + pitchedY), (float)(z + rz))
               .method_22915(color[0], color[1], color[2], color[3]);
         }
      }
   }

   private static void drawCube(class_4588 out, Matrix4f m, double x, double y, double z, double size, float[] color) {
      float x0 = (float)(x - size), x1 = (float)(x + size);
      float y0 = (float)(y - size), y1 = (float)(y + size);
      float z0 = (float)(z - size), z1 = (float)(z + size);
      quad(out, m, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, color);
      quad(out, m, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, color);
      quad(out, m, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, color);
      quad(out, m, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, color);
      quad(out, m, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, color);
      quad(out, m, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, color);
   }

   private static void strip(class_4588 out, Matrix4f m,
      double x0, double y0, double z0, double nx0, double ny0, double nz0,
      double x1, double y1, double z1, double nx1, double ny1, double nz1, float[] color) {
      quad(out, m,
         (float)(x0 + nx0), (float)(y0 + ny0), (float)(z0 + nz0),
         (float)(x0 - nx0), (float)(y0 - ny0), (float)(z0 - nz0),
         (float)(x1 - nx1), (float)(y1 - ny1), (float)(z1 - nz1),
         (float)(x1 + nx1), (float)(y1 + ny1), (float)(z1 + nz1), color);
   }

   private static void quad(class_4588 out, Matrix4f m,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3, float[] c) {
      vertex(out, m, x0, y0, z0, c); vertex(out, m, x1, y1, z1, c);
      vertex(out, m, x2, y2, z2, c); vertex(out, m, x3, y3, z3, c);
   }

   private static void quad(class_4588 out, Matrix4f m,
      float x0, float y0, float z0, float x1, float y1, float z1,
      float x2, float y2, float z2, float x3, float y3, float z3,
      float[] c0, float[] c1, float[] c2, float[] c3) {
      vertex(out, m, x0, y0, z0, c0); vertex(out, m, x1, y1, z1, c1);
      vertex(out, m, x2, y2, z2, c2); vertex(out, m, x3, y3, z3, c3);
   }

   private static void vertex(class_4588 out, Matrix4f m, float x, float y, float z, float[] color) {
      out.method_22918(m, x, y, z).method_22915(color[0], color[1], color[2], color[3]);
   }

   private static float[] rgba(int rgb, float alpha) {
      return new float[]{(rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F,
         (rgb & 255) / 255.0F, Math.max(0.0F, Math.min(1.0F, alpha))};
   }

   private static int brighten(int rgb, double amount) {
      int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
      return (int)Math.round(r + (255-r)*amount) << 16
         | (int)Math.round(g + (255-g)*amount) << 8
         | (int)Math.round(b + (255-b)*amount);
   }

   private static double frac(double value) { return value - Math.floor(value); }
}

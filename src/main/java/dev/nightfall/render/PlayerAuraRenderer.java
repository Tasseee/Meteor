package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.PlayerAura;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/**
 * Detailed PlayerAura renderer. All elements are built from filled mesh quads,
 * so this stays compatible with Nightfall's current 1.21.11 world pipeline.
 */
public final class PlayerAuraRenderer {
   private static final int RING_SEGMENTS = 72;
   private static final int ORB_LATITUDE = 7;
   private static final int ORB_LONGITUDE = 12;

   private PlayerAuraRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(PlayerAuraRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      PlayerAura aura;
      try { aura = ModuleManager.get(PlayerAura.class); }
      catch (Throwable ignored) { return; }
      if (!aura.isEnabled()) return;

      boolean firstPerson = mc.field_1690.method_31044().method_31034();
      if (firstPerson && !aura.firstPerson()) return;

      long now = System.nanoTime();
      double elapsed = Math.max(0.0, (now - aura.activatedAtNanos()) / 1.0E9);
      double appear = clamp(elapsed / 0.9, 0.0, 1.0);
      double ease = 1.0 + 2.70158 * Math.pow(appear - 1.0, 3.0) + 1.70158 * Math.pow(appear - 1.0, 2.0);
      double scale = aura.scale() * ease;
      double alpha = aura.intensity() * Math.min(1.0, appear * 3.0) * (firstPerson ? 0.45 : 1.0);
      if (alpha < 0.01 || scale < 0.01) return;

      // Position is read once per frame; the world-render callback itself runs between game ticks.
      double px = mc.field_1724.method_23317();
      double py = mc.field_1724.method_23318();
      double pz = mc.field_1724.method_23321();
      double time = (now % 3_600_000_000_000L) / 1.0E9;
      double speed = aura.speed();

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 out = context.consumers().method_73477(class_12249.method_76019());
      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         if (aura.shellEnabled()) drawBodyShell(out, matrix, aura, px, py, pz, time, scale, alpha);
         if (aura.ringsEnabled()) drawEnergyRings(out, matrix, aura, px, py, pz, time, scale, alpha, speed);
         if (aura.orbCount() > 0) drawOrbitingOrbs(out, matrix, aura, px, py, pz, time, scale, alpha, speed);
         if (aura.emberCount() > 0) drawEmbers(out, matrix, aura, px, py, pz, time, scale, alpha, speed);
         if (aura.sigilEnabled()) drawGroundSigil(out, matrix, aura, mc, px, py, pz, time, scale, alpha, speed);
      } finally {
         matrices.method_22909();
      }
   }

   // Thin, breathing energy shell, with a brighter inner core and animated vertical filaments.
   private static void drawBodyShell(class_4588 out, Matrix4f m, PlayerAura aura,
                                     double x, double y, double z, double time, double scale, double alpha) {
      int sides = 32;
      int slices = 10;
      double height = 1.95 * scale;
      double pulse = 0.5 + 0.5 * Math.sin(time * 2.2);
      for (int layer = 0; layer < 2; layer++) {
         double radius = (layer == 0 ? 0.55 : 0.38) * scale * (1.0 + 0.035 * Math.sin(time * 2.2 + layer));
         for (int j = 0; j < slices; j++) {
            double y0 = y + height * j / slices;
            double y1 = y + height * (j + 1) / slices;
            double mid = (j + 0.5) / slices;
            double vertical = Math.pow(1.0 - mid, 0.88);
            float a = (float)((layer == 0 ? 0.10 : 0.12) * vertical * alpha * (0.76 + 0.24 * pulse));
            if (a < 0.005F) continue;
            for (int s = 0; s < sides; s++) {
               double a0 = s * Math.PI * 2.0 / sides + time * 0.3;
               double a1 = (s + 1) * Math.PI * 2.0 / sides + time * 0.3;
               float[] c0 = rgba(aura.color(mid, time), a);
               float[] c1 = rgba(aura.color(mid + 0.1, time), a * 0.55F);
               quad(out, m,
                  (float)(x + Math.cos(a0) * radius), (float)y0, (float)(z + Math.sin(a0) * radius),
                  (float)(x + Math.cos(a1) * radius), (float)y0, (float)(z + Math.sin(a1) * radius),
                  (float)(x + Math.cos(a1) * radius), (float)y1, (float)(z + Math.sin(a1) * radius),
                  (float)(x + Math.cos(a0) * radius), (float)y1, (float)(z + Math.sin(a0) * radius),
                  c0, c0, c1, c1);
            }
         }
      }

      // Fine vertical strands ripple over the translucent cylinder.
      for (int strand = 0; strand < 18; strand++) {
         double angle = strand * Math.PI * 2.0 / 18.0 + time * 0.24;
         double r = 0.555 * scale;
         double x0 = x + Math.cos(angle) * r, z0 = z + Math.sin(angle) * r;
         for (int i = 0; i < 12; i++) {
            double t0 = i / 12.0, t1 = (i + 1) / 12.0;
            float a = (float)(alpha * 0.22 * Math.pow(1.0 - t0, 0.8));
            float[] c = rgba(aura.color(t0, time), a);
            double wave = 0.018 * Math.sin(time * 3.0 + t0 * 12.0 + strand);
            double x1 = x0 + Math.cos(angle) * wave, z1 = z0 + Math.sin(angle) * wave;
            verticalStrip(out, m, x0, y + height * t0, z0, x1, y + height * t1, z1, 0.012 * scale, c);
         }
      }
   }

   // Three counter-rotating tilted energy bands, each with a comet head and a long fading tail.
   private static void drawEnergyRings(class_4588 out, Matrix4f m, PlayerAura aura,
                                       double x, double y, double z, double time, double scale, double alpha, double speed) {
      double[] heights = {0.30, 0.95, 1.60};
      double[] radii = {0.95, 1.10, 0.90};
      double[] tilts = {0.35, 0.50, 0.30};
      double[] dirs = {1.0, -1.0, 1.0};
      for (int ring = 0; ring < 3; ring++) {
         double bob = 0.06 * Math.sin(time * 1.3 + ring * 2.0);
         double cx = x, cy = y + heights[ring] * scale + bob, cz = z;
         double radius = radii[ring] * scale;
         double tilt = tilts[ring] + 0.12 * Math.sin(time * 0.9 * speed + ring);
         double phi = time * 0.6 * speed * dirs[ring] + ring * 2.1;
         double nx = Math.sin(tilt) * Math.cos(phi);
         double ny = Math.cos(tilt);
         double nz = Math.sin(tilt) * Math.sin(phi);
         double hx = Math.abs(ny) < 0.99 ? 0.0 : 1.0;
         double hy = Math.abs(ny) < 0.99 ? 1.0 : 0.0;
         double hz = 0.0;
         double ux = ny * hz - nz * hy, uy = nz * hx - nx * hz, uz = nx * hy - ny * hx;
         double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
         ux /= ul; uy /= ul; uz /= ul;
         double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;

         double head = time * 1.8 * speed * dirs[ring] + ring * 1.3;
         double band = 0.045 * scale;
         int segments = 72;
         for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2.0 / segments;
            double a1 = (i + 1) * Math.PI * 2.0 / segments;
            double amid = (a0 + a1) * 0.5;
            double behind = frac(dirs[ring] * (head - amid) / (Math.PI * 2.0));
            double tail = Math.pow(1.0 - behind, 2.2);
            float opacity = (float)((0.12 + 0.88 * tail) * alpha);
            int rgb = aura.color(tail, time);
            if (tail > 0.85) rgb = brighten(rgb, (tail - 0.85) / 0.15 * 0.55);
            float[] c = rgba(rgb, opacity);
            double r0x = Math.cos(a0) * radius, r0y = Math.sin(a0) * radius;
            double r1x = Math.cos(a1) * radius, r1y = Math.sin(a1) * radius;
            double p0x = cx + ux * r0x + vx * r0y, p0y = cy + uy * r0x + vy * r0y, p0z = cz + uz * r0x + vz * r0y;
            double p1x = cx + ux * r1x + vx * r1y, p1y = cy + uy * r1x + vy * r1y, p1z = cz + uz * r1x + vz * r1y;
            double thick = band * (0.6 + 0.8 * tail);
            quad(out, m,
               (float)(p0x - nx * thick), (float)(p0y - ny * thick), (float)(p0z - nz * thick),
               (float)(p1x - nx * thick), (float)(p1y - ny * thick), (float)(p1z - nz * thick),
               (float)(p1x + nx * thick), (float)(p1y + ny * thick), (float)(p1z + nz * thick),
               (float)(p0x + nx * thick), (float)(p0y + ny * thick), (float)(p0z + nz * thick), c);
         }

         // A bright, layered comet head rides the band and leaves a short secondary glow.
         double headAngle = head;
         double headX = cx + ux * Math.cos(headAngle) * radius + vx * Math.sin(headAngle) * radius;
         double headY = cy + uy * Math.cos(headAngle) * radius + vy * Math.sin(headAngle) * radius;
         double headZ = cz + uz * Math.cos(headAngle) * radius + vz * Math.sin(headAngle) * radius;
         drawOrb(out, m, headX, headY, headZ, 0.105 * scale, aura.color(1.0, time), (float)(0.95 * alpha));
      }
   }

   // Orbiting lights have spherical layered cores and individually fading trail beads.
   private static void drawOrbitingOrbs(class_4588 out, Matrix4f m, PlayerAura aura,
                                        double x, double y, double z, double time, double scale, double alpha, double speed) {
      int count = aura.orbCount();
      for (int i = 0; i < count; i++) {
         double baseAngle = i * Math.PI * 2.0 / count;
         double rate = (1.1 + 0.18 * (i % 3)) * speed * (i % 2 == 0 ? 1.0 : -1.0);
         for (int trail = 7; trail >= 0; trail--) {
            double lag = trail * 0.10;
            double t = time - lag;
            double angle = baseAngle + t * rate;
            double h = 0.35 + 1.35 * (0.5 + 0.5 * Math.sin(t * 0.9 * speed + i * 1.3));
            double radius = (1.0 + 0.15 * Math.sin(t * 1.4 * speed + i)) * scale;
            double ox = x + Math.cos(angle) * radius;
            double oy = y + h * scale;
            double oz = z + Math.sin(angle) * radius;
            double fade = Math.pow(1.0 - trail / 8.0, 1.55);
            double size = (0.085 * fade + 0.018) * scale;
            int rgb = aura.color(frac(i * 0.17 + 0.3), time);
            if (trail == 0) rgb = brighten(rgb, 0.62);
            drawOrb(out, m, ox, oy, oz, size * 1.9, rgb, (float)(fade * alpha * 0.22));
            drawOrb(out, m, ox, oy, oz, size, rgb, (float)(fade * alpha * (trail == 0 ? 1.0 : 0.45)));
         }
      }
   }

   // Stable deterministic embers spiral upward, pulse, and leave tapered luminous streaks.
   private static void drawEmbers(class_4588 out, Matrix4f m, PlayerAura aura,
                                  double x, double y, double z, double time, double scale, double alpha, double speed) {
      for (int k = 0; k < aura.emberCount(); k++) {
         double seed = k * 7.13;
         double h1 = frac(Math.sin(seed) * 43758.5453);
         double h2 = frac(Math.sin(seed * 1.7) * 12345.678);
         double h3 = frac(Math.sin(seed * 3.1) * 999.1);
         double phase = frac(time * 0.35 * speed * (0.7 + 0.6 * h3) + h2);
         double angle = h1 * Math.PI * 2.0 + phase * 2.2;
         double radius = (0.35 + 0.55 * h2 + 0.12 * Math.sin(phase * 6.0 + k)) * scale;
         double height = phase * 2.4 * scale;
         double pulse = Math.sin(phase * Math.PI);
         double opacity = pulse * pulse * alpha;
         if (opacity < 0.02) continue;
         double ex = x + Math.cos(angle) * radius;
         double ez = z + Math.sin(angle) * radius;
         double ey = y + height;
         double size = (0.03 + 0.03 * h1) * scale;
         int rgb = aura.color(h3, time);
         float[] tail = rgba(rgb, (float)(opacity * 0.18));
         double previousPhase = Math.max(0.0, phase - 0.045);
         double previousAngle = h1 * Math.PI * 2.0 + previousPhase * 2.2;
         double previousRadius = (0.35 + 0.55 * h2 + 0.12 * Math.sin(previousPhase * 6.0 + k)) * scale;
         double px = x + Math.cos(previousAngle) * previousRadius;
         double pz = z + Math.sin(previousAngle) * previousRadius;
         double py = y + previousPhase * 2.4 * scale;
         strip(out, m, px, py, pz, size * 0.45, 0.0, size * 0.45,
            ex, ey, ez, size * 0.12, 0.0, size * 0.12, tail);
         drawOrb(out, m, ex, ey, ez, size * 1.8, rgb, (float)(opacity * 0.28));
         drawOrb(out, m, ex, ey, ez, size * 0.62, 0xFFFFFF, (float)(opacity * 0.92));
      }
   }

   // Ground sigil stays on the ground even while the player jumps.
   private static void drawGroundSigil(class_4588 out, Matrix4f m, PlayerAura aura, class_310 mc,
                                       double x, double y, double z, double time, double scale, double alpha, double speed) {
      double ground = groundY(mc, x, y, z) + 0.035;
      double pulse = 0.75 + 0.25 * Math.sin(time * 2.0);
      drawFlatRing(out, m, aura, x, z, ground, 1.30 * scale, 0.05 * scale, time, (float)(0.80 * pulse * alpha));
      drawFlatRing(out, m, aura, x, z, ground + 0.006, 1.48 * scale, 0.03 * scale, time, (float)(0.68 * pulse * alpha));
      drawFlatRing(out, m, aura, x, z, ground + 0.012, 0.84 * scale, 0.018 * scale, time, (float)(0.40 * pulse * alpha));

      // Twenty-four calibrated tick marks around the two outer rings.
      for (int i = 0; i < 24; i++) {
         double angle = i * Math.PI * 2.0 / 24.0 + time * 0.25 * speed;
         double r0 = 1.34 * scale, r1 = (i % 3 == 0 ? 1.47 : 1.43) * scale;
         double width = (i % 3 == 0 ? 0.026 : 0.016) * scale;
         double ca = Math.cos(angle), sa = Math.sin(angle);
         float[] color = rgba(aura.color(i / 24.0, time), (float)(0.62 * pulse * alpha));
         flatQuad(out, m, x, z, ground + 0.018,
            ca * r0 - sa * width, sa * r0 + ca * width,
            ca * r1 - sa * width, sa * r1 + ca * width,
            ca * r1 + sa * width, sa * r1 - ca * width,
            ca * r0 + sa * width, sa * r0 - ca * width, color);
      }

      // Counter-rotating hexagram and a small central six-point star.
      for (int triangle = 0; triangle < 2; triangle++) {
         double rotation = time * 0.5 * speed * (triangle == 0 ? 1.0 : -1.0) + triangle * Math.PI / 3.0;
         double radius = 1.22 * scale;
         for (int edge = 0; edge < 3; edge++) {
            double a0 = rotation + edge * Math.PI * 2.0 / 3.0;
            double a1 = rotation + (edge + 1) * Math.PI * 2.0 / 3.0;
            drawGroundSegment(out, m, x, z, ground + 0.024,
               Math.cos(a0) * radius, Math.sin(a0) * radius,
               Math.cos(a1) * radius, Math.sin(a1) * radius,
               0.027 * scale, rgba(aura.color(triangle * 0.5 + edge / 6.0, time), (float)(0.72 * pulse * alpha)));
         }
      }

      for (int i = 0; i < 6; i++) {
         double angle = i * Math.PI / 3.0 - time * 0.7 * speed;
         double r0 = 0.17 * scale, r1 = 0.42 * scale;
         drawGroundSegment(out, m, x, z, ground + 0.03,
            Math.cos(angle) * r0, Math.sin(angle) * r0,
            Math.cos(angle) * r1, Math.sin(angle) * r1,
            0.018 * scale, rgba(0xFFFFFF, (float)(0.50 * pulse * alpha)));
      }
      drawFlatRing(out, m, aura, x, z, ground + 0.034, 0.12 * scale, 0.025 * scale, time, (float)(0.88 * pulse * alpha));
   }

   private static double groundY(class_310 mc, double x, double y, double z) {
      class_2338 base = new class_2338((int)Math.floor(x), (int)Math.floor(y - 0.2), (int)Math.floor(z));
      for (int dy = 0; dy >= -4; dy--) {
         class_2338 pos = base.method_10086(dy);
         class_2680 state = mc.field_1687.method_8320(pos);
         var shape = state.method_26220(mc.field_1687, pos);
         if (shape.method_1110()) continue;
         return pos.method_10264() + shape.method_1107().field_1325;
      }
      return y;
   }

   private static void drawFlatRing(class_4588 out, Matrix4f m, PlayerAura aura,
                                    double x, double z, double y, double radius, double width,
                                    double time, float alpha) {
      double inner = radius - width * 0.5, outer = radius + width * 0.5;
      for (int i = 0; i < 64; i++) {
         double a0 = i * Math.PI * 2.0 / 64.0, a1 = (i + 1) * Math.PI * 2.0 / 64.0;
         int rgb = aura.color(i / 64.0, time);
         float[] c = rgba(rgb, alpha);
         quad(out, m,
            (float)(x + Math.cos(a0) * inner), (float)y, (float)(z + Math.sin(a0) * inner),
            (float)(x + Math.cos(a0) * outer), (float)y, (float)(z + Math.sin(a0) * outer),
            (float)(x + Math.cos(a1) * outer), (float)y, (float)(z + Math.sin(a1) * outer),
            (float)(x + Math.cos(a1) * inner), (float)y, (float)(z + Math.sin(a1) * inner), c);
      }
   }

   private static void drawGroundSegment(class_4588 out, Matrix4f m, double x, double z, double y,
                                         double x0, double z0, double x1, double z1, double width, float[] color) {
      double dx = x1 - x0, dz = z1 - z0;
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length < 1.0E-5) return;
      double nx = -dz / length * width, nz = dx / length * width;
      flatQuad(out, m, x, z, y,
         x0 - nx, z0 - nz, x1 - nx, z1 - nz,
         x1 + nx, z1 + nz, x0 + nx, z0 + nz, color);
   }

   private static void flatQuad(class_4588 out, Matrix4f m, double cx, double cz, double y,
                                double x0, double z0, double x1, double z1,
                                double x2, double z2, double x3, double z3, float[] color) {
      quad(out, m,
         (float)(cx + x0), (float)y, (float)(cz + z0),
         (float)(cx + x1), (float)y, (float)(cz + z1),
         (float)(cx + x2), (float)y, (float)(cz + z2),
         (float)(cx + x3), (float)y, (float)(cz + z3), color);
   }

   // Spherical UV mesh with a soft outer shell and a bright center.
   private static void drawOrb(class_4588 out, Matrix4f m, double x, double y, double z,
                               double radius, int rgb, float alpha) {
      if (alpha < 0.01F || radius < 0.002) return;
      for (int lat = 0; lat < ORB_LATITUDE; lat++) {
         double p0 = -Math.PI / 2.0 + Math.PI * lat / ORB_LATITUDE;
         double p1 = -Math.PI / 2.0 + Math.PI * (lat + 1) / ORB_LATITUDE;
         for (int lon = 0; lon < ORB_LONGITUDE; lon++) {
            double a0 = lon * Math.PI * 2.0 / ORB_LONGITUDE;
            double a1 = (lon + 1) * Math.PI * 2.0 / ORB_LONGITUDE;
            float[] edge = rgba(rgb, alpha * 0.68F);
            float[] highlight = rgba(0xFFFFFF, alpha * 0.92F);
            quad(out, m,
               (float)(x + Math.cos(p0) * Math.cos(a0) * radius), (float)(y + Math.sin(p0) * radius), (float)(z + Math.cos(p0) * Math.sin(a0) * radius),
               (float)(x + Math.cos(p0) * Math.cos(a1) * radius), (float)(y + Math.sin(p0) * radius), (float)(z + Math.cos(p0) * Math.sin(a1) * radius),
               (float)(x + Math.cos(p1) * Math.cos(a1) * radius), (float)(y + Math.sin(p1) * radius), (float)(z + Math.cos(p1) * Math.sin(a1) * radius),
               (float)(x + Math.cos(p1) * Math.cos(a0) * radius), (float)(y + Math.sin(p1) * radius), (float)(z + Math.cos(p1) * Math.sin(a0) * radius),
               edge, edge, highlight, edge);
         }
      }
   }

   private static void verticalStrip(class_4588 out, Matrix4f m,
                                     double x0, double y0, double z0, double x1, double y1, double z1,
                                     double width, float[] color) {
      quad(out, m,
         (float)(x0 - width), (float)y0, (float)z0,
         (float)(x0 + width), (float)y0, (float)z0,
         (float)(x1 + width), (float)y1, (float)z1,
         (float)(x1 - width), (float)y1, (float)z1, color);
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
      float x2, float y2, float z2, float x3, float y3, float z3, float[] color) {
      vertex(out, m, x0, y0, z0, color);
      vertex(out, m, x1, y1, z1, color);
      vertex(out, m, x2, y2, z2, color);
      vertex(out, m, x3, y3, z3, color);
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
      return new float[]{
         (rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F,
         (rgb & 255) / 255.0F, clamp(alpha, 0.0F, 1.0F)
      };
   }

   private static int brighten(int rgb, double amount) {
      int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
      return mix(r, 255, amount) << 16 | mix(g, 255, amount) << 8 | mix(b, 255, amount);
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * clamp(t, 0.0, 1.0));
   }

   private static double frac(double value) { return value - Math.floor(value); }
   private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
   private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}

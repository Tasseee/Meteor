package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SkyRibbons;
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
 * Draws SkyRibbons with Nightfall's filled-geometry render layer.
 * Ribbon meshes are camera-facing quads; reflected ribbons are projected onto
 * cached ground surfaces instead of submitting a second world-space mesh below
 * opaque terrain (which would be hidden by the depth buffer).
 */
public final class SkyRibbonsRenderer {
   private static final double[] LAYER_WIDTH = {3.2, 1.7, 0.55};
   private static final double[] LAYER_ALPHA = {0.10, 0.24, 0.96};

   private SkyRibbonsRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(SkyRibbonsRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      SkyRibbons module;
      try {
         module = ModuleManager.get(SkyRibbons.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;

      class_243 cameraPos = camera.method_71156();
      long nowNanos = System.nanoTime();
      module.prepareFrame(nowNanos, cameraPos.field_1352, cameraPos.field_1351, cameraPos.field_1350);
      double skyAlpha = module.skyAlpha();
      if (skyAlpha < 0.008) return;

      double time = nowNanos / 1_000_000_000.0;
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());

      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         if (module.groundEnabled() && module.hasGroundCache()) {
            renderGround(matrices, fills, module, cameraPos, time);
         }

         renderRibbons(matrices, fills, module, cameraPos, time);

         if (module.sparkleCount() > 0) {
            renderSparkles(matrices, fills, module, time);
         }
      } finally {
         matrices.method_22909();
      }
   }

   private static void renderGround(class_4587 matrices, class_4588 fills, SkyRibbons module, class_243 eye, double time) {
      int radius = module.groundRadius();
      double speed = module.flowSpeed();
      double darken = module.darken();
      double glow = module.groundGlow();
      double alphaBase = module.skyAlpha();

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            int bx = module.cacheCenterX() + dx;
            int bz = module.cacheCenterZ() + dz;
            double surfaceY = module.groundYAt(bx + 0.5, bz + 0.5);
            if (!Double.isFinite(surfaceY)) continue;

            double distance = Math.sqrt(
               square(bx + 0.5 - eye.field_1352) + square(bz + 0.5 - eye.field_1350)
            );
            double edge = 1.0 - smooth((distance / radius - 0.45) / 0.55);
            if (edge <= 0.01) continue;

            double y = surfaceY + 0.008;
            if (darken > 0.0) {
               float alpha = clamp((float)(darken * edge * alphaBase), 0.0F, 0.88F);
               drawQuad(matrices, fills, bx, y, bz, bx + 1.0, y, bz, bx + 1.0, y, bz + 1.0, bx, y, bz + 1.0,
                  0.025F, 0.018F, 0.008F, alpha);
            }

            if (glow > 0.0) {
               double shimmer = 0.5 + 0.5 * Math.sin(time * 0.9 * speed + bx * 0.45 + bz * 0.31
                  + Math.sin(bz * 0.2 + time * 0.4) * 1.5);
               double amount = glow * edge * alphaBase * (0.18 + 0.82 * shimmer * shimmer);
               if (amount > 0.008) {
                  int rgb = module.color(shimmer, time);
                  drawQuad(matrices, fills, bx, y + 0.004, bz, bx + 1.0, y + 0.004, bz,
                     bx + 1.0, y + 0.004, bz + 1.0, bx, y + 0.004, bz + 1.0,
                     red(rgb), green(rgb), blue(rgb), clamp((float)amount, 0.0F, 0.86F));
               }
            }
         }
      }

      if (module.reflect() && module.reflectStrength() > 0.0) {
         renderGroundReflections(matrices, fills, module, time);
      }
   }

   private static void renderRibbons(class_4587 matrices, class_4588 fills, SkyRibbons module, class_243 camera, double time) {
      int count = module.ribbonCount();
      int segments = module.segments();
      Vec[] scratch = new Vec[segments + 1];

      for (int ribbon = 0; ribbon < count; ribbon++) {
         buildRibbonPoints(module, ribbon, segments, time, scratch);
         Vec[] side = cameraSides(scratch, camera);

         for (int layer = 0; layer < LAYER_WIDTH.length; layer++) {
            drawSkyLayer(matrices, fills, module, scratch, side, layer, time);
         }
      }
   }

   private static void buildRibbonPoints(SkyRibbons module, int ribbon, int segments, double time, Vec[] out) {
      double phase = ribbon * (Math.PI * 2.0 / module.ribbonCount()) + Math.sin(ribbon * 12.9898) * 0.5;
      double span = 1.4 + 0.9 * frac(Math.sin(ribbon * 78.233) * 43758.5453);
      double elevationBase = 0.30 + 0.60 * frac(Math.sin(ribbon * 39.346) * 24634.6345);
      double radius = module.skyRadius();
      double speed = module.flowSpeed();

      for (int j = 0; j <= segments; j++) {
         double u = j / (double)segments;
         double angle = phase + u * span + 0.55 * Math.sin(time * 0.22 * speed + u * 3.0 + ribbon);
         double elevation = elevationBase
            + 0.30 * Math.sin(time * 0.28 * speed + u * 4.2 + ribbon * 1.7)
            + 0.08 * Math.sin(time * 0.55 * speed + u * 9.0 + ribbon * 0.6);
         elevation = clamp(elevation, 0.12, 1.25);
         double distance = radius * (0.82 + 0.18 * Math.sin(time * 0.17 * speed + u * 2.2 + ribbon * 0.9));
         double horizontal = Math.cos(elevation) * distance;

         out[j] = new Vec(
            module.centerX() + horizontal * Math.cos(angle),
            module.centerY() + distance * Math.sin(elevation),
            module.centerZ() + horizontal * Math.sin(angle)
         );
      }
   }

   private static Vec[] cameraSides(Vec[] points, class_243 camera) {
      Vec[] sides = new Vec[points.length];
      Vec cameraVec = new Vec(camera.field_1352, camera.field_1351, camera.field_1350);
      for (int j = 0; j < points.length; j++) {
         Vec tangent = points[Math.min(j + 1, points.length - 1)].subtract(points[Math.max(j - 1, 0)]);
         Vec toCamera = cameraVec.subtract(points[j]);
         Vec side = tangent.cross(toCamera).normalize();
         sides[j] = side.lengthSquared() < 1.0E-8 ? new Vec(0.0, 1.0, 0.0) : side;
      }
      return sides;
   }

   private static void drawSkyLayer(
      class_4587 matrices, class_4588 fills, SkyRibbons module, Vec[] points, Vec[] sides, int layer, double time
   ) {
      for (int j = 0; j < points.length - 1; j++) {
         double u0 = j / (double)(points.length - 1);
         double u1 = (j + 1) / (double)(points.length - 1);
         double uMid = (u0 + u1) * 0.5;
         double taper0 = Math.pow(Math.sin(Math.PI * u0), 0.8);
         double taper1 = Math.pow(Math.sin(Math.PI * u1), 0.8);
         double pulse = 0.85 + 0.15 * Math.sin(time * 0.9 + layer * 0.12 + j * 0.025);
         double w0 = module.skyWidth() * LAYER_WIDTH[layer] * pulse * taper0 * 0.5;
         double w1 = module.skyWidth() * LAYER_WIDTH[layer] * pulse * taper1 * 0.5;

         int rgb = module.color(uMid, time);
         if (layer == 2) rgb = mixRgb(rgb, 0xFFFFFF, 0.45);
         double opacity = LAYER_ALPHA[layer] * module.intensity() * module.skyAlpha()
            * Math.pow(Math.sin(Math.PI * uMid), 0.7);
         if (opacity < 0.004) continue;

         Vec p0 = points[j];
         Vec p1 = points[j + 1];
         Vec s0 = sides[j].multiply(w0);
         Vec s1 = sides[j + 1].multiply(w1);
         drawQuad(matrices, fills,
            p0.add(s0), p0.subtract(s0), p1.subtract(s1), p1.add(s1),
            red(rgb), green(rgb), blue(rgb), clamp((float)opacity, 0.0F, 1.0F));
      }
   }

   private static void renderSparkles(class_4587 matrices, class_4588 fills, SkyRibbons module, double time) {
      int count = module.sparkleCount();
      double radius = module.skyRadius();
      double speed = module.flowSpeed();

      for (int k = 0; k < count; k++) {
         double seed = k * 7.13;
         double angle = frac(Math.sin(seed) * 43758.5453) * Math.PI * 2.0 + time * 0.03 * speed;
         double radial = 6.0 + frac(Math.sin(seed * 1.7) * 12345.678) * radius * 0.75;
         double life = frac(time * 0.07 * speed + frac(Math.sin(seed * 3.1) * 999.1));

         double x = module.centerX() + Math.cos(angle) * radial;
         double z = module.centerZ() + Math.sin(angle) * radial;
         double y = module.centerY() - 3.0 + life * radius * 0.65;
         double twinkle = Math.sin(life * Math.PI);
         double alpha = twinkle * twinkle * module.skyAlpha() * module.intensity();
         if (alpha < 0.02) continue;

         int rgb = module.color(frac(Math.sin(seed * 5.3) * 321.9), time);
         double size = 0.09 + 0.06 * frac(Math.sin(seed * 2.9) * 777.7);
         drawCube(matrices, fills, x, y, z, size, red(rgb), green(rgb), blue(rgb), clamp((float)alpha, 0.0F, 0.95F));
      }
   }

   private static void renderGroundReflections(class_4587 matrices, class_4588 fills, SkyRibbons module, double time) {
      int count = module.ribbonCount();
      int segments = module.segments();
      double factor = Math.min(1.0, module.groundRadius() * 0.92 / module.skyRadius());
      double alphaBase = module.reflectStrength() * module.skyAlpha();
      Vec[] skyPoints = new Vec[segments + 1];

      for (int ribbon = 0; ribbon < count; ribbon++) {
         buildRibbonPoints(module, ribbon, segments, time, skyPoints);
         Vec[] groundPoints = new Vec[segments + 1];
         for (int j = 0; j <= segments; j++) {
            Vec point = skyPoints[j];
            double x = module.cacheCenterX() + 0.5 + (point.x() - module.centerX()) * factor;
            double z = module.cacheCenterZ() + 0.5 + (point.z() - module.centerZ()) * factor;
            double y = module.groundYAt(x, z);
            if (!Double.isFinite(y)) y = module.groundYS();
            groundPoints[j] = new Vec(x, y + 0.018, z);
         }

         for (int j = 0; j < segments; j++) {
            double u = (j + 0.5) / segments;
            double taper = Math.pow(Math.sin(Math.PI * u), 0.8);
            double alpha = alphaBase * module.intensity() * 0.24 * taper;
            if (alpha < 0.005) continue;
            int rgb = module.color(u, time);
            Vec p0 = groundPoints[j];
            Vec p1 = groundPoints[j + 1];
            Vec tangent = p1.subtract(p0);
            double length = Math.sqrt(tangent.x() * tangent.x() + tangent.z() * tangent.z());
            if (length < 1.0E-6) continue;
            Vec side = new Vec(-tangent.z() / length, 0.0, tangent.x() / length);
            double width = module.skyWidth() * 0.32;
            Vec s0 = side.multiply(width);
            Vec s1 = side.multiply(width);
            double y0a = module.groundYAt(p0.x() + s0.x(), p0.z() + s0.z());
            double y0b = module.groundYAt(p0.x() - s0.x(), p0.z() - s0.z());
            double y1a = module.groundYAt(p1.x() + s1.x(), p1.z() + s1.z());
            double y1b = module.groundYAt(p1.x() - s1.x(), p1.z() - s1.z());
            if (!Double.isFinite(y0a)) y0a = p0.y() - 0.018;
            if (!Double.isFinite(y0b)) y0b = p0.y() - 0.018;
            if (!Double.isFinite(y1a)) y1a = p1.y() - 0.018;
            if (!Double.isFinite(y1b)) y1b = p1.y() - 0.018;

            drawQuad(matrices, fills,
               new Vec(p0.x() + s0.x(), y0a + 0.022, p0.z() + s0.z()),
               new Vec(p0.x() - s0.x(), y0b + 0.022, p0.z() - s0.z()),
               new Vec(p1.x() - s1.x(), y1b + 0.022, p1.z() - s1.z()),
               new Vec(p1.x() + s1.x(), y1a + 0.022, p1.z() + s1.z()),
               red(rgb), green(rgb), blue(rgb), clamp((float)alpha, 0.0F, 0.85F));
         }
      }

      int sparkles = Math.min(28, module.sparkleCount());
      for (int k = 0; k < sparkles; k++) {
         double seed = k * 7.13;
         double angle = frac(Math.sin(seed) * 43758.5453) * Math.PI * 2.0 + time * 0.03 * module.flowSpeed();
         double radial = 6.0 + frac(Math.sin(seed * 1.7) * 12345.678) * module.skyRadius() * 0.75;
         double life = frac(time * 0.07 * module.flowSpeed() + frac(Math.sin(seed * 3.1) * 999.1));
         double x = module.centerX() + Math.cos(angle) * radial;
         double z = module.centerZ() + Math.sin(angle) * radial;
         double gx = module.cacheCenterX() + 0.5 + (x - module.centerX()) * factor;
         double gz = module.cacheCenterZ() + 0.5 + (z - module.centerZ()) * factor;
         double groundY = module.groundYAt(gx, gz);
         if (!Double.isFinite(groundY)) continue;
         double twinkle = Math.sin(life * Math.PI);
         double alpha = twinkle * twinkle * alphaBase * 0.34;
         if (alpha < 0.02) continue;
         int rgb = module.color(frac(Math.sin(seed * 5.3) * 321.9), time);
         double size = 0.045 + 0.035 * frac(Math.sin(seed * 2.9) * 777.7);
         drawCube(matrices, fills, gx, groundY + 0.035, gz, size,
            red(rgb), green(rgb), blue(rgb), clamp((float)alpha, 0.0F, 0.75F));
      }
   }

   private static void drawCube(
      class_4587 matrices, class_4588 consumer, double x, double y, double z, double halfSize,
      float red, float green, float blue, float alpha
   ) {
      float x0 = (float)(x - halfSize), x1 = (float)(x + halfSize);
      float y0 = (float)(y - halfSize), y1 = (float)(y + halfSize);
      float z0 = (float)(z - halfSize), z1 = (float)(z + halfSize);
      drawQuad(matrices, consumer, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, red,green,blue,alpha);
      drawQuad(matrices, consumer, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1, red,green,blue,alpha);
      drawQuad(matrices, consumer, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1, red,green,blue,alpha);
      drawQuad(matrices, consumer, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0, red,green,blue,alpha);
      drawQuad(matrices, consumer, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, red,green,blue,alpha);
      drawQuad(matrices, consumer, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1, red,green,blue,alpha);
   }

   private static void drawQuad(
      class_4587 matrices, class_4588 consumer,
      Vec a, Vec b, Vec c, Vec d,
      float red, float green, float blue, float alpha
   ) {
      drawQuad(matrices, consumer, (float)a.x(), (float)a.y(), (float)a.z(),
         (float)b.x(), (float)b.y(), (float)b.z(),
         (float)c.x(), (float)c.y(), (float)c.z(),
         (float)d.x(), (float)d.y(), (float)d.z(), red, green, blue, alpha);
   }

   private static void drawQuad(
      class_4587 matrices, class_4588 consumer,
      double ax, double ay, double az, double bx, double by, double bz,
      double cx, double cy, double cz, double dx, double dy, double dz,
      float red, float green, float blue, float alpha
   ) {
      if (alpha <= 0.004F) return;
      Matrix4f matrix = matrices.method_23760().method_23761();
      consumer.method_22918(matrix, (float)ax, (float)ay, (float)az).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)bx, (float)by, (float)bz).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)cx, (float)cy, (float)cz).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)dx, (float)dy, (float)dz).method_22915(red, green, blue, alpha);
   }

   private static int mixRgb(int first, int second, double amount) {
      int red = Math.round((first >> 16 & 255) + ((second >> 16 & 255) - (first >> 16 & 255)) * (float)amount);
      int green = Math.round((first >> 8 & 255) + ((second >> 8 & 255) - (first >> 8 & 255)) * (float)amount);
      int blue = Math.round((first & 255) + ((second & 255) - (first & 255)) * (float)amount);
      return red << 16 | green << 8 | blue;
   }

   private static float red(int rgb) { return (rgb >> 16 & 255) / 255.0F; }
   private static float green(int rgb) { return (rgb >> 8 & 255) / 255.0F; }
   private static float blue(int rgb) { return (rgb & 255) / 255.0F; }
   private static double square(double value) { return value * value; }

   private static double smooth(double value) {
      double x = clamp(value, 0.0, 1.0);
      return x * x * (3.0 - 2.0 * x);
   }

   private static double frac(double value) {
      return value - Math.floor(value);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private record Vec(double x, double y, double z) {
      Vec add(Vec other) { return new Vec(this.x + other.x, this.y + other.y, this.z + other.z); }
      Vec subtract(Vec other) { return new Vec(this.x - other.x, this.y - other.y, this.z - other.z); }
      Vec multiply(double scale) { return new Vec(this.x * scale, this.y * scale, this.z * scale); }
      Vec cross(Vec other) {
         return new Vec(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
         );
      }
      double lengthSquared() { return this.x * this.x + this.y * this.y + this.z * this.z; }
      Vec normalize() {
         double length = Math.sqrt(this.lengthSquared());
         return length < 1.0E-8 ? new Vec(0.0, 0.0, 0.0) : this.multiply(1.0 / length);
      }
   }
}

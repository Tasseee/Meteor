package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SkyVisuals;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Native Nightfall renderer for the five supplied OptiFine-style sky cubemaps.
 * Each texture is a 3x2 atlas containing the six cube faces.
 */
public final class SkyVisualRenderer {
   private static final int FULL_BRIGHT = 15728880;
   private static final double UV_INSET = 0.0015;
   private static final double[][][] FACES = {
      // -X, +X, -Y, +Y, -Z, +Z
      {{-1, -1,  1}, {-1, -1, -1}, {-1,  1, -1}, {-1,  1,  1}},
      {{ 1, -1, -1}, { 1, -1,  1}, { 1,  1,  1}, { 1,  1, -1}},
      {{-1, -1,  1}, { 1, -1,  1}, { 1, -1, -1}, {-1, -1, -1}},
      {{-1,  1, -1}, { 1,  1, -1}, { 1,  1,  1}, {-1,  1,  1}},
      {{ 1, -1, -1}, {-1, -1, -1}, {-1,  1, -1}, { 1,  1, -1}},
      {{-1, -1,  1}, { 1, -1,  1}, { 1,  1,  1}, {-1,  1,  1}}
   };

   private SkyVisualRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(SkyVisualRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) {
         return;
      }

      SkyVisuals sky;
      try {
         sky = ModuleManager.get(SkyVisuals.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!sky.isEnabled()) {
         return;
      }

      class_4184 cameraState = mc.field_1773.method_19418();
      if (cameraState == null) {
         return;
      }

      class_243 camera = cameraState.method_71156();
      String textureName = sky.style().toLowerCase(Locale.ROOT);
      class_2960 texture = class_2960.method_60655("nightfall", "textures/sky/" + textureName + ".png");
      class_4588 vertices = context.consumers().method_73477(class_12249.method_76000(texture));
      class_4587 matrices = context.matrices();
      float alpha = clamp(sky.intensity(), 0.0F, 1.0F);
      if (alpha <= 0.001F) {
         return;
      }

      double radius = sky.distance();
      double rotation = 0.0;
      if (sky.style().equals("Nebula") || sky.style().equals("Planet")) {
         rotation = System.nanoTime() * 0.00000002 * sky.rotation();
      }
      double cos = Math.cos(rotation);
      double sin = Math.sin(rotation);

      matrices.method_22903();
      matrices.method_22904(-camera.field_1352, -camera.field_1351, -camera.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         for (double[][] face : FACES) {
            for (double[] corner : face) {
               double x = corner[0];
               double y = corner[1];
               double z = corner[2];

               // Slow axial rotation for the two packs which specify animation.
               double rx = x * cos - y * sin;
               double ry = x * sin + y * cos;
               double rz = z;

               float[] uv = atlasUv(rx, ry, rz);
               float nx = (float)-rx * 0.57735026F;
               float ny = (float)-ry * 0.57735026F;
               float nz = (float)-rz * 0.57735026F;

               vertices.method_22918(
                     matrix,
                     (float)(camera.field_1352 + rx * radius),
                     (float)(camera.field_1351 + ry * radius),
                     (float)(camera.field_1350 + rz * radius)
                  )
                  .method_22915(1.0F, 1.0F, 1.0F, alpha)
                  .method_22913(uv[0], uv[1])
                  .method_22922(0)
                  .method_60803(FULL_BRIGHT)
                  .method_22914(nx, ny, nz);
            }
         }
      } finally {
         matrices.method_22909();
      }
   }

   /**
    * Converts a cube direction to the conventional 3x2 OptiFine sky atlas:
    * top row = +Y, -Z, +X; bottom row = -Y, -X, +Z.
    */
   private static float[] atlasUv(double x, double y, double z) {
      double ax = Math.abs(x);
      double ay = Math.abs(y);
      double az = Math.abs(z);
      int column;
      int row;
      double u;
      double v;

      if (ax >= ay && ax >= az) {
         if (x < 0.0) {
            column = 0;
            row = 1;
            u = (1.0 - z / ax) * 0.5;
            v = (1.0 - y / ax) * 0.5;
         } else {
            column = 2;
            row = 1;
            u = (1.0 + z / ax) * 0.5;
            v = (1.0 - y / ax) * 0.5;
         }
      } else if (ay >= az) {
         if (y > 0.0) {
            column = 1;
            row = 0;
            u = (1.0 + x / ay) * 0.5;
            v = (1.0 - z / ay) * 0.5;
         } else {
            column = 0;
            row = 0;
            u = (1.0 + x / ay) * 0.5;
            v = (1.0 + z / ay) * 0.5;
         }
      } else if (z < 0.0) {
         column = 1;
         row = 1;
         u = (1.0 + x / az) * 0.5;
         v = (1.0 - y / az) * 0.5;
      } else {
         column = 2;
         row = 0;
         u = (1.0 - x / az) * 0.5;
         v = (1.0 - y / az) * 0.5;
      }

      u = Math.max(UV_INSET, Math.min(1.0 - UV_INSET, u));
      v = Math.max(UV_INSET, Math.min(1.0 - UV_INSET, v));
      return new float[] {(float)((column + u) / 3.0), (float)((row + v) / 2.0)};
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}

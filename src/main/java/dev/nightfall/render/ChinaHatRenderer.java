package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ChinaHat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/** Renders a simple shaded conical hat and its circular brim above the local player. */
public final class ChinaHatRenderer {
   private static final int SEGMENTS = 48;
   private ChinaHatRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(ChinaHatRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;
      if (mc.field_1690.method_31044().method_31034()) return;

      ChinaHat hat;
      try { hat = ModuleManager.get(ChinaHat.class); }
      catch (Throwable ignored) { return; }
      if (!hat.isEnabled()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      double px = mc.field_1724.method_23317();
      double py = mc.field_1724.method_23318() + mc.field_1724.method_17682() + 0.035;
      double pz = mc.field_1724.method_23321();
      double radius = hat.radius();
      double brim = radius * hat.brimWidth();
      double height = hat.height();
      double phase = System.nanoTime() * 1.0E-9 * hat.rotationSpeed() * Math.PI * 2.0;
      int rgb = hat.color();
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         float red = (rgb >> 16 & 255) / 255.0F;
         float green = (rgb >> 8 & 255) / 255.0F;
         float blue = (rgb & 255) / 255.0F;
         float dark = 0.72F;
         for (int i = 0; i < SEGMENTS; i++) {
            double a0 = phase + i * Math.PI * 2.0 / SEGMENTS;
            double a1 = phase + (i + 1) * Math.PI * 2.0 / SEGMENTS;
            float x0 = (float)(px + Math.cos(a0) * radius);
            float z0 = (float)(pz + Math.sin(a0) * radius);
            float x1 = (float)(px + Math.cos(a1) * radius);
            float z1 = (float)(pz + Math.sin(a1) * radius);

            // Wind the cone faces upward and outward so they remain visible from above.
            vertex(fills, matrix, x1, (float)py, z1, red * dark, green * dark, blue * dark, 0.96F);
            vertex(fills, matrix, x0, (float)py, z0, red * dark, green * dark, blue * dark, 0.96F);
            vertex(fills, matrix, (float)px, (float)(py + height), (float)pz, red, green, blue, 0.96F);

            float bx0 = (float)(px + Math.cos(a0) * brim);
            float bz0 = (float)(pz + Math.sin(a0) * brim);
            float bx1 = (float)(px + Math.cos(a1) * brim);
            float bz1 = (float)(pz + Math.sin(a1) * brim);

            // Reverse the annulus winding as well: its top face points toward the sky.
            vertex(fills, matrix, bx0, (float)py, bz0, red, green, blue, 0.78F);
            vertex(fills, matrix, x0, (float)py, z0, red, green, blue, 0.78F);
            vertex(fills, matrix, x1, (float)py, z1, red, green, blue, 0.78F);
            vertex(fills, matrix, bx1, (float)py, bz1, red, green, blue, 0.78F);
         }
      } finally {
         matrices.method_22909();
      }
   }

   private static void vertex(class_4588 consumer, Matrix4f matrix, float x, float y, float z,
                              float red, float green, float blue, float alpha) {
      consumer.method_22918(matrix, x, y, z).method_22915(red, green, blue, alpha);
   }
}

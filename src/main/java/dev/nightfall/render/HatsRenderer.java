package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Hats;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

public final class HatsRenderer {
   private static final int STRANDS = 3;
   private static final int BEADS_PER_STRAND = 24;

   private HatsRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(HatsRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 != null && mc.field_1724 != null && context.matrices() != null && context.consumers() != null) {
         if (!mc.field_1690.method_31044().method_31034()) {
            Hats hats;
            try {
               hats = ModuleManager.get(Hats.class);
            } catch (Throwable var40) {
               return;
            }

            if (hats.isEnabled()) {
               class_4184 camera = mc.field_1773.method_19418();
               class_243 cameraPos = camera.method_71156();
               class_243 playerPos = new class_243(mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321());
               double scale = hats.size();
               double baseY = mc.field_1724.method_23318() + mc.field_1724.method_17682() + hats.height();
               double phase = System.nanoTime() * 1.0E-9 * hats.rotationSpeed() * Math.PI * 2.0;
               class_4587 matrices = context.matrices();
               class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
               int rgb = hats.color();
               float red = (rgb >> 16 & 0xFF) / 255.0F;
               float green = (rgb >> 8 & 0xFF) / 255.0F;
               float blue = (rgb & 0xFF) / 255.0F;
               matrices.method_22903();
               matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);

               try {
                  for (int strand = 0; strand < 3; strand++) {
                     double offset = strand * (Math.PI * 2.0 / 3.0);

                     for (int i = 0; i <= 24; i++) {
                        double progress = i / 24.0;
                        double angle = phase + offset + progress * Math.PI * 2.4;
                        double radius = scale * (0.38 * (1.0 - progress) + 0.065);
                        double x = playerPos.field_1352 + Math.cos(angle) * radius;
                        double y = baseY + progress * 0.78 * scale;
                        double z = playerPos.field_1350 + Math.sin(angle) * radius;
                        double bead = scale * (0.028 + 0.012 * (1.0 - progress));
                        float brightness = (float)(0.66 + 0.34 * Math.sin(Math.PI * (0.15 + progress * 0.7)));
                        drawCube(
                           matrices,
                           fills,
                           x,
                           y,
                           z,
                           bead,
                           Math.min(1.0F, red * brightness),
                           Math.min(1.0F, green * brightness),
                           Math.min(1.0F, blue * brightness),
                           0.96F
                        );
                     }
                  }
               } finally {
                  matrices.method_22909();
               }
            }
         }
      }
   }

   private static void drawCube(
      class_4587 matrices, class_4588 consumer, double x, double y, double z, double halfSize, float red, float green, float blue, float alpha
   ) {
      Matrix4f matrix = matrices.method_23760().method_23761();
      float x0 = (float)(x - halfSize);
      float x1 = (float)(x + halfSize);
      float y0 = (float)(y - halfSize);
      float y1 = (float)(y + halfSize);
      float z0 = (float)(z - halfSize);
      float z1 = (float)(z + halfSize);
      quad(consumer, matrix, red, green, blue, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
      quad(consumer, matrix, red, green, blue, alpha, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
      quad(consumer, matrix, red, green, blue, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
      quad(consumer, matrix, red, green, blue, alpha, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
      quad(consumer, matrix, red, green, blue, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
      quad(consumer, matrix, red, green, blue, alpha, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
   }

   private static void quad(
      class_4588 consumer,
      Matrix4f matrix,
      float red,
      float green,
      float blue,
      float alpha,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3
   ) {
      consumer.method_22918(matrix, x0, y0, z0).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x1, y1, z1).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x2, y2, z2).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x3, y3, z3).method_22915(red, green, blue, alpha);
   }
}

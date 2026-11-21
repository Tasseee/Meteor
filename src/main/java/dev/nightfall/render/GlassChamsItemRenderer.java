package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.GlassChams;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_238;
import net.minecraft.class_310;
import net.minecraft.class_4588;
import net.minecraft.class_4587;
import net.minecraft.class_4184;
import org.joml.Matrix4f;

/** Draws translucent glass shells around dropped-item entities. */
public final class GlassChamsItemRenderer {
   private GlassChamsItemRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(GlassChamsItemRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      GlassChams module;
      try {
         module = ModuleManager.get(GlassChams.class);
      } catch (Throwable ignored) {
         return;
      }

      class_310 mc = class_310.method_1551();
      if (!module.isEnabled() || !module.items() || mc.field_1687 == null || mc.field_1724 == null
         || context.matrices() == null || context.consumers() == null) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;

      var cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 consumer = context.consumers().method_73477(class_12249.method_76019());
      float tickDelta = 1.0F;
      double rangeSquared = module.rangeSquared();

      matrices.method_22903();
      try {
         matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);

         for (class_1297 entity : mc.field_1687.method_18112()) {
            if (!(entity instanceof class_1542 item)) continue;
            if (item.method_5858(mc.field_1724) > rangeSquared) continue;

            double x = entity.field_6038 + (entity.method_23317() - entity.field_6038) * tickDelta;
            double y = entity.field_5971 + (entity.method_23318() - entity.field_5971) * tickDelta;
            double z = entity.field_5989 + (entity.method_23321() - entity.field_5989) * tickDelta;
            class_238 current = entity.method_5829();
            double dx = x - entity.method_23317();
            double dy = y - entity.method_23318();
            double dz = z - entity.method_23321();

            double minX = current.field_1323 + dx;
            double minY = current.field_1322 + dy;
            double minZ = current.field_1321 + dz;
            double maxX = current.field_1320 + dx;
            double maxY = current.field_1325 + dy;
            double maxZ = current.field_1324 + dz;

            double time = System.nanoTime() / 1_000_000_000.0;
            float pulse = module.shimmer() ? 0.82F + 0.18F * (float)Math.sin(time * 2.4) : 1.0F;
            int rgb = module.color(0.0F);
            int layers = module.glowLayers();

            for (int layer = layers; layer >= 1; layer--) {
               double spread = module.glowSpread() * layer;
               float fade = 1.0F - 0.62F * (layer - 1) / Math.max(1, layers);
               float alpha = module.glowAlpha() * pulse * fade;
               drawBox(matrices, consumer,
                  minX - spread, minY - spread, minZ - spread,
                  maxX + spread, maxY + spread, maxZ + spread,
                  rgb, alpha);
            }

            drawBox(matrices, consumer, minX, minY, minZ, maxX, maxY, maxZ,
               rgb, module.coverSkin() ? 1.0F : module.fillAlpha() * pulse);
         }
      } finally {
         matrices.method_22909();
      }
   }

   private static void drawBox(
      class_4587 matrices,
      class_4588 consumer,
      double minX, double minY, double minZ,
      double maxX, double maxY, double maxZ,
      int rgb,
      float alpha
   ) {
      Matrix4f matrix = matrices.method_23760().method_23761();
      float red = (rgb >> 16 & 0xFF) / 255.0F;
      float green = (rgb >> 8 & 0xFF) / 255.0F;
      float blue = (rgb & 0xFF) / 255.0F;
      float a = Math.max(0.0F, Math.min(1.0F, alpha));

      float x0 = (float)minX, x1 = (float)maxX;
      float y0 = (float)minY, y1 = (float)maxY;
      float z0 = (float)minZ, z1 = (float)maxZ;

      quad(consumer, matrix, red, green, blue, a, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
      quad(consumer, matrix, red, green, blue, a, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
      quad(consumer, matrix, red, green, blue, a, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
      quad(consumer, matrix, red, green, blue, a, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
      quad(consumer, matrix, red, green, blue, a, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
      quad(consumer, matrix, red, green, blue, a, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
   }

   private static void quad(
      class_4588 consumer, Matrix4f matrix,
      float red, float green, float blue, float alpha,
      float x0, float y0, float z0,
      float x1, float y1, float z1,
      float x2, float y2, float z2,
      float x3, float y3, float z3
   ) {
      consumer.method_22918(matrix, x0, y0, z0).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x1, y1, z1).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x2, y2, z2).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x3, y3, z3).method_22915(red, green, blue, alpha);
   }
}

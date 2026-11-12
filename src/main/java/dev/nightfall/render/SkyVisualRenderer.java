package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SkyVisuals;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SkyVisualRenderer {
   private SkyVisualRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(SkyVisualRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 != null && mc.field_1724 != null && context.matrices() != null && context.consumers() != null) {
         SkyVisuals sky;
         try {
            sky = ModuleManager.get(SkyVisuals.class);
         } catch (Throwable var24) {
            return;
         }

         if (sky.isEnabled()) {
            class_4587 matrices = context.matrices();
            class_4184 renderCamera = mc.field_1773.method_19418();
            class_243 camera = renderCamera.method_71156();
            Quaternionf cameraRotation = renderCamera.method_23767();
            Vector3f forwardVector = new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraRotation);
            Vector3f rightVector = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
            Vector3f upVector = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraRotation);
            class_243 forward = new class_243(forwardVector.x, forwardVector.y, forwardVector.z).method_1029();
            class_243 right = new class_243(rightVector.x, rightVector.y, rightVector.z).method_1029();
            class_243 up = new class_243(upVector.x, upVector.y, upVector.z).method_1029();
            class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
            double distance = sky.distance();
            float intensity = sky.intensity();
            int color = sky.color();
            matrices.method_22903();
            matrices.method_22904(-camera.field_1352, -camera.field_1351, -camera.field_1350);

            try {
               drawStars(matrices, fills, camera, distance, intensity);
               String var18 = sky.style();
               switch (var18) {
                  case "Nordlicht":
                     drawAurora(matrices, fills, camera, right, up, forward, distance, color, intensity);
                     break;
                  case "Planet":
                     drawPlanet(matrices, fills, camera, right, up, forward, distance, color, intensity, sky.planetScale());
                     break;
                  default:
                     drawNebula(matrices, fills, camera, right, up, forward, distance, color, intensity);
               }
            } finally {
               matrices.method_22909();
            }
         }
      }
   }

   private static class_243 point(class_243 origin, class_243 right, class_243 up, class_243 forward, double x, double y, double z) {
      return origin.method_1019(right.method_1021(x)).method_1019(up.method_1021(y)).method_1019(forward.method_1021(z));
   }

   private static void drawFilledBox(class_4587 matrices, class_4588 consumer, class_238 box, float red, float green, float blue, float alpha) {
      Matrix4f matrix = matrices.method_23760().method_23761();
      float x0 = (float)box.field_1323;
      float y0 = (float)box.field_1322;
      float z0 = (float)box.field_1321;
      float x1 = (float)box.field_1320;
      float y1 = (float)box.field_1325;
      float z1 = (float)box.field_1324;
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

   private static void quad(
      class_4588 consumer, Matrix4f matrix, class_243 a, class_243 b, class_243 c, class_243 d, float red, float green, float blue, float alpha
   ) {
      consumer.method_22918(matrix, (float)a.field_1352, (float)a.field_1351, (float)a.field_1350).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)b.field_1352, (float)b.field_1351, (float)b.field_1350).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)c.field_1352, (float)c.field_1351, (float)c.field_1350).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, (float)d.field_1352, (float)d.field_1351, (float)d.field_1350).method_22915(red, green, blue, alpha);
   }

   private static void drawStars(class_4587 m, class_4588 fills, class_243 c, double d, float power) {
      for (int i = 0; i < 120; i++) {
         double a = i * 2.399963229728653;
         double y = Math.sin(i * 91.17) * 0.76;
         double r = Math.sqrt(1.0 - y * y);
         double x = Math.cos(a) * r;
         double z = Math.sin(a) * r;
         double radius = d * (0.78 + i % 7 * 0.035);
         double px = c.field_1352 + x * radius;
         double py = c.field_1351 + y * radius;
         double pz = c.field_1350 + z * radius;
         double size = (i % 11 == 0 ? 0.12 : 0.045) * power;
         int col = i % 5 == 0 ? -4661249 : -1;
         drawFilledBox(
            m,
            fills,
            new class_238(px - size, py - size, pz - size, px + size, py + size, pz + size),
            (col >> 16 & 0xFF) / 255.0F,
            (col >> 8 & 0xFF) / 255.0F,
            (col & 0xFF) / 255.0F,
            0.95F
         );
      }
   }

   private static void drawAurora(class_4587 m, class_4588 fills, class_243 c, class_243 right, class_243 up, class_243 forward, double d, int rgb, float power) {
      int g = rgb >> 8 & 0xFF;
      int r = rgb >> 16 & 0xFF;
      int b = rgb & 0xFF;

      for (int band = 0; band < 4; band++) {
         double phase = System.currentTimeMillis() * 2.5E-4 + band * 1.4;

         for (int i = 0; i < 42; i++) {
            double t = i / 41.0;
            double x = (t - 0.5) * d * 1.5;
            double z = d * (0.42 + band * 0.035) + Math.sin(t * 8.0 + phase) * 5.0;
            double y = d * 0.3 + Math.sin(t * 11.0 + phase) * 5.0 + band * 2.6;
            class_243 p = point(c, right, up, forward, x, y, z);
            double w = 0.08 * power;
            class_238 segment = new class_238(
               p.field_1352 - w, p.field_1351 - w, p.field_1350 - w, p.field_1352 + w, p.field_1351 + 1.2 * power, p.field_1350 + w
            );
            float mix = 0.55F + 0.35F * (float)Math.sin(t * Math.PI);
            drawFilledBox(m, fills, segment, r / 255.0F * mix, g / 255.0F * mix, b / 255.0F * mix, 0.62F);
         }
      }
   }

   private static class_243 spherePoint(class_243 center, class_243 right, class_243 up, class_243 forward, double radius, double latitude, double longitude) {
      double ring = radius * Math.cos(latitude);
      return point(center, right, up, forward, ring * Math.cos(longitude), radius * Math.sin(latitude), ring * Math.sin(longitude));
   }

   private static void drawPlanet(
      class_4587 m, class_4588 fills, class_243 c, class_243 right, class_243 up, class_243 forward, double d, int rgb, float power, float scale
   ) {
      class_243 center = point(c, right, up, forward, d * 0.24, d * 0.1, d * 0.72);
      double rad = d * 0.13 * scale;
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      Matrix4f matrix = m.method_23760().method_23761();
      int latitudeSteps = 18;
      int longitudeSteps = 72;

      for (int lat = 0; lat < latitudeSteps; lat++) {
         double phi0 = (-Math.PI / 2) + lat * Math.PI / latitudeSteps;
         double phi1 = (-Math.PI / 2) + (lat + 1) * Math.PI / latitudeSteps;

         for (int lon = 0; lon < longitudeSteps; lon++) {
            double theta0 = lon * Math.PI * 2.0 / longitudeSteps;
            double theta1 = (lon + 1) * Math.PI * 2.0 / longitudeSteps;
            class_243 p00 = spherePoint(center, right, up, forward, rad, phi0, theta0);
            class_243 p01 = spherePoint(center, right, up, forward, rad, phi0, theta1);
            class_243 p11 = spherePoint(center, right, up, forward, rad, phi1, theta1);
            class_243 p10 = spherePoint(center, right, up, forward, rad, phi1, theta0);
            float shade = 0.58F + 0.42F * Math.max(0.0F, (float)(Math.cos(theta0) * Math.cos((phi0 + phi1) * 0.5)));
            quad(
               fills,
               matrix,
               p00,
               p10,
               p11,
               p01,
               Math.min(1.0F, r / 255.0F * shade),
               Math.min(1.0F, g / 255.0F * shade),
               Math.min(1.0F, b / 255.0F * shade),
               0.98F * power
            );
         }
      }

      for (int i = 0; i < 72; i++) {
         double a = i * Math.PI * 2.0 / 72.0;
         class_243 p = point(center, right, up, forward, Math.cos(a) * rad * 1.65, Math.sin(a) * rad * 0.35, 0.0);
         double s = 0.1 * scale;
         drawFilledBox(
            m,
            fills,
            new class_238(p.field_1352 - s, p.field_1351 - s, p.field_1350 - s, p.field_1352 + s, p.field_1351 + s, p.field_1350 + s),
            0.45F,
            0.78F,
            1.0F,
            0.82F
         );
      }
   }

   private static void drawNebula(class_4587 m, class_4588 fills, class_243 c, class_243 right, class_243 up, class_243 forward, double d, int rgb, float power) {
      for (int cloud = 0; cloud < 5; cloud++) {
         double cx = Math.sin(cloud * 2.1) * d * 0.55;
         double cy = Math.cos(cloud * 1.7) * d * 0.32;
         double cz = d * 0.82;

         for (int i = 0; i < 18; i++) {
            double a = i * 2.399963;
            double rr = (4 + i % 6) * power;
            class_243 p = point(c, right, up, forward, cx + Math.cos(a) * rr, cy + Math.sin(a * 1.4) * rr, cz + Math.sin(a) * rr);
            double s = 0.45 * power;
            float red = Math.min(1.0F, (rgb >> 16 & 0xFF) / 255.0F + 0.15F);
            float green = (rgb >> 8 & 0xFF) / 255.0F;
            float blue = Math.min(1.0F, (rgb & 0xFF) / 255.0F + 0.2F);
            drawFilledBox(
               m,
               fills,
               new class_238(p.field_1352 - s, p.field_1351 - s, p.field_1350 - s, p.field_1352 + s, p.field_1351 + s, p.field_1350 + s),
               red,
               green,
               blue,
               0.42F
            );
         }
      }
   }
}

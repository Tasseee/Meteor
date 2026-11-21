package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.JumpWave;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4184;
import org.joml.Matrix4f;

/**
 * Renders cached JumpWave ground columns using filled geometry only.
 * No legacy line vertex pipeline is used.
 */
public final class JumpWaveRenderer {
   private JumpWaveRenderer() {}

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(JumpWaveRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      JumpWave module;
      try {
         module = ModuleManager.get(JumpWave.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled() || module.waves().isEmpty()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      var cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
      long now = System.nanoTime();

      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         for (JumpWave.Wave wave : module.waves()) {
            double progress = (now - wave.startNanos()) / (module.durationMs() * 1_000_000.0);
            if (progress < 0.0 || progress >= 1.0) continue;

            double eased = 1.0 - Math.pow(1.0 - progress, 3.0);
            double power = wave.power();
            double radius = module.radius() * eased * (wave.impact() ? 0.55 + power * 0.58 : 0.62);
            double halfWidth = module.ringWidth() * 0.5;
            double fade = Math.min(1.0, progress * 8.0) * Math.pow(1.0 - progress, 1.25);
            double pulse = module.pulse()
               ? 1.0 + Math.sin(now / 1_000_000_000.0 * 3.2 + progress * 5.0) * module.pulseStrength()
               : 1.0;
            double liftScale = wave.impact() ? 0.35 + power * 1.25 : 0.55;

            int cellIndex = 0;
            for (JumpWave.Cell cell : wave.cells()) {
               double dx = cell.x() + 0.5 - wave.centerX();
               double dz = cell.z() + 0.5 - wave.centerZ();
               double distance = Math.sqrt(dx * dx + dz * dz);
               double rel = (distance - radius) / halfWidth;
               double absRel = Math.abs(rel);
               if (absRel >= 1.0) {
                  cellIndex++;
                  continue;
               }

               double bell = Math.cos(rel * Math.PI * 0.5);
               double strength = bell * bell * fade;
               if (strength < 0.025) {
                  cellIndex++;
                  continue;
               }

               double wavePulse = pulse * (0.94 + 0.06 * Math.sin(now / 450_000_000.0 + distance));
               double blockLift = module.lift() * liftScale * strength * wavePulse;
               double surfaceY = cell.topY() + blockLift;
               double colorPos = Math.max(0.0, Math.min(1.0, (rel + 1.0) * 0.5));
               int rgb = module.color(colorPos, now);
               float red = (rgb >> 16 & 255) / 255.0F;
               float green = (rgb >> 8 & 255) / 255.0F;
               float blue = (rgb & 255) / 255.0F;
               float alpha = clamp((float)(strength * (0.82 + (wave.impact() ? Math.min(0.45, power * 0.28) : 0.0))), 0.0F, 1.0F);

               String mode = module.renderMode();
               if (mode.equals("Blöcke")) {
                  drawBox(matrices, fills,
                     cell.x(), cell.y(), cell.z(),
                     cell.x() + 1.0, cell.topY() - cell.y() + blockLift, cell.z() + 1.0,
                     red, green, blue, clamp(alpha * 0.52F, 0.0F, 0.65F));
               } else if (mode.equals("Neon")) {
                  drawTop(matrices, fills, cell.x(), surfaceY + 0.006, cell.z(), 1.0F,
                     red, green, blue, clamp((float)(module.surfaceAlpha() * strength * pulse * 2.8), 0.0F, 0.92F));
                  drawBorder(matrices, fills, cell.x(), surfaceY + 0.012, cell.z(), 1.0F, 0.035,
                     red, green, blue, clamp((float)(module.edgeAlpha() * strength), 0.0F, 1.0F));
               } else if (mode.equals("Hologramm")) {
                  drawTop(matrices, fills, cell.x(), surfaceY + 0.008, cell.z(), 1.0F,
                     red, green, blue, clamp((float)(module.surfaceAlpha() * strength * 0.55), 0.0F, 0.38F));
                  drawGrid(matrices, fills, cell.x(), surfaceY + 0.014, cell.z(),
                     red, green, blue, clamp((float)(module.edgeAlpha() * strength * 0.82), 0.0F, 0.9F));
               } else if (mode.equals("Minimal")) {
                  drawTop(matrices, fills, cell.x(), surfaceY + 0.014, cell.z(), 1.0F,
                     red, green, blue, clamp((float)(0.055 * strength), 0.0F, 0.1F));
                  drawBorder(matrices, fills, cell.x(), surfaceY + 0.018, cell.z(), 1.0F, 0.012,
                     red, green, blue, clamp((float)(0.58 * strength), 0.0F, 0.65F));
               } else {
                  // Energy: soft surface glow plus a bright moving edge and floating sparks.
                  drawTop(matrices, fills, cell.x(), surfaceY + 0.008, cell.z(), 1.0F,
                     red, green, blue, clamp((float)(module.surfaceAlpha() * strength * pulse * 1.25), 0.0F, 0.65F));
                  drawBorder(matrices, fills, cell.x(), surfaceY + 0.014, cell.z(), 1.0F, 0.026,
                     red, green, blue, clamp((float)(module.edgeAlpha() * strength), 0.0F, 1.0F));
                  if (cellIndex % 9 == 0) {
                     double bob = 0.10 + 0.13 * (0.5 + 0.5 * Math.sin(now / 230_000_000.0 + cellIndex));
                     double sx = cell.x() + 0.5 + Math.sin(now / 360_000_000.0 + cellIndex * 1.7) * 0.20;
                     double sz = cell.z() + 0.5 + Math.cos(now / 410_000_000.0 + cellIndex * 1.3) * 0.20;
                     drawCube(matrices, fills, sx, surfaceY + bob, sz, 0.035 + 0.025 * strength,
                        red, green, blue, clamp((float)(strength * 0.85), 0.0F, 0.9F));
                  }
               }

               // Extra thin concentric passes make the Energy mode read as several rings.
               if (mode.equals("Energy")) {
                  for (int ring = 1; ring <= 2; ring++) {
                     double offset = ring * halfWidth * 1.05;
                     double ringRel = (distance - (radius - offset)) / Math.max(0.28, halfWidth * 0.20);
                     if (Math.abs(ringRel) < 1.0) {
                        double edge = Math.pow(Math.cos(ringRel * Math.PI * 0.5), 2.0) * fade;
                        drawBorder(matrices, fills, cell.x(), surfaceY + 0.020 + ring * 0.003, cell.z(), 1.0F, 0.014,
                           red, green, blue, clamp((float)(edge * 0.72), 0.0F, 0.78F));
                     }
                  }
               }

               cellIndex++;
            }
         }
      } finally {
         matrices.method_22909();
      }
   }

   private static void drawBox(
      class_4587 matrices, class_4588 consumer,
      double x, double y, double z, double width, double height, double depth,
      float red, float green, float blue, float alpha
   ) {
      if (height <= 0.0 || alpha <= 0.005F) return;
      Matrix4f matrix = matrices.method_23760().method_23761();
      float x0=(float)x, x1=(float)width, y0=(float)y, y1=(float)(y+height), z0=(float)z, z1=(float)depth;
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
      quad(consumer,matrix,red,green,blue,alpha,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1);
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
      quad(consumer,matrix,red,green,blue,alpha,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0);
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0);
      quad(consumer,matrix,red,green,blue,alpha,x1,y0,z1,x1,y1,z1,x0,y1,z1,x0,y0,z1);
   }

   private static void drawTop(class_4587 matrices, class_4588 consumer, int x, double y, int z, float size,
                               float red, float green, float blue, float alpha) {
      if (alpha <= 0.005F) return;
      Matrix4f matrix = matrices.method_23760().method_23761();
      quad(consumer, matrix, red, green, blue, alpha,
         x, (float)y, z, x + size, (float)y, z, x + size, (float)y, z + size, x, (float)y, z + size);
   }

   private static void drawBorder(class_4587 matrices, class_4588 consumer, int x, double y, int z, float size, float width,
                                  float red, float green, float blue, float alpha) {
      drawTop(matrices, consumer, x, y, z, size, red, green, blue, alpha * 0.15F);
      drawTop(matrices, consumer, x, y, z, size, red, green, blue, alpha);
      drawStrip(matrices, consumer, x, y, z, size, width, red, green, blue, alpha);
   }

   private static void drawStrip(class_4587 matrices, class_4588 consumer, int x, double y, int z, float size, float width,
                                 float red, float green, float blue, float alpha) {
      if (alpha <= 0.005F) return;
      Matrix4f matrix = matrices.method_23760().method_23761();
      float yy = (float)y;
      rect(consumer, matrix, x, yy, z, x + size, yy, z + width, red, green, blue, alpha);
      rect(consumer, matrix, x, yy, z + size - width, x + size, yy, z + size, red, green, blue, alpha);
      rect(consumer, matrix, x, yy, z + width, x + width, yy, z + size - width, red, green, blue, alpha);
      rect(consumer, matrix, x + size - width, yy, z + width, x + size, yy, z + size - width, red, green, blue, alpha);
   }

   private static void drawGrid(class_4587 matrices, class_4588 consumer, int x, double y, int z,
                                float red, float green, float blue, float alpha) {
      float width = 0.018F;
      for (int i = 1; i <= 3; i++) {
         float offset = i * 0.25F;
         rect(consumer, matrices.method_23760().method_23761(), x + offset - width * 0.5F, (float)y, z,
            x + offset + width * 0.5F, (float)y, z + 1.0F, red, green, blue, alpha * 0.66F);
         rect(consumer, matrices.method_23760().method_23761(), x, (float)y, z + offset - width * 0.5F,
            x + 1.0F, (float)y, z + offset + width * 0.5F, red, green, blue, alpha * 0.66F);
      }
      drawStrip(matrices, consumer, x, y, z, 1.0F, width, red, green, blue, alpha);
   }

   private static void rect(class_4588 consumer, Matrix4f matrix, float x0, float y, float z0, float x1, float y2, float z1,
                            float red, float green, float blue, float alpha) {
      quad(consumer, matrix, red, green, blue, alpha, x0, y, z0, x1, y2, z0, x1, y2, z1, x0, y, z1);
   }

   private static void drawCube(class_4587 matrices, class_4588 consumer, double x, double y, double z, double half,
                                float red, float green, float blue, float alpha) {
      if (alpha <= 0.005F) return;
      Matrix4f matrix = matrices.method_23760().method_23761();
      float x0=(float)(x-half), x1=(float)(x+half), y0=(float)(y-half), y1=(float)(y+half);
      float z0=(float)(z-half), z1=(float)(z+half);
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0);
      quad(consumer,matrix,red,green,blue,alpha,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1);
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1);
      quad(consumer,matrix,red,green,blue,alpha,x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0);
      quad(consumer,matrix,red,green,blue,alpha,x0,y0,z0,x0,y1,z0,x1,y1,z0,x1,y0,z0);
      quad(consumer,matrix,red,green,blue,alpha,x1,y0,z1,x1,y1,z1,x0,y1,z1,x0,y0,z1);
   }

   private static void quad(class_4588 consumer, Matrix4f matrix, float red, float green, float blue, float alpha,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3) {
      consumer.method_22918(matrix, x0, y0, z0).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x1, y1, z1).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x2, y2, z2).method_22915(red, green, blue, alpha);
      consumer.method_22918(matrix, x3, y3, z3).method_22915(red, green, blue, alpha);
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }
}

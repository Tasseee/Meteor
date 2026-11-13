package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FakeLag;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_1007;
import net.minecraft.class_10017;
import net.minecraft.class_10055;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_238;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_897;
import org.joml.Matrix4f;

/**
 * Renders the local player's last acknowledged server position while FakeLag
 * is holding movement packets. A colored outline and connector make the
 * server/client position difference easier to read.
 */
public final class FakeLagGhostRenderer {
   private static final float RED = 0.20F;
   private static final float GREEN = 0.82F;
   private static final float BLUE = 1.00F;

   private FakeLagGhostRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(FakeLagGhostRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null || context.matrices() == null) return;

      FakeLag fakeLag;
      try {
         fakeLag = ModuleManager.get(FakeLag.class);
      } catch (IllegalStateException ignored) {
         return;
      }
      if (!fakeLag.isGhostEnabled()) return;

      FakeLag.GhostSnapshot snapshot = FakeLag.snapshot();
      if (snapshot == null) return;

      // Render the same player model at the server's last acknowledged position.
      if (context.commandQueue() != null && context.worldState() != null
         && context.worldState().field_63082 != null) {
         try {
            class_897 baseRenderer = mc.method_1561().method_3953(mc.field_1724);
            if (baseRenderer instanceof class_1007 playerRenderer) {
               float tickDelta = mc.method_1561().method_3953(mc.field_1724) == null
                  ? 0.0F
                  : mc.field_1724.method_5695();
               class_10055 state = playerRenderer.method_62608();
               playerRenderer.method_62354(mc.field_1724, state, tickDelta);

               state.field_53325 = snapshot.x();
               state.field_53326 = snapshot.y();
               state.field_53327 = snapshot.z();
               state.field_53446 = snapshot.yaw();
               state.field_53447 = 0.0F;
               state.field_53448 = snapshot.pitch();

               // Do not render a second nameplate above the visual server-position copy.
               state.field_53337 = null;
               state.field_53338 = null;

               ((class_897)playerRenderer).method_3936(
                  (class_10017)state,
                  context.matrices(),
                  context.commandQueue(),
                  context.worldState().field_63082
               );
            }
         } catch (Throwable ignored) {
            // A render-only helper must never interrupt the normal world render.
         }
      }

      if ((fakeLag.isGhostOutlineEnabled() || fakeLag.isGhostLinkEnabled()) && context.consumers() != null) {
         try {
            renderMarkers(context, mc, snapshot, fakeLag);
         } catch (Throwable ignored) {
            // Marker rendering is optional and must not affect the normal world render.
         }
      }
   }

   private static void renderMarkers(
      WorldRenderContext context,
      class_310 mc,
      FakeLag.GhostSnapshot snapshot,
      FakeLag fakeLag
   ) {
      class_4184 cameraView = mc.field_1773.method_19418();
      if (cameraView == null || !cameraView.method_19332()) return;
      class_243 camera = cameraView.method_71156();
      double cameraX = camera.field_1352;
      double cameraY = camera.field_1351;
      double cameraZ = camera.field_1350;

      double playerX = mc.field_1724.method_23317();
      double playerY = mc.field_1724.method_23318();
      double playerZ = mc.field_1724.method_23321();
      double offsetX = snapshot.x() - playerX;
      double offsetY = snapshot.y() - playerY;
      double offsetZ = snapshot.z() - playerZ;

      class_238 currentBox = mc.field_1724.method_5829();
      class_238 ghostBox = new class_238(
         currentBox.field_1323 + offsetX,
         currentBox.field_1322 + offsetY,
         currentBox.field_1321 + offsetZ,
         currentBox.field_1320 + offsetX,
         currentBox.field_1325 + offsetY,
         currentBox.field_1324 + offsetZ
      );

      class_4587 matrices = context.matrices();
      matrices.method_22903();
      try {
         matrices.method_22904(-cameraX, -cameraY, -cameraZ);
         class_4588 vertices = context.consumers().method_73477(class_12249.method_76015());
         Matrix4f matrix = matrices.method_23760().method_23761();

         if (fakeLag.isGhostOutlineEnabled()) {
            drawBox(vertices, matrix, ghostBox, 0.88F);
         }

         if (fakeLag.isGhostLinkEnabled()) {
            double gapSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
            if (gapSquared > 0.0025) {
               drawLine(
                  vertices,
                  matrix,
                  playerX,
                  playerY + 0.9,
                  playerZ,
                  snapshot.x(),
                  snapshot.y() + 0.9,
                  snapshot.z(),
                  0.52F,
                  2.0F
               );
            }
         }
      } finally {
         matrices.method_22904(cameraX, cameraY, cameraZ);
         matrices.method_22909();
      }
   }

   private static void drawBox(class_4588 vertices, Matrix4f matrix, class_238 box, float alpha) {
      double minX = box.field_1323;
      double minY = box.field_1322;
      double minZ = box.field_1321;
      double maxX = box.field_1320;
      double maxY = box.field_1325;
      double maxZ = box.field_1324;

      for (int corner = 0; corner < 8; corner++) {
         for (int bit = 1; bit <= 4; bit <<= 1) {
            if ((corner & bit) == 0) {
               int next = corner | bit;
               double x0 = (corner & 1) == 0 ? minX : maxX;
               double y0 = (corner & 2) == 0 ? minY : maxY;
               double z0 = (corner & 4) == 0 ? minZ : maxZ;
               double x1 = (next & 1) == 0 ? minX : maxX;
               double y1 = (next & 2) == 0 ? minY : maxY;
               double z1 = (next & 4) == 0 ? minZ : maxZ;
               drawLine(vertices, matrix, x0, y0, z0, x1, y1, z1, alpha, 2.0F);
            }
         }
      }
   }

   private static void drawLine(
      class_4588 vertices,
      Matrix4f matrix,
      double x0,
      double y0,
      double z0,
      double x1,
      double y1,
      double z1,
      float alpha,
      float width
   ) {
      vertices.method_22918(matrix, (float)x0, (float)y0, (float)z0)
         .method_22915(RED, GREEN, BLUE, alpha)
         .method_75298(width)
         .method_22914(0.0F, 1.0F, 0.0F);
      vertices.method_22918(matrix, (float)x1, (float)y1, (float)z1)
         .method_22915(RED, GREEN, BLUE, alpha)
         .method_75298(width)
         .method_22914(0.0F, 1.0F, 0.0F);
   }
}

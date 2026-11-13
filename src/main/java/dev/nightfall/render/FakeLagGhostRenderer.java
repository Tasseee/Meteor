package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FakeLag;
import dev.nightfall.util.Projection;
import java.util.List;
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

/** Renders the server-position ghost and the buffered movement path. */
public final class FakeLagGhostRenderer {
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

      if (fakeLag.isModelMode() && context.commandQueue() != null
         && context.worldState() != null && context.worldState().field_63082 != null) {
         renderModel(context, mc, snapshot);
      }

      if (context.consumers() != null
         && (fakeLag.isBoxMode() || fakeLag.isWireframeMode()
            || fakeLag.isGhostOutlineEnabled() || fakeLag.isGhostLinkEnabled()
            || fakeLag.isGhostTrailEnabled())) {
         renderMarkers(context, mc, snapshot, fakeLag);
      }
   }

   private static void renderModel(
      WorldRenderContext context,
      class_310 mc,
      FakeLag.GhostSnapshot snapshot
   ) {
      try {
         class_897 baseRenderer = mc.method_1561().method_3953(mc.field_1724);
         if (!(baseRenderer instanceof class_1007 playerRenderer)) return;

         class_10055 state = playerRenderer.method_62608();
         playerRenderer.method_62354(mc.field_1724, state, Projection.tickDelta());
         state.field_53325 = snapshot.x();
         state.field_53326 = snapshot.y();
         state.field_53327 = snapshot.z();
         state.field_53446 = snapshot.yaw();
         state.field_53447 = 0.0F;
         state.field_53448 = snapshot.pitch();
         // Avoid drawing a second nameplate above the ghost model.
         state.field_53337 = null;
         state.field_53338 = null;

         ((class_897)playerRenderer).method_3936(
            (class_10017)state,
            context.matrices(),
            context.commandQueue(),
            context.worldState().field_63082
         );
      } catch (Throwable ignored) {
         // A render-only helper must never interrupt the normal world render.
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

      int rgb = fakeLag.ghostColor();
      float red = (rgb >> 16 & 255) / 255.0F;
      float green = (rgb >> 8 & 255) / 255.0F;
      float blue = (rgb & 255) / 255.0F;
      float opacity = fakeLag.ghostOpacity();

      class_4587 matrices = context.matrices();
      matrices.method_22903();
      try {
         matrices.method_22904(-cameraX, -cameraY, -cameraZ);
         Matrix4f matrix = matrices.method_23760().method_23761();

         if (fakeLag.isBoxMode()) {
            class_4588 fill = context.consumers().method_73477(class_12249.method_76019());
            drawFilledBox(fill, matrix, ghostBox, red, green, blue, opacity * 0.18F);
         }

         boolean drawEdges = fakeLag.isWireframeMode()
            || fakeLag.isBoxMode()
            || fakeLag.isGhostOutlineEnabled();
         if (drawEdges) {
            class_4588 lines = context.consumers().method_73477(class_12249.method_76015());
            drawBox(lines, matrix, ghostBox, red, green, blue, opacity);
         }

         if (fakeLag.isGhostLinkEnabled()) {
            double gapSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
            if (gapSquared > 0.0025) {
               class_4588 lines = context.consumers().method_73477(class_12249.method_76015());
               drawLine(
                  lines, matrix,
                  playerX, playerY + 0.9, playerZ,
                  snapshot.x(), snapshot.y() + 0.9, snapshot.z(),
                  red, green, blue, opacity * 0.72F, 2.0F
               );
            }
         }

         if (fakeLag.isGhostTrailEnabled()) {
            drawTrail(context, matrix, snapshot.trail(), fakeLag, red, green, blue);
         }
      } finally {
         matrices.method_22904(cameraX, cameraY, cameraZ);
         matrices.method_22909();
      }
   }

   private static void drawTrail(
      WorldRenderContext context,
      Matrix4f matrix,
      List<FakeLag.GhostPoint> trail,
      FakeLag fakeLag,
      float red,
      float green,
      float blue
   ) {
      if (trail.size() < 2) return;
      class_4588 lines = context.consumers().method_73477(class_12249.method_76015());
      int start = Math.max(0, trail.size() - fakeLag.trailLength());
      int count = trail.size() - start;
      for (int i = 1; i < count; i++) {
         FakeLag.GhostPoint from = trail.get(start + i - 1);
         FakeLag.GhostPoint to = trail.get(start + i);
         float progress = i / (float)Math.max(1, count - 1);
         float alpha = fakeLag.ghostOpacity() * (0.12F + 0.68F * progress);
         float width = 1.0F + progress * 1.3F;
         drawLine(
            lines, matrix,
            from.x(), from.y() + 0.08, from.z(),
            to.x(), to.y() + 0.08, to.z(),
            red, green, blue, alpha, width
         );
      }
   }

   private static void drawFilledBox(
      class_4588 vertices,
      Matrix4f matrix,
      class_238 box,
      float red,
      float green,
      float blue,
      float alpha
   ) {
      float x0 = (float)box.field_1323;
      float y0 = (float)box.field_1322;
      float z0 = (float)box.field_1321;
      float x1 = (float)box.field_1320;
      float y1 = (float)box.field_1325;
      float z1 = (float)box.field_1324;
      quad(vertices, matrix, red, green, blue, alpha, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
      quad(vertices, matrix, red, green, blue, alpha, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
      quad(vertices, matrix, red, green, blue, alpha, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
      quad(vertices, matrix, red, green, blue, alpha, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
      quad(vertices, matrix, red, green, blue, alpha, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
      quad(vertices, matrix, red, green, blue, alpha, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
   }

   private static void quad(
      class_4588 vertices,
      Matrix4f matrix,
      float red,
      float green,
      float blue,
      float alpha,
      float x0, float y0, float z0,
      float x1, float y1, float z1,
      float x2, float y2, float z2,
      float x3, float y3, float z3
   ) {
      vertices.method_22918(matrix, x0, y0, z0).method_22915(red, green, blue, alpha);
      vertices.method_22918(matrix, x1, y1, z1).method_22915(red, green, blue, alpha);
      vertices.method_22918(matrix, x2, y2, z2).method_22915(red, green, blue, alpha);
      vertices.method_22918(matrix, x3, y3, z3).method_22915(red, green, blue, alpha);
   }

   private static void drawBox(
      class_4588 vertices,
      Matrix4f matrix,
      class_238 box,
      float red,
      float green,
      float blue,
      float alpha
   ) {
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
               drawLine(vertices, matrix, x0, y0, z0, x1, y1, z1, red, green, blue, alpha, 2.0F);
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
      float red,
      float green,
      float blue,
      float alpha,
      float width
   ) {
      vertices.method_22918(matrix, (float)x0, (float)y0, (float)z0)
         .method_22915(red, green, blue, alpha)
         .method_75298(width)
         .method_22914(0.0F, 1.0F, 0.0F);
      vertices.method_22918(matrix, (float)x1, (float)y1, (float)z1)
         .method_22915(red, green, blue, alpha)
         .method_75298(width)
         .method_22914(0.0F, 1.0F, 0.0F);
   }
}

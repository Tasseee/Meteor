package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_12249;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_777;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Builds a neon contour from camera-facing faces of the baked item mesh. */
public final class ItemGlowRenderer {
   private ItemGlowRenderer() {}

   public static void render(class_4587 matrices, class_4597 consumers, List<?> rawQuads) {
      ItemGlow module;
      try {
         module = ModuleManager.get(ItemGlow.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled() || matrices == null || consumers == null || rawQuads == null || rawQuads.isEmpty()) return;

      Map<EdgeKey, EdgeInfo> edges = new LinkedHashMap<>();
      class_4587.class_4665 entry = matrices.method_23760();
      Matrix3f normalMatrix = entry.method_23762();
      Matrix4f positionMatrix = entry.method_23761();

      for (Object raw : rawQuads) {
         if (!(raw instanceof class_777 quad)) continue;
         Vector3f[] points = {
            new Vector3f(quad.comp_5238()),
            new Vector3f(quad.comp_5239()),
            new Vector3f(quad.comp_5240()),
            new Vector3f(quad.comp_5241())
         };
         Vector3f localNormal = new Vector3f(points[1]).sub(points[0])
            .cross(new Vector3f(points[2]).sub(points[0]));
         if (localNormal.lengthSquared() < 1.0E-10F) continue;
         localNormal.normalize();
         Vector3f viewNormal = new Vector3f(localNormal);
         normalMatrix.transform(viewNormal).normalize();
         boolean cameraFacing = viewNormal.z > 0.015F;

         for (int i = 0; i < 4; i++) {
            Vector3f a = points[i];
            Vector3f b = points[(i + 1) & 3];
            EdgeInfo info = edges.computeIfAbsent(EdgeKey.of(a, b), ignored -> new EdgeInfo(a, b));
            if (cameraFacing) {
               info.frontFaces++;
               info.frontNormal.set(localNormal);
            } else {
               info.otherFaces++;
            }
         }
      }

      int rgb = module.currentGlowColor();
      int red = rgb >> 16 & 0xFF;
      int green = rgb >> 8 & 0xFF;
      int blue = rgb & 0xFF;
      int alpha = module.alpha();
      float offset = module.edgeOffset();
      class_4588 lines = consumers.method_73477(class_12249.method_76668());

      for (EdgeInfo edge : edges.values()) {
         boolean silhouette = edge.frontFaces > 0 && (edge.otherFaces > 0 || edge.frontFaces == 1);
         if (!silhouette) continue;
         Vector3f a = new Vector3f(edge.a).fma(offset, edge.frontNormal);
         Vector3f b = new Vector3f(edge.b).fma(offset, edge.frontNormal);
         emit(lines, positionMatrix, a, b, red, green, blue, Math.max(1, alpha / 4));
         emit(lines, positionMatrix, a, b, red, green, blue, alpha);
      }
   }

   private static void emit(class_4588 consumer, Matrix4f matrix, Vector3f a, Vector3f b,
                            int red, int green, int blue, int alpha) {
      consumer.method_22918(matrix, a.x, a.y, a.z).method_1336(red, green, blue, alpha).method_22914(0.0F, 0.0F, 1.0F);
      consumer.method_22918(matrix, b.x, b.y, b.z).method_1336(red, green, blue, alpha).method_22914(0.0F, 0.0F, 1.0F);
   }

   private record PointKey(long x, long y, long z) {
      private static PointKey of(Vector3fc point) {
         return new PointKey(Math.round(point.x() * 100000.0), Math.round(point.y() * 100000.0), Math.round(point.z() * 100000.0));
      }
      private boolean before(PointKey other) {
         if (this.x != other.x) return this.x < other.x;
         if (this.y != other.y) return this.y < other.y;
         return this.z <= other.z;
      }
   }

   private record EdgeKey(PointKey first, PointKey second) {
      private static EdgeKey of(Vector3fc a, Vector3fc b) {
         PointKey first = PointKey.of(a);
         PointKey second = PointKey.of(b);
         return first.before(second) ? new EdgeKey(first, second) : new EdgeKey(second, first);
      }
   }

   private static final class EdgeInfo {
      private final Vector3f a;
      private final Vector3f b;
      private final Vector3f frontNormal = new Vector3f(0.0F, 0.0F, 1.0F);
      private int frontFaces;
      private int otherFaces;
      private EdgeInfo(Vector3f a, Vector3f b) {
         this.a = new Vector3f(a);
         this.b = new Vector3f(b);
      }
   }
}
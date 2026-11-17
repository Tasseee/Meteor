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
import net.minecraft.class_811;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Draws a neon contour from the silhouette edges of the real baked item mesh.
 * Only first-person hand items are affected; this is not an enchantment effect.
 */
public final class ItemGlowMeshRenderer {
   private ItemGlowMeshRenderer() {}

   public static void render(
      class_811 displayContext,
      class_4587 matrices,
      class_4597 consumers,
      List<?> rawQuads
   ) {
      ItemGlow module;
      try {
         module = ModuleManager.get(ItemGlow.class);
      } catch (Throwable ignored) {
         return;
      }
      if (module == null || !module.isEnabled() || !module.modelOutline()
         || displayContext == null || !displayContext.method_29998()
         || matrices == null || consumers == null || rawQuads == null || rawQuads.isEmpty()) {
         return;
      }

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
               info.frontA.set(a);
               info.frontB.set(b);
               info.frontNormal.set(localNormal);
            } else {
               info.otherFaces++;
            }
         }
      }

      if (edges.isEmpty()) return;

      int rgb = module.color(0.0F);
      int red = rgb >> 16 & 0xFF;
      int green = rgb >> 8 & 0xFF;
      int blue = rgb & 0xFF;
      int coreAlpha = clamp(Math.round(235.0F * module.intensity() * module.outlineStrength()));
      int haloAlpha = clamp(Math.round(coreAlpha * 0.24F));

      class_4588 halo = consumers.method_73477(class_12249.field_64592);
      class_4588 core = consumers.method_73477(class_12249.field_64042);

      for (EdgeInfo edge : edges.values()) {
         // A boundary between a camera-facing face and a side/back face is a silhouette edge.
         boolean silhouette = edge.frontFaces > 0
            && (edge.otherFaces > 0 || edge.frontFaces == 1);
         if (!silhouette) continue;

         Vector3f direction = new Vector3f(edge.frontB).sub(edge.frontA);
         Vector3f outward = direction.cross(new Vector3f(edge.frontNormal));
         if (outward.lengthSquared() < 1.0E-10F) continue;
         outward.normalize();

         Vector3f a = new Vector3f(edge.frontA);
         Vector3f b = new Vector3f(edge.frontB);
         Vector3f haloA = new Vector3f(a).fma(0.0025F, outward);
         Vector3f haloB = new Vector3f(b).fma(0.0025F, outward);

         emit(halo, positionMatrix, haloA, haloB, edge.frontNormal, red, green, blue, haloAlpha, 5.0F);
         emit(halo, positionMatrix, a, b, edge.frontNormal, red, green, blue, haloAlpha, 3.0F);
         emit(core, positionMatrix, a, b, edge.frontNormal, red, green, blue, coreAlpha, 1.5F);
      }
   }

   private static void emit(
      class_4588 consumer,
      Matrix4f matrix,
      Vector3f a,
      Vector3f b,
      Vector3f normal,
      int red,
      int green,
      int blue,
      int alpha,
      float lineWidth
   ) {
      consumer.method_22918(matrix, a.x, a.y, a.z)
         .method_1336(red, green, blue, alpha)
         .method_22914(normal.x, normal.y, normal.z)
         .method_75298(lineWidth);
      consumer.method_22918(matrix, b.x, b.y, b.z)
         .method_1336(red, green, blue, alpha)
         .method_22914(normal.x, normal.y, normal.z)
         .method_75298(lineWidth);
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }

   private record PointKey(long x, long y, long z) {
      private static PointKey of(Vector3fc point) {
         return new PointKey(
            Math.round(point.x() * 100000.0),
            Math.round(point.y() * 100000.0),
            Math.round(point.z() * 100000.0)
         );
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
      private final Vector3f frontA = new Vector3f();
      private final Vector3f frontB = new Vector3f();
      private final Vector3f frontNormal = new Vector3f(0.0F, 0.0F, 1.0F);
      private int frontFaces;
      private int otherFaces;

      private EdgeInfo(Vector3f ignoredA, Vector3f ignoredB) {}
   }
}
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
import net.minecraft.class_310;
import net.minecraft.class_4184;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Quaternionf;

/**
 * Builds a screen-facing neon band from the silhouette edges of the baked
 * item mesh. This is independent of Minecraft's enchantment glint.
 */
public final class ItemGlowRenderer {
   private ItemGlowRenderer() {}

   public static void render(class_811 displayContext, class_4587 matrices, class_4597 consumers, List<?> rawQuads) {
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
      Vector3f towardCamera = new Vector3f(0.0F, 0.0F, 1.0F);
      if (displayContext != class_811.field_4317) {
         try {
            class_310 mc = class_310.method_1551();
            class_4184 camera = mc.field_1773.method_19418();
            Quaternionf cameraRotation = camera.method_23767();
            towardCamera.rotate(cameraRotation).normalize();
         } catch (Throwable ignored) {
            towardCamera.set(0.0F, 0.0F, 1.0F);
         }
      }

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
         boolean cameraFacing = viewNormal.dot(towardCamera) > 0.015F;

         for (int i = 0; i < 4; i++) {
            Vector3f a = points[i];
            Vector3f b = points[(i + 1) & 3];
            EdgeInfo info = edges.computeIfAbsent(EdgeKey.of(a, b), ignored -> new EdgeInfo(a, b));
            if (cameraFacing) {
               info.frontFaces++;
               // Keep the front-face winding: edge x normal points outside.
               info.frontA.set(a);
               info.frontB.set(b);
               info.frontNormal.set(localNormal);
            } else {
               info.otherFaces++;
            }
         }
      }

      int rgb = module.currentGlowColor();
      float red = (rgb >> 16 & 0xFF) / 255.0F;
      float green = (rgb >> 8 & 0xFF) / 255.0F;
      float blue = (rgb & 0xFF) / 255.0F;
      float opacity = module.alpha() / 255.0F;
      float offset = module.edgeOffset();
      float width = module.thickness();
      class_4588 fills = consumers.method_73477(class_12249.method_76023());

      for (EdgeInfo edge : edges.values()) {
         boolean silhouette = edge.frontFaces > 0 && (edge.otherFaces > 0 || edge.frontFaces == 1);
         if (!silhouette) continue;

         Vector3f direction = new Vector3f(edge.frontB).sub(edge.frontA);
         Vector3f outward = direction.cross(new Vector3f(edge.frontNormal));
         if (outward.lengthSquared() < 1.0E-10F) continue;
         outward.normalize();

         Vector3f a = new Vector3f(edge.frontA).fma(offset, edge.frontNormal);
         Vector3f b = new Vector3f(edge.frontB).fma(offset, edge.frontNormal);

         // A wider, transparent band creates the outer neon halo.
         emitBand(fills, positionMatrix, a, b, outward, width * 2.5F,
            red, green, blue, opacity * 0.16F);
         // The saturated inner band creates the crisp visible contour.
         emitBand(fills, positionMatrix, a, b, outward, width,
            red, green, blue, opacity);
      }
   }

   private static void emitBand(
      class_4588 consumer, Matrix4f matrix, Vector3f a, Vector3f b, Vector3f outward,
      float width, float red, float green, float blue, float alpha
   ) {
      Vector3f outerA = new Vector3f(a).fma(width, outward);
      Vector3f outerB = new Vector3f(b).fma(width, outward);
      // Winding faces the camera-facing side of the item surface.
      vertex(consumer, matrix, a, red, green, blue, alpha);
      vertex(consumer, matrix, outerA, red, green, blue, alpha);
      vertex(consumer, matrix, outerB, red, green, blue, alpha);
      vertex(consumer, matrix, b, red, green, blue, alpha);
   }

   private static void vertex(
      class_4588 consumer, Matrix4f matrix, Vector3f point,
      float red, float green, float blue, float alpha
   ) {
      consumer.method_22918(matrix, point.x, point.y, point.z).method_22915(red, green, blue, alpha);
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
      private final Vector3f frontA = new Vector3f();
      private final Vector3f frontB = new Vector3f();
      private final Vector3f frontNormal = new Vector3f(0.0F, 0.0F, 1.0F);
      private int frontFaces;
      private int otherFaces;
      private EdgeInfo(Vector3f a, Vector3f b) {
         this.a = new Vector3f(a);
         this.b = new Vector3f(b);
      }
   }
}
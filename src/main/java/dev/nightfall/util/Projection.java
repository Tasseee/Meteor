package dev.nightfall.util;

import dev.nightfall.mixin.GameRendererAccessor;
import dev.nightfall.mixin.CameraProjectionAccessor;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_4184;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Projection {
   private static double ex;
   private static double ey;
   private static double ez;
   private static int sw;
   private static int sh;
   private static float tickDelta;
   private static Matrix4f viewProjection;
   private static boolean valid;

   private Projection() {
   }

   public static boolean valid() {
      return valid;
   }

   public static int width() {
      return sw;
   }

   public static int height() {
      return sh;
   }

   public static double eyeX() {
      return ex;
   }

   public static double eyeY() {
      return ey;
   }

   public static double eyeZ() {
      return ez;
   }

   public static float tickDelta() {
      return tickDelta;
   }

   public static void update(class_310 mc, float frameTickDelta, int width, int height) {
      valid = false;
      viewProjection = null;
      if (mc.field_1724 != null && mc.field_1687 != null && width > 0 && height > 0 && Float.isFinite(frameTickDelta)) {
         class_4184 camera = mc.field_1773.method_19418();
         if (camera != null && camera.method_19332()) {
            class_243 cameraPos = camera.method_71156();
            ex = cameraPos.field_1352;
            ey = cameraPos.field_1351;
            ez = cameraPos.field_1350;
            GameRendererAccessor renderer = (GameRendererAccessor)mc.field_1773;
            // Read the camera's effective projection instead of GameRenderer#getFov.
            // Zoomify changes Camera's projection while its zoom key is held, so the
            // renderer FOV alone can be stale and make ESP boxes drift away from entities.
            CameraProjectionAccessor cameraProjection = (CameraProjectionAccessor) camera.method_36425();
            class_243 halfHeight = cameraProjection.nightfall$getHalfHeight();
            double halfHeightLength = Math.sqrt(
               halfHeight.field_1352 * halfHeight.field_1352
                  + halfHeight.field_1351 * halfHeight.field_1351
                  + halfHeight.field_1350 * halfHeight.field_1350
            );
            float fov = (float) Math.toDegrees(2.0 * Math.atan(halfHeightLength));
            if (Float.isFinite(fov) && fov > 0.0F) {
               Matrix4f projection = renderer.nightfall$invokeGetProjectionMatrix(fov);
               Quaternionf inverseCameraRotation = camera.method_23767().conjugate(new Quaternionf());
               Matrix4f view = new Matrix4f().rotation(inverseCameraRotation);
               viewProjection = projection.mul(view);
               sw = width;
               sh = height;
               tickDelta = Math.max(0.0F, Math.min(1.0F, frameTickDelta));
               valid = Double.isFinite(ex) && Double.isFinite(ey) && Double.isFinite(ez);
            }
         }
      }
   }

   public static boolean canSeeFromCamera(class_310 mc, class_1297 target) {
      if (mc != null && mc.field_1687 != null && mc.field_1724 != null && target != null) {
         class_4184 camera = mc.field_1773.method_19418();
         class_243 start = camera != null && camera.method_19332() ? camera.method_71156() : mc.field_1724.method_33571();
         class_238 box = target.method_5829();
         class_243 end = new class_243(
            (box.field_1323 + box.field_1320) * 0.5, (box.field_1322 + box.field_1325) * 0.5, (box.field_1321 + box.field_1324) * 0.5
         );
         return start.method_1025(end) < 1.0E-8
            ? true
            : mc.field_1687.method_17742(new class_3959(start, end, class_3960.field_17558, class_242.field_1348, mc.field_1724)).method_17783()
               == class_240.field_1333;
      } else {
         return false;
      }
   }

   public static boolean projectToEdge(double x, double y, double z, double[] out) {
      if (valid && viewProjection != null && out != null && out.length >= 2 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
         float dx = (float)(x - ex);
         float dy = (float)(y - ey);
         float dz = (float)(z - ez);
         float clipX = viewProjection.m00() * dx + viewProjection.m10() * dy + viewProjection.m20() * dz;
         float clipY = viewProjection.m01() * dx + viewProjection.m11() * dy + viewProjection.m21() * dz;
         float clipW = viewProjection.m03() * dx + viewProjection.m13() * dy + viewProjection.m23() * dz;
         if (Float.isFinite(clipX) && Float.isFinite(clipY) && Float.isFinite(clipW)) {
            if (clipW < 0.0F) {
               clipX = -clipX;
               clipY = -clipY;
            }

            double vx = clipX;
            double vy = -clipY;
            double length = Math.hypot(vx, vy);
            if (length < 1.0E-6) {
               vx = 0.0;
               vy = -1.0;
               length = 1.0;
            }

            vx /= length;
            vy /= length;
            double cx = sw * 0.5;
            double cy = sh * 0.5;
            double halfW = Math.max(1.0, cx - 14.0);
            double halfH = Math.max(1.0, cy - 14.0);
            double scaleX = Math.abs(vx) < 1.0E-6 ? Double.POSITIVE_INFINITY : halfW / Math.abs(vx);
            double scaleY = Math.abs(vy) < 1.0E-6 ? Double.POSITIVE_INFINITY : halfH / Math.abs(vy);
            double scale = Math.min(scaleX, scaleY);
            out[0] = cx + vx * scale;
            out[1] = cy + vy * scale;
            return Double.isFinite(out[0]) && Double.isFinite(out[1]);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean project(double x, double y, double z, double[] out) {
      if (valid && viewProjection != null && out != null && out.length >= 2 && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
         float dx = (float)(x - ex);
         float dy = (float)(y - ey);
         float dz = (float)(z - ez);
         float clipX = viewProjection.m00() * dx + viewProjection.m10() * dy + viewProjection.m20() * dz + viewProjection.m30();
         float clipY = viewProjection.m01() * dx + viewProjection.m11() * dy + viewProjection.m21() * dz + viewProjection.m31();
         float clipW = viewProjection.m03() * dx + viewProjection.m13() * dy + viewProjection.m23() * dz + viewProjection.m33();
         if (Float.isFinite(clipX) && Float.isFinite(clipY) && Float.isFinite(clipW) && !(clipW <= 1.0E-4F)) {
            double ndcX = clipX / clipW;
            double ndcY = clipY / clipW;
            out[0] = (ndcX * 0.5 + 0.5) * sw;
            out[1] = (0.5 - ndcY * 0.5) * sh;
            return Double.isFinite(out[0]) && Double.isFinite(out[1]);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}

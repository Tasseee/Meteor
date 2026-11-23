package dev.nightfall.util;

import net.minecraft.class_238;
import net.minecraft.class_332;

/**
 * Shared screen-space ESP box renderer. Uses the same rounded outline, soft
 * glow and translucent fill as the dedicated EntityESP 2D module.
 */
public final class Esp2DRenderer {
   private Esp2DRenderer() {
   }

   public static boolean draw(class_332 ctx, class_238 box, int argb, int thickness) {
      return draw(ctx, box, argb, thickness, 35, true);
   }

   public static boolean draw(class_332 ctx, class_238 box, int argb, int thickness, int fillAlpha, boolean glow) {
      if (ctx == null || box == null || !Projection.valid()) {
         return false;
      }

      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      double[] projected = new double[2];
      int projectedCorners = 0;

      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? box.field_1323 : box.field_1320;
         double y = (i & 2) == 0 ? box.field_1322 : box.field_1325;
         double z = (i & 4) == 0 ? box.field_1321 : box.field_1324;
         if (Projection.project(x, y, z, projected)) {
            projectedCorners++;
            minX = Math.min(minX, projected[0]);
            minY = Math.min(minY, projected[1]);
            maxX = Math.max(maxX, projected[0]);
            maxY = Math.max(maxY, projected[1]);
         }
      }

      if (projectedCorners == 0 || maxX < 0.0 || maxY < 0.0
         || minX > Projection.width() || minY > Projection.height()) {
         return false;
      }

      minX = Math.max(0.0, Math.min(Projection.width(), minX - 1.5));
      minY = Math.max(0.0, Math.min(Projection.height(), minY - 1.5));
      maxX = Math.max(0.0, Math.min(Projection.width(), maxX + 1.5));
      maxY = Math.max(0.0, Math.min(Projection.height(), maxY + 1.5));

      double width = maxX - minX;
      double height = maxY - minY;
      if (width < 2.0 || height < 2.0) {
         return false;
      }

      int left = (int)Math.floor(minX);
      int top = (int)Math.floor(minY);
      int right = (int)Math.ceil(maxX);
      int bottom = (int)Math.ceil(maxY);
      int rgb = argb & 0xFFFFFF;
      int baseAlpha = argb >>> 24 & 0xFF;
      double radius = Math.min(5.0, Math.min(width, height) * 0.5);
      int lineWidth = Math.max(1, thickness);

      if (fillAlpha > 0 && baseAlpha > 0) {
         int alpha = (int)Math.round(Math.min(255, fillAlpha) * baseAlpha / 255.0);
         if (alpha > 0) {
            ctx.method_25294(left, top, right, bottom, Theme.withAlpha(rgb, alpha));
         }
      }

      if (glow && baseAlpha > 0) {
         drawRoundedOutline(ctx, minX, minY, maxX, maxY, radius + 2.0,
            Math.max(3, lineWidth + 3), Theme.withAlpha(rgb, (int)(18.0 * baseAlpha / 255.0)));
         drawRoundedOutline(ctx, minX, minY, maxX, maxY, radius + 1.0,
            Math.max(2, lineWidth + 1), Theme.withAlpha(rgb, (int)(42.0 * baseAlpha / 255.0)));
      }

      drawRoundedOutline(ctx, minX, minY, maxX, maxY, radius, lineWidth, argb);

      // Keep shared label placement consistent with Draw.box().
      Draw.topX = (float)((minX + maxX) * 0.5);
      Draw.topY = (float)minY;
      Draw.botY = (float)maxY;
      return true;
   }

   private static void drawRoundedOutline(class_332 ctx, double x0, double y0, double x1, double y1,
      double radius, int lineWidth, int argb) {
      double r = Math.max(0.0, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5));
      if (r < 1.0) {
         Draw.line(ctx, (float)x0, (float)y0, (float)x1, (float)y0, argb, lineWidth, true);
         Draw.line(ctx, (float)x1, (float)y0, (float)x1, (float)y1, argb, lineWidth, true);
         Draw.line(ctx, (float)x1, (float)y1, (float)x0, (float)y1, argb, lineWidth, true);
         Draw.line(ctx, (float)x0, (float)y1, (float)x0, (float)y0, argb, lineWidth, true);
         return;
      }

      Draw.line(ctx, (float)(x0 + r), (float)y0, (float)(x1 - r), (float)y0, argb, lineWidth, true);
      Draw.line(ctx, (float)x1, (float)(y0 + r), (float)x1, (float)(y1 - r), argb, lineWidth, true);
      Draw.line(ctx, (float)(x1 - r), (float)y1, (float)(x0 + r), (float)y1, argb, lineWidth, true);
      Draw.line(ctx, (float)x0, (float)(y1 - r), (float)x0, (float)(y0 + r), argb, lineWidth, true);

      drawArc(ctx, x0 + r, y0 + r, r, 180.0, argb, lineWidth);
      drawArc(ctx, x1 - r, y0 + r, r, 270.0, argb, lineWidth);
      drawArc(ctx, x1 - r, y1 - r, r, 0.0, argb, lineWidth);
      drawArc(ctx, x0 + r, y1 - r, r, 90.0, argb, lineWidth);
   }

   private static void drawArc(class_332 ctx, double cx, double cy, double radius, double startDegrees,
      int argb, int lineWidth) {
      int segments = Math.max(6, (int)(radius * 1.5));
      double previousX = cx + Math.cos(Math.toRadians(startDegrees)) * radius;
      double previousY = cy + Math.sin(Math.toRadians(startDegrees)) * radius;
      for (int i = 1; i <= segments; i++) {
         double angle = Math.toRadians(startDegrees + 90.0 * i / segments);
         double nextX = cx + Math.cos(angle) * radius;
         double nextY = cy + Math.sin(angle) * radius;
         Draw.line(ctx, (float)previousX, (float)previousY, (float)nextX, (float)nextY, argb, lineWidth, true);
         previousX = nextX;
         previousY = nextY;
      }
   }
}

package dev.nightfall.util;

import net.minecraft.class_238;
import net.minecraft.class_327;
import net.minecraft.class_332;
import org.joml.Matrix3x2fStack;

public final class Draw {
   public static float topX;
   public static float topY;
   public static float botY;
   private static final double[][] C = new double[8][2];
   private static final boolean[] OK = new boolean[8];
   private static final float[] CLIPPED_LINE = new float[4];

   private Draw() {
   }

   public static void line(class_332 ctx, float x1, float y1, float x2, float y2, int argb, int th) {
      line(ctx, x1, y1, x2, y2, argb, th, true);
   }

   public static void line(class_332 ctx, float x1, float y1, float x2, float y2, int argb, int th, boolean smooth) {
      if (clipLine(x1, y1, x2, y2, Projection.width(), Projection.height(), CLIPPED_LINE)) {
         x1 = CLIPPED_LINE[0];
         y1 = CLIPPED_LINE[1];
         x2 = CLIPPED_LINE[2];
         y2 = CLIPPED_LINE[3];
         float dx = x2 - x1;
         float dy = y2 - y1;
         float len = (float)Math.sqrt(dx * dx + dy * dy);
         if (!(len < 0.5F) && !(len > 20000.0F) && th >= 1) {
            Matrix3x2fStack m = ctx.method_51448();
            m.pushMatrix();
            m.translate(x1, y1);
            m.rotate((float)Math.atan2(dy, dx));
            int half = th / 2;
            if (smooth) {
               int alpha = argb >>> 24 & 0xFF;
               int fringe = argb & 16777215 | alpha * 58 / 255 << 24;
               ctx.method_25294(-1, -half - 1, (int)Math.ceil(len) + 1, th - half + 1, fringe);
            }

            ctx.method_25294(0, -half, (int)Math.ceil(len), th - half, argb);
            m.popMatrix();
         }
      }
   }

   public static boolean box(class_332 ctx, class_238 b, int argb, int th, boolean threeD) {
      return box(ctx, b, argb, th, threeD, true);
   }

   public static boolean box(class_332 ctx, class_238 b, int argb, int th, boolean threeD, boolean smooth) {
      return box(ctx, b.field_1323, b.field_1322, b.field_1321, b.field_1320, b.field_1325, b.field_1324, argb, th, threeD, smooth);
   }

   public static boolean box(class_332 ctx, double worldMinX, double worldMinY, double worldMinZ, double worldMaxX, double worldMaxY, double worldMaxZ, int argb, int th, boolean threeD, boolean smooth) {
      int any = 0;
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;
      boolean all = true;

      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? worldMinX : worldMaxX;
         double y = (i & 2) == 0 ? worldMinY : worldMaxY;
         double z = (i & 4) == 0 ? worldMinZ : worldMaxZ;
         OK[i] = Projection.project(x, y, z, C[i]);
         if (OK[i]) {
            any++;
            minX = Math.min(minX, (float)C[i][0]);
            maxX = Math.max(maxX, (float)C[i][0]);
            minY = Math.min(minY, (float)C[i][1]);
            maxY = Math.max(maxY, (float)C[i][1]);
         } else {
            all = false;
         }
      }

      if (any == 0) {
         return false;
      } else if (!(maxX < 0.0F) && !(maxY < 0.0F) && !(minX > Projection.width()) && !(minY > Projection.height())) {
         topX = Math.max(0.0F, Math.min((float)Projection.width(), (minX + maxX) / 2.0F));
         topY = Math.max(0.0F, Math.min((float)Projection.height(), minY));
         botY = Math.max(0.0F, Math.min((float)Projection.height(), maxY));
         if (!threeD) {
            if (!all) {
               return false;
            } else {
               int x0 = Math.max(0, Math.min(Projection.width(), Math.round(minX)));
               int y0 = Math.max(0, Math.min(Projection.height(), Math.round(minY)));
               int x1 = Math.max(0, Math.min(Projection.width(), Math.round(maxX)));
               int y1 = Math.max(0, Math.min(Projection.height(), Math.round(maxY)));
               if (x1 > x0 && y1 > y0) {
                  roundedOutline(ctx, x0, y0, x1, y1, argb, th, smooth);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            for (int ix = 0; ix < 8; ix++) {
               if (OK[ix]) {
                  for (int bit = 1; bit <= 4; bit <<= 1) {
                     if ((ix & bit) == 0) {
                        int j = ix | bit;
                        if (OK[j]) {
                           line(ctx, (float)C[ix][0], (float)C[ix][1], (float)C[j][0], (float)C[j][1], argb, th, smooth);
                        }
                     }
                  }
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static void roundedOutline(class_332 ctx, int x0, int y0, int x1, int y1, int argb, int thickness, boolean smooth) {
      float radius = Math.min(4.0F, Math.min((x1 - x0) * 0.2F, (y1 - y0) * 0.2F));
      if (radius < 1.5F) {
         int t = Math.max(1, Math.min(thickness, Math.min(x1 - x0, y1 - y0)));
         ctx.method_25294(x0, y0, x1, Math.min(y1, y0 + t), argb);
         ctx.method_25294(x0, Math.max(y0, y1 - t), x1, y1, argb);
         ctx.method_25294(x0, y0, Math.min(x1, x0 + t), y1, argb);
         ctx.method_25294(Math.max(x0, x1 - t), y0, x1, y1, argb);
         return;
      }
      float left = x0 + radius;
      float right = x1 - radius;
      float top = y0 + radius;
      float bottom = y1 - radius;
      line(ctx, left, y0, right, y0, argb, thickness, smooth);
      line(ctx, right, y1, left, y1, argb, thickness, smooth);
      line(ctx, x0, top, x0, bottom, argb, thickness, smooth);
      line(ctx, x1, bottom, x1, top, argb, thickness, smooth);
      for (int corner = 0; corner < 4; corner++) {
         float cx = corner == 0 || corner == 3 ? left : right;
         float cy = corner < 2 ? top : bottom;
         float start = switch (corner) {
            case 0 -> 180.0F;
            case 1 -> 270.0F;
            case 2 -> 0.0F;
            default -> 90.0F;
         };
         float previousX = cx + (float)Math.cos(Math.toRadians(start)) * radius;
         float previousY = cy + (float)Math.sin(Math.toRadians(start)) * radius;
         for (int step = 1; step <= 6; step++) {
            float angle = (float)Math.toRadians(start + step * 90.0F / 6.0F);
            float nextX = cx + (float)Math.cos(angle) * radius;
            float nextY = cy + (float)Math.sin(angle) * radius;
            line(ctx, previousX, previousY, nextX, nextY, argb, thickness, smooth);
            previousX = nextX;
            previousY = nextY;
         }
      }
   }

   public static boolean fillProjectedBounds(class_332 ctx, class_238 b, int argb) {
      int any = 0;
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;

      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? b.field_1323 : b.field_1320;
         double y = (i & 2) == 0 ? b.field_1322 : b.field_1325;
         double z = (i & 4) == 0 ? b.field_1321 : b.field_1324;
         if (Projection.project(x, y, z, C[i])) {
            any++;
            minX = Math.min(minX, (float)C[i][0]);
            maxX = Math.max(maxX, (float)C[i][0]);
            minY = Math.min(minY, (float)C[i][1]);
            maxY = Math.max(maxY, (float)C[i][1]);
         }
      }

      if (any != 0 && !(maxX < 0.0F) && !(maxY < 0.0F) && !(minX > Projection.width()) && !(minY > Projection.height())) {
         int x0 = Math.max(0, Math.min(Projection.width(), (int)Math.floor(minX)));
         int y0 = Math.max(0, Math.min(Projection.height(), (int)Math.floor(minY)));
         int x1 = Math.max(0, Math.min(Projection.width(), (int)Math.ceil(maxX)));
         int y1 = Math.max(0, Math.min(Projection.height(), (int)Math.ceil(maxY)));
         if (x1 > x0 && y1 > y0) {
            ctx.method_25294(x0, y0, x1, y1, argb);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean clipLine(float x0, float y0, float x1, float y1, int width, int height, float[] out) {
      if (width <= 0 || height <= 0 || !Float.isFinite(x0) || !Float.isFinite(y0)
         || !Float.isFinite(x1) || !Float.isFinite(y1)) {
         return false;
      }
      float dx = x1 - x0;
      float dy = y1 - y0;
      float u0 = 0.0F;
      float u1 = 1.0F;
      for (int i = 0; i < 4; i++) {
         float p;
         float q;
         switch (i) {
            case 0 -> { p = -dx; q = x0; }
            case 1 -> { p = dx; q = width - x0; }
            case 2 -> { p = -dy; q = y0; }
            default -> { p = dy; q = height - y0; }
         }
         if (Math.abs(p) < 1.0E-6F) {
            if (q < 0.0F) return false;
         } else {
            float r = q / p;
            if (p < 0.0F) {
               if (r > u1) return false;
               u0 = Math.max(u0, r);
            } else {
               if (r < u0) return false;
               u1 = Math.min(u1, r);
            }
         }
      }
      out[0] = x0 + u0 * dx;
      out[1] = y0 + u0 * dy;
      out[2] = x0 + u1 * dx;
      out[3] = y0 + u1 * dy;
      return true;
   }

   public static void centeredText(class_332 ctx, class_327 tr, String s, float cx, float y, int argb) {
      ctx.method_51433(tr, s, Math.round(cx - tr.method_1727(s) / 2.0F), Math.round(y), argb, true);
   }
}

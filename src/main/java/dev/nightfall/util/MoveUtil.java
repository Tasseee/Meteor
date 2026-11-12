package dev.nightfall.util;

import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_315;

public final class MoveUtil {
   private MoveUtil() {
   }

   public static class_243 direction() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null) {
         return class_243.field_1353;
      } else {
         class_315 o = mc.field_1690;
         float f = (o.field_1894.method_1434() ? 1 : 0) - (o.field_1881.method_1434() ? 1 : 0);
         float s = (o.field_1913.method_1434() ? 1 : 0) - (o.field_1849.method_1434() ? 1 : 0);
         if (f == 0.0F && s == 0.0F) {
            return class_243.field_1353;
         } else {
            double yaw = Math.toRadians(mc.field_1724.method_36454());
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            double x = -sin * f + cos * s;
            double z = cos * f + sin * s;
            double len = Math.sqrt(x * x + z * z);
            return new class_243(x / len, 0.0, z / len);
         }
      }
   }

   public static boolean isMoving() {
      return direction() != class_243.field_1353;
   }
}

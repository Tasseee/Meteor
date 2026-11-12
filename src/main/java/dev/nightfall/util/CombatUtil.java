package dev.nightfall.util;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FriendManager;
import dev.nightfall.modules.KillAura;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_746;

public final class CombatUtil {
   private CombatUtil() {
   }

   public static double explosionDamage(class_243 center, class_1309 e, double power) {
      double q = power * 2.0;
      double d = e.method_73189().method_1022(center) / q;
      if (d > 1.0) {
         return 0.0;
      } else {
         double impact = 1.0 - d;
         double dmg = (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;
         double armor = e.method_6096();
         double f = Math.min(20.0, Math.max(armor / 5.0, armor - dmg / 2.0)) / 25.0;
         return dmg * (1.0 - f);
      }
   }

   public static double effectiveHealth(class_1309 e) {
      return e.method_6032() + e.method_6067();
   }

   public static boolean isFriend(class_1657 p) {
      String name = p.method_5477().getString();
      return ModuleManager.get(KillAura.class).friends.has(name) || ModuleManager.get(FriendManager.class).isFriend(name);
   }

   public static class_1657 nearestPlayer(double range) {
      class_310 mc = class_310.method_1551();
      class_746 me = mc.field_1724;
      if (me != null && mc.field_1687 != null) {
         class_1657 best = null;
         double bd = range * range;

         for (class_1657 p : mc.field_1687.method_18456()) {
            if (p != me && p.method_5805() && !p.method_7325() && !isFriend(p)) {
               double d = p.method_5858(me);
               if (d < bd) {
                  bd = d;
                  best = p;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }
}

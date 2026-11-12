package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1311;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class KillAura extends Module {
   private final NumberSetting reach = this.add(new NumberSetting("Reichweite", 3.2, 1.0, 6.0, 0.1));
   private final NumberSetting fov = this.add(new NumberSetting("FOV-Limit", 180.0, 30.0, 360.0, 10.0));
   private final ModeSetting priority = this.add(new ModeSetting("Prioritaet", "Distanz", "Distanz", "Health"));
   private final ModeSetting rotate = this.add(new ModeSetting("Rotation", "Aus", "Aus", "Sichtbar"));
   private final BoolSetting cooldown = this.add(new BoolSetting("Cooldown pruefen", true));
   private final BoolSetting walls = this.add(new BoolSetting("Durch Waende", false));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting hostile = this.add(new BoolSetting("Feindliche Mobs", true));
   private final BoolSetting passive = this.add(new BoolSetting("Passive Mobs", false));
   public final ListSetting friends = this.add(new ListSetting("Freunde"));
   public class_1309 target;

   public KillAura() {
      super("KillAura", "Greift Ziele in Reichweite automatisch an (Cooldown, Freundesliste).", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.target = null;
   }

   private double reachDist(class_746 p, class_1297 e) {
      class_238 b = e.method_5829();
      class_243 eye = p.method_33571();
      double x = Math.max(b.field_1323, Math.min(eye.field_1352, b.field_1320));
      double y = Math.max(b.field_1322, Math.min(eye.field_1351, b.field_1325));
      double z = Math.max(b.field_1321, Math.min(eye.field_1350, b.field_1324));
      return eye.method_1022(new class_243(x, y, z));
   }

   private boolean valid(class_746 p, class_1309 e) {
      if (e != p && e.method_5805() && !e.method_7325()) {
         if (e instanceof class_1657 pe) {
            if (!this.players.get() || this.friends.has(pe.method_5477().getString())) {
               return false;
            }
         } else if (e.method_5864().method_5891() == class_1311.field_6302) {
            if (!this.hostile.get()) {
               return false;
            }
         } else if (!this.passive.get()) {
            return false;
         }

         if (this.reachDist(p, e) > this.reach.get()) {
            return false;
         } else if (!this.walls.get() && !p.method_6057(e)) {
            return false;
         } else {
            if (this.fov.get() < 360.0) {
               double dx = e.method_23317() - p.method_23317();
               double dz = e.method_23321() - p.method_23321();
               double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
               double diff = Math.abs(((yaw - p.method_36454()) % 360.0 + 540.0) % 360.0 - 180.0);
               if (diff > this.fov.get() / 2.0) {
                  return false;
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   @Override
   public void onTick() {
      this.target = null;
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && mc.field_1761 != null && mc.field_1755 == null && p.method_5805()) {
         class_1309 best = null;
         double bestKey = Double.MAX_VALUE;

         for (class_1297 e : mc.field_1687.method_18112()) {
            if (e instanceof class_1309 le && this.valid(p, le)) {
               double key = this.priority.is("Health") ? le.method_6032() : this.reachDist(p, le);
               if (key < bestKey) {
                  bestKey = key;
                  best = le;
               }
            }
         }

         if (best != null) {
            this.target = best;
            if (this.rotate.is("Sichtbar")) {
               class_238 b = best.method_5829();
               class_243 eye = p.method_33571();
               double dx = (b.field_1323 + b.field_1320) / 2.0 - eye.field_1352;
               double dy = (b.field_1322 + b.field_1325) / 2.0 - eye.field_1351;
               double dz = (b.field_1321 + b.field_1324) / 2.0 - eye.field_1350;
               p.method_36456((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
               p.method_36457((float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))));
            }

            if (!this.cooldown.get() || !(p.method_7261(0.5F) < 0.95F)) {
               mc.field_1761.method_2918(p, best);
               p.method_6104(class_1268.field_5808);
            }
         }
      }
   }
}

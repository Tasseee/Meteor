package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_243;

public final class BoatFly extends Module {
   private final NumberSetting horizontal = this.add(new NumberSetting("Geschwindigkeit", 1.3, 0.1, 4.0, 0.1));
   private final NumberSetting vertical = this.add(new NumberSetting("Vertikal", 0.9, 0.1, 3.0, 0.1));
   private class_1297 controlled;
   private boolean previousNoGravity;

   public BoatFly() {
      super("BoatFly", "Freie Bootsbewegung in alle Richtungen, auch unter Wasser.", Category.MOVEMENT);
   }

   @Override
   protected void onEnable() {
      this.captureBoat();
   }

   private void captureBoat() {
      if (mc.field_1724 != null && mc.field_1724.method_5765()) {
         class_1297 vehicle = mc.field_1724.method_5854();
         if (vehicle != null && vehicle.method_5864().toString().toLowerCase().contains("boat")) {
            if (this.controlled != vehicle) {
               this.restoreBoat();
               this.controlled = vehicle;
               this.previousNoGravity = vehicle.method_5740();
            }

            vehicle.method_5875(true);
         }
      }
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (!mc.field_1724.method_5765()) {
            this.restoreBoat();
         } else {
            class_1297 boat = mc.field_1724.method_5854();
            if (boat != null && boat.method_5864().toString().toLowerCase().contains("boat")) {
               this.captureBoat();
               float yaw = mc.field_1724.method_36454();
               float pitch = mc.field_1724.method_36455();
               double yr = Math.toRadians(yaw);
               double pr = Math.toRadians(pitch);
               class_243 forward = new class_243(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
               class_243 right = new class_243(Math.cos(yr), 0.0, Math.sin(yr));
               class_243 movement = class_243.field_1353;
               if (mc.field_1690.field_1894.method_1434()) {
                  movement = movement.method_1019(forward);
               }

               if (mc.field_1690.field_1881.method_1434()) {
                  movement = movement.method_1020(forward);
               }

               if (mc.field_1690.field_1849.method_1434()) {
                  movement = movement.method_1019(right);
               }

               if (mc.field_1690.field_1913.method_1434()) {
                  movement = movement.method_1020(right);
               }

               if (mc.field_1690.field_1903.method_1434()) {
                  movement = movement.method_1031(0.0, 1.0, 0.0);
               }

               if (mc.field_1690.field_1832.method_1434()) {
                  movement = movement.method_1031(0.0, -1.0, 0.0);
               }

               if (movement.method_1027() > 0.0) {
                  class_243 normalized = movement.method_1029();
                  boat.method_18800(
                     normalized.field_1352 * this.horizontal.get(), normalized.field_1351 * this.vertical.get(), normalized.field_1350 * this.horizontal.get()
                  );
               } else {
                  boat.method_18799(class_243.field_1353);
               }

               boat.method_5875(true);
            } else {
               this.restoreBoat();
            }
         }
      }
   }

   private void restoreBoat() {
      if (this.controlled != null) {
         this.controlled.method_5875(this.previousNoGravity);
         this.controlled = null;
      }
   }

   @Override
   protected void onDisable() {
      this.restoreBoat();
   }
}

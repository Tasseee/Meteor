package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_243;

public final class BoatFly extends Module {
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 20.0, 1.0, 200.0, 1.0));
   private final NumberSetting verticalSpeed = this.add(new NumberSetting("Vertikalgeschwindigkeit", 10.0, 0.0, 100.0, 1.0));
   private final BoolSetting noclip = this.add(new BoolSetting("Noclip", true));
   private final BoolSetting hover = this.add(new BoolSetting("Hover", true));
   private final BoolSetting lockYaw = this.add(new BoolSetting("Blickrichtung sperren", true));

   private class_1297 lastBoat;
   private boolean previousNoGravity;
   private boolean previousNoClip;

   public BoatFly() {
      super("BoatFly", "Boot-Flug mit einstellbarer Geschwindigkeit, Hover und optionalem Noclip.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1690 == null) {
         this.restoreBoat();
         return;
      }

      class_1297 boat = mc.field_1724.method_5854();
      if (!mc.field_1724.method_5765() || !this.isBoat(boat)) {
         this.restoreBoat();
         return;
      }

      this.captureBoat(boat);

      if (this.lockYaw.get()) {
         boat.method_36456(mc.field_1724.method_36454());
      }

      boat.method_5875(true);
      boat.field_5960 = this.noclip.get();

      double yaw = Math.toRadians(mc.field_1724.method_36454());
      double forward = 0.0;
      double side = 0.0;

      if (mc.field_1690.field_1894.method_1434()) forward += 1.0;
      if (mc.field_1690.field_1881.method_1434()) forward -= 1.0;
      if (mc.field_1690.field_1913.method_1434()) side += 1.0;
      if (mc.field_1690.field_1849.method_1434()) side -= 1.0;

      double perTickSpeed = this.speed.get() / 20.0;
      double vx = 0.0;
      double vz = 0.0;

      if (forward != 0.0 || side != 0.0) {
         double length = Math.sqrt(forward * forward + side * side);
         forward /= length;
         side /= length;

         vx = (-Math.sin(yaw) * forward + Math.cos(yaw) * side) * perTickSpeed;
         vz = (Math.cos(yaw) * forward + Math.sin(yaw) * side) * perTickSpeed;
      }

      double vy = 0.0;
      if (mc.field_1690.field_1903.method_1434()) vy += this.verticalSpeed.get() / 20.0;
      if (mc.field_1690.field_1832.method_1434()) vy -= this.verticalSpeed.get() / 20.0;

      if (!this.hover.get() && vy == 0.0) {
         vy = boat.method_18798().field_1351;
      }

      boat.method_18800(vx, vy, vz);
   }

   @Override
   public void onWorldLeave() {
      this.restoreBoat();
   }

   @Override
   protected void onDisable() {
      this.restoreBoat();
   }

   private boolean isBoat(class_1297 entity) {
      return entity != null && entity.method_5864().toString().toLowerCase().contains("boat");
   }

   private void captureBoat(class_1297 boat) {
      if (this.lastBoat == boat) return;

      this.restoreBoat();
      this.lastBoat = boat;
      this.previousNoGravity = boat.method_5740();
      this.previousNoClip = boat.field_5960;
   }

   private void restoreBoat() {
      if (this.lastBoat == null) return;

      this.lastBoat.field_5960 = this.previousNoClip;
      this.lastBoat.method_5875(this.previousNoGravity);
      this.lastBoat = null;
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_243;
import net.minecraft.class_638;
import net.minecraft.class_332;

/**
 * Independent, non-rendering scan-position provider used by ChunkMemory.
 *
 * It stores only a client-side position. It does not create or register an
 * Entity, replace the render camera, move the player, send packets, or request
 * chunks from the server.
 */
public final class BackgroundFreecam extends Module {
   private static boolean active;
   private static class_638 cameraWorld;
   private static class_243 cameraPosition;
   private static double phase;

   private final NumberSetting orbitRadius = this.add(
      new NumberSetting("Flugradius (Bloecke)", 48.0, 8.0, 192.0, 2.0)
   );
   private final NumberSetting flightSpeed = this.add(
      new NumberSetting("Flugtempo (Bloecke/Tick)", 0.45, 0.05, 4.0, 0.05)
   );
   private final NumberSetting height = this.add(
      new NumberSetting("Flughoehe ueber Spieler", 12.0, 2.0, 64.0, 1.0)
   );
   private final NumberSetting verticalSweep = this.add(
      new NumberSetting("Vertikaler Scanbereich", 6.0, 0.0, 32.0, 1.0)
   );

   public BackgroundFreecam() {
      super(
         "BackgroundFreecam",
         "Eigene automatische Hintergrundkamera als Scan-Zentrum fuer ChunkMemory; veraendert nicht die normale Sicht.",
         Category.WORLD
      );
   }

   public static boolean isActive() {
      return active && cameraPosition != null && cameraWorld == mc.field_1687 && mc.field_1724 != null;
   }

   public static class_243 cameraPosition() {
      return isActive() ? cameraPosition : null;
   }

   @Override
   protected void onEnable() {
      class_638 world = mc.field_1687;
      if (world == null || mc.field_1724 == null) {
         active = false;
         cameraWorld = null;
         cameraPosition = null;
         return;
      }

      cameraWorld = world;
      phase = 0.0;
      active = true;
      updatePosition();
   }

   @Override
   protected void onDisable() {
      active = false;
      cameraWorld = null;
      cameraPosition = null;
      phase = 0.0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         active = false;
         cameraWorld = null;
         cameraPosition = null;
         return;
      }

      // Rebind the position provider after a world transition.
      if (mc.field_1687 != cameraWorld || cameraPosition == null) {
         cameraWorld = mc.field_1687;
         phase = 0.0;
         active = true;
      }

      updatePosition();
   }

   private void updatePosition() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         cameraPosition = null;
         active = false;
         return;
      }

      double radius = Math.max(8.0, orbitRadius.get());
      phase += flightSpeed.get() / radius;
      if (phase >= Math.PI * 2.0) {
         phase -= Math.PI * 2.0;
      }

      // A smooth orbit with gentle radial and vertical sweeps.
      double currentRadius = radius * (0.75 + 0.25 * Math.sin(phase * 0.22));
      double playerX = mc.field_1724.method_23317();
      double playerY = mc.field_1724.method_33571().field_1351;
      double playerZ = mc.field_1724.method_23321();

      double x = playerX + Math.cos(phase) * currentRadius;
      double z = playerZ + Math.sin(phase) * currentRadius;
      double y = playerY + height.get() + Math.sin(phase * 0.5) * verticalSweep.get();

      // class_243 is immutable, so consumers can safely share this per-tick value.
      cameraPosition = new class_243(x, y, z);
      cameraWorld = mc.field_1687;
      active = true;
   }

   @Override
   public void renderHud(class_332 context) {
      class_243 position = cameraPosition();
      if (!this.isEnabled() || position == null) return;

      String title = "BACKGROUND FREECAM";
      String info = "X " + Math.round(position.field_1352 * 10.0) / 10.0
         + "  Y " + Math.round(position.field_1351 * 10.0) / 10.0
         + "  Z " + Math.round(position.field_1350 * 10.0) / 10.0;
      int width = Math.max(mc.field_1772.method_1727(title), mc.field_1772.method_1727(info)) + 18;
      int x = 8;
      int y = 4;
      context.method_25294(x, y, x + width, y + 34, 0x90000000);
      context.method_25294(x, y, x + width, y + 2, 0xFF39FFB6);
      context.method_51433(mc.field_1772, title, x + 8, y + 5, 0xFF39FFB6, true);
      context.method_51433(mc.field_1772, info, x + 8, y + 18, 0xFFFFFFFF, true);
   }

   @Override
   public void onWorldLeave() {
      active = false;
      cameraWorld = null;
      cameraPosition = null;
      phase = 0.0;
   }
}

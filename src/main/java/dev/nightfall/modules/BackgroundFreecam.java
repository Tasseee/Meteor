package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_243;
import net.minecraft.class_638;
import net.minecraft.class_332;

/**
 * Virtual scan-position provider for ChunkMemory.
 *
 * This module intentionally does not create an Entity, replace Minecraft's
 * render camera, move the player, send packets, or request chunks. It only
 * exposes a client-side position around the local player.
 */
public final class BackgroundFreecam extends Module {
   private static boolean active;
   private static boolean paused;
   private static String currentMode = "Orbit";
   private static class_638 cameraWorld;
   private static class_243 cameraPosition;

   private double phase;
   private double rasterDistance;
   private double spiralProgress;
   private int spiralDirection = 1;

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
   private final ModeSetting movementMode = this.add(
      new ModeSetting("Bewegungsmuster", "Orbit", "Orbit", "Spirale", "Raster", "Acht")
   );
   private final BoolSetting pauseMovement = this.add(
      new BoolSetting("Bewegung pausieren", false)
   );

   public BackgroundFreecam() {
      super(
         "BackgroundFreecam",
         "Virtuelles Scan-Zentrum fuer ChunkMemory; veraendert weder Spieler noch normale Kamera.",
         Category.WORLD
      );
   }

   public static boolean isActive() {
      return active && cameraPosition != null && cameraWorld == mc.field_1687 && mc.field_1724 != null;
   }

   public static boolean isPaused() {
      return isActive() && paused;
   }

   public static String movementModeName() {
      return currentMode;
   }

   public static class_243 cameraPosition() {
      return isActive() ? cameraPosition : null;
   }

   public void setPaused(boolean value) {
      this.pauseMovement.set(value);
      paused = value;
   }

   public boolean isMovementPaused() {
      return this.pauseMovement.get();
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

      this.resetPath();
      cameraWorld = world;
      active = true;
      paused = this.pauseMovement.get();
      currentMode = this.movementMode.get();
      this.updatePosition();
   }

   @Override
   protected void onDisable() {
      active = false;
      paused = false;
      cameraWorld = null;
      cameraPosition = null;
      this.resetPath();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         active = false;
         cameraWorld = null;
         cameraPosition = null;
         return;
      }

      // Rebind safely after a dimension/server transition.
      if (mc.field_1687 != cameraWorld || cameraPosition == null) {
         cameraWorld = mc.field_1687;
         this.resetPath();
         active = true;
         this.updatePosition();
      }

      paused = this.pauseMovement.get();
      currentMode = this.movementMode.get();
      if (!paused) {
         this.updatePosition();
      }
   }

   private void resetPath() {
      this.phase = 0.0;
      this.rasterDistance = 0.0;
      this.spiralProgress = 0.0;
      this.spiralDirection = 1;
   }

   private void updatePosition() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         cameraPosition = null;
         active = false;
         return;
      }

      double radius = Math.max(8.0, this.orbitRadius.get());
      double speed = this.flightSpeed.get();
      this.phase += speed / radius;
      if (this.phase >= Math.PI * 2.0) {
         this.phase -= Math.PI * 2.0;
      }

      double xOffset;
      double zOffset;
      switch (this.movementMode.get()) {
         case "Spirale" -> {
            // Sweep smoothly from the inner radius to the outside and back.
            this.spiralProgress += this.spiralDirection * speed / Math.max(32.0, radius * 4.0);
            if (this.spiralProgress >= 1.0) {
               this.spiralProgress = 1.0;
               this.spiralDirection = -1;
            } else if (this.spiralProgress <= 0.0) {
               this.spiralProgress = 0.0;
               this.spiralDirection = 1;
            }
            double spiralRadius = 8.0 + (radius - 8.0) * this.spiralProgress;
            xOffset = Math.cos(this.phase) * spiralRadius;
            zOffset = Math.sin(this.phase) * spiralRadius;
         }
         case "Raster" -> {
            // Traverse the four sides of a square instead of repeatedly
            // circling the same ring of chunks.
            double side = radius * 2.0;
            double perimeter = side * 4.0;
            this.rasterDistance = (this.rasterDistance + speed) % perimeter;
            double p = this.rasterDistance;
            if (p < side) {
               xOffset = -radius + p;
               zOffset = -radius;
            } else if (p < side * 2.0) {
               xOffset = radius;
               zOffset = -radius + (p - side);
            } else if (p < side * 3.0) {
               xOffset = radius - (p - side * 2.0);
               zOffset = radius;
            } else {
               xOffset = -radius;
               zOffset = radius - (p - side * 3.0);
            }
         }
         case "Acht" -> {
            xOffset = Math.sin(this.phase) * radius;
            zOffset = Math.sin(this.phase * 2.0) * radius * 0.5;
         }
         default -> {
            double currentRadius = radius * (0.75 + 0.25 * Math.sin(this.phase * 0.22));
            xOffset = Math.cos(this.phase) * currentRadius;
            zOffset = Math.sin(this.phase) * currentRadius;
         }
      }

      double playerX = mc.field_1724.method_23317();
      double playerY = mc.field_1724.method_33571().field_1351;
      double playerZ = mc.field_1724.method_23321();
      double yOffset = this.height.get() + Math.sin(this.phase * 0.5) * this.verticalSweep.get();

      cameraPosition = new class_243(playerX + xOffset, playerY + yOffset, playerZ + zOffset);
      cameraWorld = mc.field_1687;
      active = true;
   }

   @Override
   public void renderHud(class_332 context) {
      class_243 position = cameraPosition();
      if (!this.isEnabled() || position == null) return;

      String title = "BACKGROUND FREECAM  /  " + this.movementMode.get().toUpperCase(java.util.Locale.ROOT);
      String state = this.pauseMovement.get() ? "PAUSIERT" : "AKTIV";
      String info = "X " + Math.round(position.field_1352 * 10.0) / 10.0
         + "  Y " + Math.round(position.field_1351 * 10.0) / 10.0
         + "  Z " + Math.round(position.field_1350 * 10.0) / 10.0;
      int width = Math.max(mc.field_1772.method_1727(title), mc.field_1772.method_1727(info + "  |  " + state)) + 18;
      int x = 8;
      int y = 4;
      context.method_25294(x, y, x + width, y + 34, 0x90000000);
      context.method_25294(x, y, x + width, y + 2, this.pauseMovement.get() ? 0xFFFFB347 : 0xFF39FFB6);
      context.method_51433(mc.field_1772, title, x + 8, y + 5, 0xFF39FFB6, true);
      context.method_51433(mc.field_1772, info + "  |  " + state, x + 8, y + 18, 0xFFFFFFFF, true);
   }

   @Override
   public void onWorldLeave() {
      active = false;
      cameraWorld = null;
      cameraPosition = null;
      this.resetPath();
   }
}

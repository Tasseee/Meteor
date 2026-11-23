package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1299;
import net.minecraft.class_1531;
import net.minecraft.class_243;
import net.minecraft.class_638;

/**
 * Independent, non-rendering scout camera used only as ChunkMemory's scan origin.
 *
 * The proxy is never added to the world and never replaces Minecraft's active
 * camera entity. Its position is used for lookup-only reads of chunks already
 * present in the client.
 */
public final class BackgroundFreecam extends Module {
   private static boolean active;
   private static class_1531 cameraProxy;
   private static class_638 cameraWorld;
   private static double cameraX;
   private static double cameraY;
   private static double cameraZ;
   private static double phase;
   private static double eyeOffset;

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
      return active && cameraProxy != null && cameraWorld == mc.field_1687;
   }

   public static class_243 cameraPosition() {
      return isActive() ? new class_243(cameraX, cameraY, cameraZ) : null;
   }

   @Override
   protected void onEnable() {
      class_638 world = mc.field_1687;
      if (world == null || mc.field_1724 == null) {
         active = false;
         cameraProxy = null;
         cameraWorld = null;
         return;
      }

      cameraWorld = world;
      cameraProxy = new class_1531(class_1299.field_6131, world);
      eyeOffset = cameraProxy.method_5751() - cameraProxy.method_23318();
      phase = 0.0;
      active = true;
      updatePosition();
   }

   @Override
   protected void onDisable() {
      active = false;
      cameraProxy = null;
      cameraWorld = null;
      phase = 0.0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         active = false;
         cameraProxy = null;
         cameraWorld = null;
         return;
      }

      // Recreate the independent proxy after a world transition even if the
      // module itself remained enabled across the disconnect callback.
      if (mc.field_1687 != cameraWorld || cameraProxy == null) {
         cameraWorld = mc.field_1687;
         cameraProxy = new class_1531(class_1299.field_6131, cameraWorld);
         eyeOffset = cameraProxy.method_5751() - cameraProxy.method_23318();
         phase = 0.0;
         active = true;
      }

      updatePosition();
   }

   private void updatePosition() {
      if (mc.field_1724 == null || cameraProxy == null) return;

      double radius = Math.max(8.0, orbitRadius.get());
      double angularStep = flightSpeed.get() / radius;
      phase += angularStep;
      if (phase >= Math.PI * 2.0) phase -= Math.PI * 2.0;

      // A slow radial sweep plus a gentle vertical wave makes this a 3D patrol,
      // while the player's actual position and active render camera stay untouched.
      double radialFactor = 0.75 + 0.25 * Math.sin(phase * 0.22);
      double currentRadius = radius * radialFactor;
      double playerX = mc.field_1724.method_23317();
      double playerY = mc.field_1724.method_33571().field_1351;
      double playerZ = mc.field_1724.method_23321();

      cameraX = playerX + Math.cos(phase) * currentRadius;
      cameraZ = playerZ + Math.sin(phase) * currentRadius;
      cameraY = playerY + height.get() + Math.sin(phase * 0.5) * verticalSweep.get();

      float yaw = (float)Math.toDegrees(Math.atan2(playerX - cameraX, playerZ - cameraZ));
      double horizontalDistance = Math.sqrt(
         (playerX - cameraX) * (playerX - cameraX)
            + (playerZ - cameraZ) * (playerZ - cameraZ)
      );
      float pitch = (float)-Math.toDegrees(Math.atan2(playerY - cameraY, horizontalDistance));
      cameraProxy.method_36456(yaw);
      cameraProxy.method_36457(pitch);
      cameraProxy.method_5814(cameraX, cameraY - eyeOffset, cameraZ);
   }

   @Override
   public void onWorldLeave() {
      active = false;
      cameraProxy = null;
      cameraWorld = null;
      phase = 0.0;
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_310;
import org.lwjgl.glfw.GLFW;

public final class Freecam extends Module {
   private static class_243 cameraPosition = class_243.field_1353;
   private static class_243 previousCameraPosition = class_243.field_1353;
   private static float previousCameraYaw;
   private static float previousCameraPitch;
   private long lastMoveNanos;
   private static class_243 cameraVelocity = class_243.field_1353;
   private static boolean active;
   private static float cameraYaw;
   private static float cameraPitch;
   private class_243 playerStart = class_243.field_1353;
   private float savedYaw;
   private float savedPitch;
   private double lastMouseX;
   private double lastMouseY;
   private boolean mouseInitialized;
   private final double[] cursorX = new double[1];
   private final double[] cursorY = new double[1];
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 0.6, 0.1, 3.0, 0.05));
   private final NumberSetting smoothing = this.add(new NumberSetting("Gleiten", 0.25, 0.03, 1.0, 0.01));
   private final BoolSetting stopOnDamage = this.add(new BoolSetting("Stopp bei Schaden", true));
   private final BoolSetting hud = this.add(new BoolSetting("Freecam-Anzeige", true));
   private final NumberSetting sensitivity = this.add(new NumberSetting("Kamera-Sensitivitaet", 0.15, 0.03, 0.5, 0.01));
   private final BoolSetting invertVertical = this.add(new BoolSetting("Vertikale Maus invertieren", true));

   public Freecam() {
      super("Freecam", "Bewegt und dreht die Kamera unabhaengig vom Spieler durch die Welt.", Category.MOVEMENT);
   }

   public static boolean isActive() {
      return active;
   }

   public static class_243 cameraPosition() {
      return cameraPosition;
   }

   public static float cameraYaw() {
      return cameraYaw;
   }

   public static float cameraPitch() {
      return cameraPitch;
   }

   public static class_243 interpolatedPosition(float tickDelta) {
      return previousCameraPosition.method_35590(cameraPosition, Math.max(0.0F, Math.min(1.0F, tickDelta)));
   }

   public static float interpolatedYaw(float tickDelta) {
      float difference = (cameraYaw - previousCameraYaw) % 360.0F;
      if (difference >= 180.0F) {
         difference -= 360.0F;
      }

      if (difference < -180.0F) {
         difference += 360.0F;
      }

      return previousCameraYaw + difference * Math.max(0.0F, Math.min(1.0F, tickDelta));
   }

   public static float interpolatedPitch(float tickDelta) {
      return previousCameraPitch + (cameraPitch - previousCameraPitch) * Math.max(0.0F, Math.min(1.0F, tickDelta));
   }

   private boolean activateIfReady() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         try {
            FreeLook look = ModuleManager.get(FreeLook.class);
            if (look.isEnabled()) {
               look.setEnabled(false);
            }
         } catch (IllegalStateException var2) {
         }

         this.playerStart = mc.field_1724.method_73189();
         this.savedYaw = mc.field_1724.method_36454();
         this.savedPitch = mc.field_1724.method_36455();
         cameraYaw = this.savedYaw;
         cameraPitch = this.savedPitch;
         previousCameraYaw = cameraYaw;
         previousCameraPitch = cameraPitch;
         class_243 lookDirection = new class_243(-Math.sin(Math.toRadians(cameraYaw)), 0.0, Math.cos(Math.toRadians(cameraYaw)));
         cameraPosition = mc.field_1724.method_33571().method_1020(lookDirection.method_1021(2.5));
         previousCameraPosition = cameraPosition;
         this.lastMoveNanos = System.nanoTime();
         cameraVelocity = class_243.field_1353;
         this.mouseInitialized = false;
         active = true;
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected void onEnable() {
      this.activateIfReady();
   }

   @Override
   public void onTick() {
      if (!active) {
         this.activateIfReady();
      } else if (mc.field_1724 != null && mc.field_1687 != null && mc.method_22683() != null) {
         if (this.stopOnDamage.get() && mc.field_1724.field_6235 > 0) {
            this.setEnabled(false);
            return;
         }

         long window = mc.method_22683().method_4490();
         previousCameraPosition = cameraPosition;
         previousCameraYaw = cameraYaw;
         previousCameraPitch = cameraPitch;
         long now = System.nanoTime();
         double frameSeconds = Math.max(0.0, Math.min(0.05, (now - this.lastMoveNanos) / 1.0E9));
         this.lastMoveNanos = now;
         GLFW.glfwGetCursorPos(window, this.cursorX, this.cursorY);
         if (this.mouseInitialized) {
            double dx = Math.max(-250.0, Math.min(250.0, this.cursorX[0] - this.lastMouseX));
            double dy = Math.max(-250.0, Math.min(250.0, this.cursorY[0] - this.lastMouseY));
            // Invert both mouse axes for Freecam: right increases yaw, down decreases pitch.
            cameraYaw = cameraYaw + (float)(dx * this.sensitivity.get());
            cameraPitch = cameraPitch - (float)(dy * this.sensitivity.get());
            cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
         }

         this.lastMouseX = this.cursorX[0];
         this.lastMouseY = this.cursorY[0];
         this.mouseInitialized = true;
         float yaw = (float)Math.toRadians(cameraYaw);
         float pitch = (float)Math.toRadians(cameraPitch);
         class_243 forward = new class_243(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
         class_243 right = new class_243(Math.cos(yaw), 0.0, Math.sin(yaw));
         class_243 move = class_243.field_1353;
         if (GLFW.glfwGetKey(window, 87) == 1) {
            move = move.method_1019(forward);
         }

         if (GLFW.glfwGetKey(window, 83) == 1) {
            move = move.method_1020(forward);
         }

         if (GLFW.glfwGetKey(window, 68) == 1) {
            move = move.method_1019(right);
         }

         if (GLFW.glfwGetKey(window, 65) == 1) {
            move = move.method_1020(right);
         }

         if (GLFW.glfwGetKey(window, 32) == 1) {
            move = move.method_1031(0.0, 1.0, 0.0);
         }

         if (GLFW.glfwGetKey(window, 340) == 1) {
            move = move.method_1031(0.0, -1.0, 0.0);
         }

         class_243 targetVelocity = move.method_1027() > 0.0
            ? move.method_1029().method_1021(this.speed.get() * 20.0)
            : class_243.field_1353;
         double smooth = this.smoothing.get();
         cameraVelocity = cameraVelocity.method_1019(targetVelocity.method_1020(cameraVelocity).method_1021(smooth));
         cameraPosition = cameraPosition.method_1019(cameraVelocity.method_1021(frameSeconds));

         if (mc.field_1724.method_73189().method_1025(this.playerStart) > 1.0E-8) {
            mc.field_1724.method_33574(this.playerStart);
         }

         mc.field_1724.method_18799(class_243.field_1353);
         mc.field_1724.method_36456(this.savedYaw);
         mc.field_1724.method_36457(this.savedPitch);
      }
   }

   @Override
   public void onWorldLeave() {
      active = false;
      this.mouseInitialized = false;
      cameraPosition = class_243.field_1353;
      previousCameraPosition = class_243.field_1353;
      this.lastMoveNanos = 0L;
      cameraVelocity = class_243.field_1353;
   }

   @Override
   protected void onDisable() {
      boolean wasActive = active;
      active = false;
      if (wasActive && mc.field_1724 != null) {
         mc.field_1724.method_33574(this.playerStart);
         mc.field_1724.method_18799(class_243.field_1353);
         mc.field_1724.method_36456(this.savedYaw);
         mc.field_1724.method_36457(this.savedPitch);
      }

      this.mouseInitialized = false;
      previousCameraPosition = class_243.field_1353;
      this.lastMoveNanos = 0L;
      cameraVelocity = class_243.field_1353;
   }

   @Override
   public void renderHud(class_332 context) {
      if (!this.isEnabled() || !this.hud.get() || !active || mc.field_1724 == null) return;

      class_310 client = class_310.method_1551();
      String title = "FREECAM";
      String info = String.format("X %.1f   Y %.1f   Z %.1f   |   Tempo %.2f",
         cameraPosition.field_1352, cameraPosition.field_1351, cameraPosition.field_1350, this.speed.get());
      int width = Math.max(client.field_1772.method_1727(title), client.field_1772.method_1727(info)) + 24;
      int center = context.method_51421() / 2;
      int left = center - width / 2;
      int right = center + width / 2;
      context.method_25294(left, 4, right, 40, 0x90000000);
      context.method_25294(left, 4, right, 5, 0xFF39FFB6);
      context.method_25294(left, 39, right, 40, 0xFF39FFB6);
      context.method_51433(client.field_1772, title, center - client.field_1772.method_1727(title) / 2, 9, 0xFF39FFB6, true);
      context.method_51433(client.field_1772, info, center - client.field_1772.method_1727(info) / 2, 21, 0xFFFFFFFF, true);
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import org.lwjgl.glfw.GLFW;

public final class FreeLook extends Module {
   private static boolean active;
   private static float cameraYaw;
   private static float cameraPitch;
   private static float previousCameraYaw;
   private static float previousCameraPitch;
   private float playerYaw;
   private float playerPitch;
   private double lastMouseX;
   private double lastMouseY;
   private boolean mouseInitialized;
   private final double[] cursorX = new double[1];
   private final double[] cursorY = new double[1];
   private final NumberSetting cameraDistance = this.add(new NumberSetting("Kamera-Distanz", 6.0, 2.0, 24.0, 1.0));
   private final BoolSetting ignoreCollision = this.add(new BoolSetting("Kamerakollision ignorieren", true));

   public FreeLook() {
      super("FreeLook", "Mit der Maus umsehen, ohne die Blickrichtung des Spielers zu aendern.", Category.VISUALS);
   }

   public static boolean isActive() {
      return active;
   }

   public static float cameraYaw() {
      return cameraYaw;
   }

   public static float cameraPitch() {
      return cameraPitch;
   }

   public static float interpolatedYaw(float tickDelta) {
      float t = Math.max(0.0F, Math.min(1.0F, tickDelta));
      return previousCameraYaw + net.minecraft.class_3532.method_15393(cameraYaw - previousCameraYaw) * t;
   }

   public static float interpolatedPitch(float tickDelta) {
      float t = Math.max(0.0F, Math.min(1.0F, tickDelta));
      return previousCameraPitch + (cameraPitch - previousCameraPitch) * t;
   }

   public static double cameraDistance() {
      try {
         FreeLook module = ModuleManager.get(FreeLook.class);
         return module.cameraDistance.get();
      } catch (Throwable ignored) {
         return 4.0;
      }
   }

   public static class_243 cameraPosition(class_243 eye, float yaw, float pitch) {
      if (eye == null || mc.field_1724 == null || mc.field_1687 == null) return eye;
      double distance = cameraDistance();
      double yawRad = Math.toRadians(yaw);
      double pitchRad = Math.toRadians(pitch);
      double cp = Math.cos(pitchRad);
      class_243 target = eye.method_1031(
         Math.sin(yawRad) * cp * distance,
         Math.sin(pitchRad) * distance,
         -Math.cos(yawRad) * cp * distance
      );

      FreeLook module;
      try { module = ModuleManager.get(FreeLook.class); }
      catch (Throwable ignored) { return target; }
      if (module.ignoreCollision.get()) return target;

      class_3965 hit = mc.field_1687.method_17742(new class_3959(
         eye, target, class_3959.class_3960.field_17558,
         class_3959.class_242.field_1348, mc.field_1724
      ));
      if (hit.method_17783() != class_239.class_240.field_1332) return target;

      class_243 hitPos = hit.method_17784();
      double length = eye.method_1022(target);
      if (length < 1.0E-6) return hitPos;
      double factor = Math.max(0.0, (eye.method_1022(hitPos) - 0.2) / length);
      return new class_243(
         eye.field_1352 + (target.field_1352 - eye.field_1352) * factor,
         eye.field_1351 + (target.field_1351 - eye.field_1351) * factor,
         eye.field_1350 + (target.field_1350 - eye.field_1350) * factor
      );
   }

   private boolean activateIfReady() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         FreecamController freecam = null;

         try {
            freecam = ModuleManager.get(FreecamController.class);
         } catch (IllegalStateException var3) {
         }

         if (freecam != null && freecam.isEnabled()) {
            freecam.setEnabled(false);
         }

         this.playerYaw = mc.field_1724.method_36454();
         this.playerPitch = mc.field_1724.method_36455();
         cameraYaw = this.playerYaw;
         cameraPitch = this.playerPitch;
         previousCameraYaw = cameraYaw;
         previousCameraPitch = cameraPitch;
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
         previousCameraYaw = cameraYaw;
         previousCameraPitch = cameraPitch;
         long window = mc.method_22683().method_4490();
         GLFW.glfwGetCursorPos(window, this.cursorX, this.cursorY);
         if (this.mouseInitialized) {
            cameraYaw = cameraYaw + (float)((this.cursorX[0] - this.lastMouseX) * 0.15);
            cameraPitch = cameraPitch - (float)((this.cursorY[0] - this.lastMouseY) * 0.15);
            cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
         }

         this.lastMouseX = this.cursorX[0];
         this.lastMouseY = this.cursorY[0];
         this.mouseInitialized = true;
         mc.field_1724.method_36456(this.playerYaw);
         mc.field_1724.method_36457(this.playerPitch);
      }
   }

   @Override
   public void onWorldLeave() {
      active = false;
      this.mouseInitialized = false;
   }

   @Override
   protected void onDisable() {
      boolean wasActive = active;
      active = false;
      if (wasActive && mc.field_1724 != null) {
         mc.field_1724.method_36456(this.playerYaw);
         mc.field_1724.method_36457(this.playerPitch);
      }

      this.mouseInitialized = false;
   }
}

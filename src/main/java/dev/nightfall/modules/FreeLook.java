package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.NumberSetting;
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
   private final NumberSetting cameraDistance = this.add(new NumberSetting("Kamera-Reichweite", 4.0, 0.0, 50.0, 0.25));

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

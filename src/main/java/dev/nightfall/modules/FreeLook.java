package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import org.lwjgl.glfw.GLFW;

public final class FreeLook extends Module {
   private static boolean active;
   private static float cameraYaw;
   private static float cameraPitch;
   private float playerYaw;
   private float playerPitch;
   private double lastMouseX;
   private double lastMouseY;
   private boolean mouseInitialized;

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

   private boolean activateIfReady() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         Freecam freecam = null;

         try {
            freecam = ModuleManager.get(Freecam.class);
         } catch (IllegalStateException var3) {
         }

         if (freecam != null && freecam.isEnabled()) {
            freecam.setEnabled(false);
         }

         this.playerYaw = mc.field_1724.method_36454();
         this.playerPitch = mc.field_1724.method_36455();
         cameraYaw = this.playerYaw;
         cameraPitch = this.playerPitch;
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
         long window = mc.method_22683().method_4490();
         double[] x = new double[1];
         double[] y = new double[1];
         GLFW.glfwGetCursorPos(window, x, y);
         if (this.mouseInitialized) {
            cameraYaw = cameraYaw + (float)((x[0] - this.lastMouseX) * 0.15);
            cameraPitch = cameraPitch + (float)((y[0] - this.lastMouseY) * 0.15);
            cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch));
         }

         this.lastMouseX = x[0];
         this.lastMouseY = y[0];
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

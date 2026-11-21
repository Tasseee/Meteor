package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import net.minecraft.class_1299;
import net.minecraft.class_1531;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_638;
import org.lwjgl.glfw.GLFW;

public final class FreecamController extends Module {
   private static final int BODY_KEY = GLFW.GLFW_KEY_LEFT_ALT;

   private static boolean active;
   private static class_1531 cam;
   private static class_638 camWorld;
   private static double camX, camY, camZ;
   private static double previousCamX, previousCamY, previousCamZ;
   private static float previousCamYaw, previousCamPitch;
   private static double velX, velY, velZ;
   private static double eyeOffset;

   private class_243 playerStart = class_243.field_1353;
   private float savedYaw;
   private float savedPitch;

   private double lastMouseX;
   private double lastMouseY;
   private boolean mouseInitialized;
   private final double[] cursorX = new double[1];
   private final double[] cursorY = new double[1];

   private final dev.nightfall.module.setting.NumberSetting speed =
      this.add(new dev.nightfall.module.setting.NumberSetting("Geschwindigkeit", 0.6, 0.1, 3.0, 0.05));
   private final dev.nightfall.module.setting.BoolSetting stopOnDamage =
      this.add(new dev.nightfall.module.setting.BoolSetting("Stopp bei Schaden", true));
   private final dev.nightfall.module.setting.BoolSetting hud =
      this.add(new dev.nightfall.module.setting.BoolSetting("Freecam-Anzeige", true));
   private final dev.nightfall.module.setting.NumberSetting sensitivity =
      this.add(new dev.nightfall.module.setting.NumberSetting("Kamera-Sensitivitaet", 0.15, 0.03, 0.5, 0.01));

   public FreecamController() {
      super("Freecam", "Loest die Kamera vom Spielerkoerper und bewegt sie frei durch die Welt.", Category.MOVEMENT);
   }

   public static boolean isActive() {
      return active;
   }

   public static class_243 cameraPosition() {
      return cam == null ? class_243.field_1353 : new class_243(camX, camY, camZ);
   }

   public static float cameraYaw() {
      return cam == null ? 0.0F : cam.method_36454();
   }

   public static float cameraPitch() {
      return cam == null ? 0.0F : cam.method_36455();
   }

   public static class_243 interpolatedPosition(float tickDelta) {
      if (cam == null) return class_243.field_1353;
      float t = Math.max(0.0F, Math.min(1.0F, tickDelta));
      return new class_243(
         previousCamX + (camX - previousCamX) * t,
         previousCamY + (camY - previousCamY) * t,
         previousCamZ + (camZ - previousCamZ) * t
      );
   }

   public static float interpolatedYaw(float tickDelta) {
      if (cam == null) return 0.0F;
      float t = Math.max(0.0F, Math.min(1.0F, tickDelta));
      return previousCamYaw + class_3532.method_15393(cam.method_36454() - previousCamYaw) * t;
   }

   public static float interpolatedPitch(float tickDelta) {
      if (cam == null) return 0.0F;
      float t = Math.max(0.0F, Math.min(1.0F, tickDelta));
      return previousCamPitch + (cam.method_36455() - previousCamPitch) * t;
   }

   @Override
   protected void onEnable() {
      enableFreecam();
   }

   private void enableFreecam() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null) return;

      try {
         ModuleManager.get(FreeLook.class).setEnabled(false);
      } catch (Throwable ignored) {
      }

      playerStart = mc.field_1724.method_73189();
      savedYaw = mc.field_1724.method_36454();
      savedPitch = mc.field_1724.method_36455();

      camWorld = mc.field_1687;
      cam = new class_1531(class_1299.field_6131, camWorld);

      camX = mc.field_1724.method_23317();
      camY = mc.field_1724.method_33571().field_1351;
      camZ = mc.field_1724.method_23321();
      velX = velY = velZ = 0.0;

      float yaw = savedYaw;
      float pitch = savedPitch;
      cam.method_36456(yaw);
      cam.method_36457(pitch);

      eyeOffset = cam.method_5751() - cam.method_23318();
      previousCamX = camX;
      previousCamY = camY;
      previousCamZ = camZ;
      previousCamYaw = yaw;
      previousCamPitch = pitch;
      cam.method_5814(camX, camY - eyeOffset, camZ);

      active = true;
      mc.field_1692 = cam;
   }

   @Override
   protected void onDisable() {
      disableFreecam();
   }

   public void disableFreecam() {
      class_310 mc = class_310.method_1551();
      boolean wasActive = active;
      active = false;
      mouseInitialized = false;
      velX = velY = velZ = 0.0;
      cam = null;
      camWorld = null;

      if (wasActive && mc.field_1724 != null) {
         mc.field_1724.method_33574(playerStart);
         mc.field_1724.method_18799(class_243.field_1353);
         mc.field_1724.method_36456(savedYaw);
         mc.field_1724.method_36457(savedPitch);
         mc.field_1692 = mc.field_1724;
      }
   }

   @Override
   public void onTick() {
      if (!active) {
         enableFreecam();
         return;
      }

      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null || cam == null || mc.field_1687 != camWorld) {
         active = false;
         cam = null;
         camWorld = null;
         if (mc.field_1724 != null) mc.field_1692 = mc.field_1724;
         return;
      }

      if (stopOnDamage.get() && mc.field_1724.field_6235 > 0) {
         setEnabled(false);
         return;
      }

      if (mc.field_1692 != cam) {
         mc.field_1692 = cam;
      }
      // The camera entity is only a client-side render proxy. It never controls
      // the real player's collision box, so camera movement remains noclip.
      previousCamX = camX;
      previousCamY = camY;
      previousCamZ = camZ;
      previousCamYaw = cam.method_36454();
      previousCamPitch = cam.method_36455();

      long window = mc.method_22683().method_4490();
      GLFW.glfwGetCursorPos(window, cursorX, cursorY);

      if (mouseInitialized) {
         double dx = Math.max(-250.0, Math.min(250.0, cursorX[0] - lastMouseX));
         double dy = Math.max(-250.0, Math.min(250.0, cursorY[0] - lastMouseY));

         float yaw = cam.method_36454() + (float)dx * sensitivity.get().floatValue();
         float pitch = Math.max(-90.0F, Math.min(90.0F,
            cam.method_36455() - (float)dy * sensitivity.get().floatValue()));
         cam.method_36456(yaw);
         cam.method_36457(pitch);
      }

      lastMouseX = cursorX[0];
      lastMouseY = cursorY[0];
      mouseInitialized = true;

      // Read movement directly from GLFW instead of mutating Minecraft KeyBinding
      // states. This prevents a pressed key from being cancelled for a tick and
      // makes Freecam movement respond immediately like spectator movement.
      boolean forward = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
      boolean back = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS;
      boolean left = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS;
      boolean right = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS;
      boolean up = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
      boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
      boolean fast = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;

      class_243 move = class_243.field_1353;
      if (mc.field_1755 == null) {
         double yawR = Math.toRadians(cam.method_36454());

         // Creative-style camera movement: WASD is always horizontal and
         // follows yaw only. Looking up/down never tilts forward movement.
         class_243 forwardVec = new class_243(-Math.sin(yawR), 0.0, Math.cos(yawR));
         class_243 rightVec = new class_243(Math.cos(yawR), 0.0, Math.sin(yawR));

         if (forward) move = move.method_1019(forwardVec);
         if (back) move = move.method_1020(forwardVec);
         if (right) move = move.method_1019(rightVec);
         if (left) move = move.method_1020(rightVec);
         if (up) move = move.method_1031(0.0, 1.0, 0.0);
         if (down) move = move.method_1031(0.0, -1.0, 0.0);
      }

      double speedValue = speed.get() * (fast ? 2.0 : 1.0);
      class_243 step = move.method_1027() > 1.0E-4
         ? move.method_1029().method_1021(speedValue)
         : class_243.field_1353;

      // Apply input immediately: no acceleration tail, no delayed start and
      // no continued drift after the movement key is released.
      velX = step.field_1352;
      velY = step.field_1351;
      velZ = step.field_1350;
      camX += velX;
      camY += velY;
      camZ += velZ;
      cam.method_5814(camX, camY - eyeOffset, camZ);

      if (mc.field_1724.method_73189().method_1025(playerStart) > 1.0E-8) {
         mc.field_1724.method_33574(playerStart);
      }
      mc.field_1724.method_18799(class_243.field_1353);
      mc.field_1724.method_36456(savedYaw);
      mc.field_1724.method_36457(savedPitch);
   }

   @Override
   public void onWorldLeave() {
      active = false;
      mouseInitialized = false;
      cam = null;
      camWorld = null;
      velX = velY = velZ = 0.0;
      if (mc.field_1724 != null) mc.field_1692 = mc.field_1724;
   }

   @Override
   public void renderHud(class_332 context) {
      if (!isEnabled() || !hud.get() || !active || mc.field_1724 == null) return;

      String title = "FREECAM";
      String info = String.format("X %.1f   Y %.1f   Z %.1f   |   Tempo %.2f", camX, camY, camZ, speed.get());

      int width = Math.max(mc.field_1772.method_1727(title), mc.field_1772.method_1727(info)) + 24;
      int center = context.method_51421() / 2;
      int left = center - width / 2;
      int right = center + width / 2;

      context.method_25294(left, 4, right, 40, 0x90000000);
      context.method_25294(left, 4, right, 5, 0xFF39FFB6);
      context.method_25294(left, 39, right, 40, 0xFF39FFB6);
      context.method_51433(mc.field_1772, title, center - mc.field_1772.method_1727(title) / 2, 9, 0xFF39FFB6, true);
      context.method_51433(mc.field_1772, info, center - mc.field_1772.method_1727(info) / 2, 21, 0xFFFFFFFF, true);
   }
}

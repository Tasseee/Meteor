package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import net.minecraft.class_1299;
import net.minecraft.class_1531;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_310;
import net.minecraft.class_638;
import org.lwjgl.glfw.GLFW;

/**
 * FreecamController replaces the old Nightfall Freecam implementation.
 *
 * The camera is a local ArmorStand camera entity. The real player remains at
 * the saved position and keeps normal interaction. Holding Left Alt switches
 * control back to the real body while the camera stays frozen.
 */
public final class FreecamController extends Module {
   private static final int BODY_KEY = GLFW.GLFW_KEY_LEFT_ALT;

   private static boolean active;
   private static class_1531 cam;
   private static class_638 camWorld;
   private static double camX, camY, camZ;
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
   private final dev.nightfall.module.setting.NumberSetting smoothing =
      this.add(new dev.nightfall.module.setting.NumberSetting("Gleiten", 0.25, 0.03, 1.0, 0.01));
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
      return cam == null ? 0.0F : cam.getYaw();
   }

   public static float cameraPitch() {
      return cam == null ? 0.0F : cam.getPitch();
   }

   public static class_243 interpolatedPosition(float tickDelta) {
      if (cam == null) return class_243.field_1353;
      double x = cam.lastRenderX + (cam.getX() - cam.lastRenderX) * Math.max(0.0F, Math.min(1.0F, tickDelta));
      double y = cam.lastRenderY + (cam.getY() - cam.lastRenderY) * Math.max(0.0F, Math.min(1.0F, tickDelta));
      double z = cam.lastRenderZ + (cam.getZ() - cam.lastRenderZ) * Math.max(0.0F, Math.min(1.0F, tickDelta));
      return new class_243(x, y + eyeOffset, z);
   }

   public static float interpolatedYaw(float tickDelta) {
      return cameraYaw();
   }

   public static float interpolatedPitch(float tickDelta) {
      return cameraPitch();
   }

   public static boolean isBodyMode() {
      class_310 mc = class_310.method_1551();
      if (mc.method_22683() == null) return false;
      return GLFW.glfwGetKey(mc.method_22683().method_4490(), BODY_KEY) == GLFW.GLFW_PRESS;
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
      cam = new class_1531(class_1299.field_60980, camWorld);

      camX = mc.field_1724.getX();
      camY = mc.field_1724.method_33571().field_1351;
      camZ = mc.field_1724.getZ();
      velX = velY = velZ = 0.0;

      float yaw = savedYaw;
      float pitch = savedPitch;
      cam.setYaw(yaw);
      cam.setPitch(pitch);
      cam.prevYaw = yaw;
      cam.prevPitch = pitch;

      eyeOffset = cam.getEyeY();
      cam.setPosition(camX, camY - eyeOffset, camZ);
      syncPreviousPosition();

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

      syncPreviousPosition();

      if (isBodyMode()) {
         velX = velY = velZ = 0.0;
         cam.setPosition(camX, camY - eyeOffset, camZ);
         mc.field_1724.method_18799(class_243.field_1353);
         return;
      }

      long window = mc.method_22683().method_4490();
      GLFW.glfwGetCursorPos(window, cursorX, cursorY);

      if (mouseInitialized) {
         double dx = Math.max(-250.0, Math.min(250.0, cursorX[0] - lastMouseX));
         double dy = Math.max(-250.0, Math.min(250.0, cursorY[0] - lastMouseY));

         // Same mouse direction as the supplied FreecamController.
         float yaw = cam.getYaw() + (float)dx * (float)sensitivity.get();
         float pitch = Math.max(-90.0F, Math.min(90.0F,
            cam.getPitch() + (float)dy * (float)sensitivity.get()));
         cam.setYaw(yaw);
         cam.setPitch(pitch);
         cam.prevYaw = yaw;
         cam.prevPitch = pitch;
      }

      lastMouseX = cursorX[0];
      lastMouseY = cursorY[0];
      mouseInitialized = true;

      boolean forward = mc.options.forwardKey.isPressed();
      boolean back = mc.options.backKey.isPressed();
      boolean left = mc.options.leftKey.isPressed();
      boolean right = mc.options.rightKey.isPressed();
      boolean up = mc.options.jumpKey.isPressed();
      boolean down = mc.options.sneakKey.isPressed();
      boolean fast = mc.options.sprintKey.isPressed();

      mc.options.forwardKey.setPressed(false);
      mc.options.backKey.setPressed(false);
      mc.options.leftKey.setPressed(false);
      mc.options.rightKey.setPressed(false);
      mc.options.jumpKey.setPressed(false);
      mc.options.sneakKey.setPressed(false);
      mc.options.sprintKey.setPressed(false);

      class_243 move = class_243.field_1353;
      if (mc.field_1755 == null) {
         double yawR = Math.toRadians(cam.getYaw());
         double pitchR = Math.toRadians(cam.getPitch());

         class_243 forwardVec = new class_243(
            -Math.sin(yawR) * Math.cos(pitchR),
            -Math.sin(pitchR),
            Math.cos(yawR) * Math.cos(pitchR)
         );
         class_243 rightVec = new class_243(-Math.cos(yawR), 0.0, -Math.sin(yawR));

         if (forward) move = move.method_1019(forwardVec);
         if (back) move = move.method_1020(forwardVec);
         if (right) move = move.method_1019(rightVec);
         if (left) move = move.method_1020(rightVec);
         if (up) move = move.method_1031(0.0, 1.0, 0.0);
         if (down) move = move.method_1031(0.0, -1.0, 0.0);
      }

      double speedValue = speed.get() * (fast ? 2.0 : 1.0);
      class_243 target = move.method_1027() > 1.0E-4
         ? move.method_1029().method_1021(speedValue * 20.0)
         : class_243.field_1353;

      double k = Math.max(0.03, Math.min(1.0, smoothing.get()));
      velX += (target.field_1352 - velX) * k;
      velY += (target.field_1351 - velY) * k;
      velZ += (target.field_1350 - velZ) * k;

      double dt = 0.05;
      camX += velX * dt;
      camY += velY * dt;
      camZ += velZ * dt;

      cam.setPosition(camX, camY - eyeOffset, camZ);

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

      String title = isBodyMode() ? "FREECAM  -  KOERPER-MODUS" : "FREECAM";
      String info = String.format(
         "X %.1f   Y %.1f   Z %.1f   |   Tempo %.2f",
         camX, camY, camZ, speed.get()
      );

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

   private void syncPreviousPosition() {
      if (cam != null) {
         cam.lastRenderX = cam.getX();
         cam.lastRenderY = cam.getY();
         cam.lastRenderZ = cam.getZ();
      }
   }
}

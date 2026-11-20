package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_310;

/**
 * Third-person camera rotation independent from the local player's view.
 * The player keeps the same yaw and pitch while the camera can orbit freely.
 */
public final class FreeLook extends Module {
   private static FreeLook instance;

   private final ModeSetting perspective =
      this.add(new ModeSetting("Perspektive", "Dritte Person hinten", "Dritte Person hinten", "Dritte Person vorne"));
   private final NumberSetting sensitivity =
      this.add(new NumberSetting("Empfindlichkeit", 1.0, 0.1, 3.0, 0.05));
   private final BoolSetting noclip =
      this.add(new BoolSetting("Kamera ohne Kollision", true));
   private final BoolSetting scrollZoom =
      this.add(new BoolSetting("Zoom mit Mausrad", true));
   private final NumberSetting startDistance =
      this.add(new NumberSetting("Start-Abstand", 4.0, 1.0, 30.0, 0.25));
   private final NumberSetting scrollStep =
      this.add(new NumberSetting("Zoom-Schritt", 0.6, 0.1, 3.0, 0.1));
   private final NumberSetting maxDistance =
      this.add(new NumberSetting("Maximaler Abstand", 40.0, 5.0, 100.0, 1.0));
   private final NumberSetting smoothing =
      this.add(new NumberSetting("Zoom-Glättung", 0.6, 0.0, 0.95, 0.05));

   public float cameraYaw;
   public float cameraPitch;

   private double targetDistance = 4.0;
   private double shownDistance = 4.0;

   public FreeLook() {
      super("FreeLook", "Dreht die Third-Person-Kamera unabhängig von der Blickrichtung des Spielers.", Category.VISUALS);
      instance = this;
   }

   public static FreeLook active() {
      FreeLook current = instance;
      return current != null && current.isEnabled() ? current : null;
   }

   public static boolean isActive() {
      return active() != null;
   }

   public static boolean handleScroll(double vertical) {
      FreeLook current = active();
      class_310 mc = class_310.method_1551();
      if (current == null || !current.scrollZoom.get() || mc.field_1755 != null) return false;

      current.targetDistance = Math.max(
         0.5,
         Math.min(current.maxDistance.get(), current.targetDistance - vertical * current.scrollStep.get())
      );
      return true;
   }

   public boolean isFrontPerspective() {
      return perspective.is("Dritte Person vorne");
   }

   public boolean isNoClip() {
      return noclip.get();
   }

   public float getCameraYaw() {
      return cameraYaw;
   }

   public float getCameraPitch() {
      return cameraPitch;
   }

   public float getDistance() {
      shownDistance += (targetDistance - shownDistance) * (1.0 - smoothing.get());
      return (float) shownDistance;
   }

   public void changeLook(double dx, double dy) {
      float scale = 0.15F * sensitivity.get().floatValue();
      cameraYaw += (float) dx * scale;
      cameraPitch = Math.max(-90.0F, Math.min(90.0F, cameraPitch + (float) dy * scale));
   }

   @Override
   protected void onEnable() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null) {
         setEnabled(false);
         return;
      }

      // Freecam already owns the camera and player input; keep the two modes exclusive.
      FreecamController freecam = ModuleManager.get(FreecamController.class);
      if (freecam.isEnabled()) freecam.setEnabled(false);

      cameraYaw = mc.field_1724.method_36454();
      cameraPitch = mc.field_1724.method_36455();
      targetDistance = shownDistance = startDistance.get();
   }

   @Override
   protected void onDisable() {
      // CameraMixin returns to vanilla rotation/perspective automatically.
   }

   @Override
   public void onWorldLeave() {
      if (isEnabled()) setEnabled(false);
   }
}

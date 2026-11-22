package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;

public final class SkyVisuals extends Module {
   private final ModeSetting style = this.add(new ModeSetting(
      "Himmel", "Nebula", "Nebula", "Aurora", "Synthwave", "Eclipse", "Planet"
   ));
   private final NumberSetting distance = this.add(new NumberSetting("Abstand", 112.0, 48.0, 180.0, 4.0));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensität", 1.0, 0.1, 1.0, 0.05));
   private final NumberSetting rotation = this.add(new NumberSetting("Rotation", 1.0, 0.0, 4.0, 0.1));

   public SkyVisuals() {
      super("SkyVisuals", "Textur-Himmel mit fünf detailreichen Skybox-Stilen: Nebula, Aurora, Synthwave, Eclipse und Planet.", Category.VISUALS);
   }

   public String style() {
      return this.style.get();
   }

   public double distance() {
      return this.distance.get();
   }

   public float intensity() {
      return this.intensity.get().floatValue();
   }

   public float rotation() {
      return this.rotation.get().floatValue();
   }
}

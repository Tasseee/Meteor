package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;

public final class SkyVisuals extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Himmel", "Weltraum", "Weltraum", "Nordlicht", "Planet"));
   private final ColorSetting color = this.add(new ColorSetting("Hauptfarbe", 5627374));
   private final NumberSetting distance = this.add(new NumberSetting("Abstand", 112.0, 64.0, 180.0, 8.0));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensität", 1.0, 0.2, 2.0, 0.05));
   private final NumberSetting scale = this.add(new NumberSetting("Planetengröße", 1.0, 0.5, 2.0, 0.05));

   public SkyVisuals() {
      super("SkyVisuals", "Animierte 3D-Sterne, Aurora-Bänder und Planetenkörper im Weltraum-Himmel.", Category.VISUALS);
   }

   public String style() {
      return this.style.get();
   }

   public int color() {
      return this.color.rgb();
   }

   public double distance() {
      return this.distance.get();
   }

   public float intensity() {
      return this.intensity.get().floatValue();
   }

   public float planetScale() {
      return this.scale.get().floatValue();
   }
}

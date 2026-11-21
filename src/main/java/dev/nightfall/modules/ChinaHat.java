package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;

/** A decorative Chinese conical hat rendered above the local player. */
public final class ChinaHat extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0xD9A85B));
   private final NumberSetting radius = this.add(new NumberSetting("Radius", 0.62, 0.25, 1.2, 0.01));
   private final NumberSetting height = this.add(new NumberSetting("Höhe", 0.42, 0.15, 0.9, 0.01));
   private final NumberSetting brimWidth = this.add(new NumberSetting("Randbreite", 1.0, 0.7, 1.35, 0.01));
   private final NumberSetting rotationSpeed = this.add(new NumberSetting("Drehgeschwindigkeit", 0.15, 0.0, 1.0, 0.01));

   public ChinaHat() {
      super("ChinaHat", "Dekorativer kegelförmiger Hut über deinem Kopf (Third-Person).", Category.VISUALS);
   }

   public int color() { return this.color.rgb(); }
   public double radius() { return this.radius.get(); }
   public double height() { return this.height.get(); }
   public double brimWidth() { return this.brimWidth.get(); }
   public double rotationSpeed() { return this.rotationSpeed.get(); }
}

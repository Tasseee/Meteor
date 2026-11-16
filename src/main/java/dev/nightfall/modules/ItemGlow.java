package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;

/** True neon contour around rendered item geometry; no enchantment glint. */
public final class ItemGlow extends Module {
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Neon", "Neon", "Rainbow", "Pulse", "Dual"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x36F5FF));
   private final ColorSetting color2 = this.add(new ColorSetting("Zweite Farbe", 0xB64CFF));
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 1.0, 0.1, 4.0, 0.1));
   private final NumberSetting intensity = this.add(new NumberSetting("Leuchtstärke", 1.0, 0.2, 2.0, 0.05));
   private final NumberSetting opacity = this.add(new NumberSetting("Deckkraft", 1.0, 0.1, 1.0, 0.05));
   private final NumberSetting thickness = this.add(new NumberSetting("Konturstärke", 0.012, 0.002, 0.04, 0.001));
   private final NumberSetting edgeOffset = this.add(new NumberSetting("Randabstand", 0.0015, 0.0, 0.01, 0.0005));

   public ItemGlow() {
      super("Item Glow", "Animierte Neon-Outline entlang der sichtbaren Außenkanten von Items.", Category.VISUALS);
   }

   public int currentGlowColor() {
      double time = System.nanoTime() / 1_000_000_000.0 * this.speed.get();
      int rgb;
      if (this.mode.is("Rainbow")) {
         rgb = Color.HSBtoRGB((float)((time * 0.16) % 1.0), 0.9F, 1.0F) & 0xFFFFFF;
      } else if (this.mode.is("Pulse")) {
         double blend = (Math.sin(time * 1.8) + 1.0) * 0.5;
         rgb = blend(this.color.rgb(), this.color2.rgb(), blend);
      } else if (this.mode.is("Dual")) {
         double blend = (time * 0.35) % 1.0;
         blend = blend < 0.5 ? blend * 2.0 : (1.0 - blend) * 2.0;
         rgb = blend(this.color.rgb(), this.color2.rgb(), blend);
      } else {
         rgb = this.color.rgb();
      }

      double power = this.intensity.get();
      return clamp((int)Math.round((rgb >> 16 & 0xFF) * power)) << 16
         | clamp((int)Math.round((rgb >> 8 & 0xFF) * power)) << 8
         | clamp((int)Math.round((rgb & 0xFF) * power));
   }

   public int alpha() {
      return clamp((int)Math.round(255.0 * this.opacity.get()));
   }

   public float edgeOffset() {
      return this.edgeOffset.get().floatValue();
   }

   public float thickness() {
      return this.thickness.get().floatValue();
   }

   private static int blend(int first, int second, double amount) {
      int red = (int)Math.round((first >> 16 & 0xFF) * (1.0 - amount) + (second >> 16 & 0xFF) * amount);
      int green = (int)Math.round((first >> 8 & 0xFF) * (1.0 - amount) + (second >> 8 & 0xFF) * amount);
      int blue = (int)Math.round((first & 0xFF) * (1.0 - amount) + (second & 0xFF) * amount);
      return red << 16 | green << 8 | blue;
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }
}
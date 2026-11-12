package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;
import net.minecraft.class_1799;

public final class ItemGlint extends Module {
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Static", "Static", "Rainbow", "Pulse"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0xFF2878));
   private final ColorSetting color2 = this.add(new ColorSetting("Zweite Farbe", 0x3C78FF));
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 1.0, 0.1, 4.0, 0.1));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensitaet", 1.0, 0.2, 2.0, 0.1));

   public ItemGlint() {
      super("Item Glint", "Eigener, animierter Lichtstreifen-Glanz fuer alle Items.", Category.VISUALS);
   }

   public boolean forceGlint(class_1799 stack) {
      return this.isEnabled() && stack != null;
   }

   public boolean shouldTint() {
      return this.isEnabled();
   }

   public int currentGlintColor() {
      double time = System.currentTimeMillis() / 1000.0 * this.speed.get();
      int rgb;

      if (this.mode.is("Rainbow")) {
         rgb = Color.HSBtoRGB((float) ((time * 0.12) % 1.0), 0.82F, 1.0F) & 0xFFFFFF;
      } else if (this.mode.is("Pulse")) {
         double blend = (Math.sin(time * 1.8) + 1.0) * 0.5;
         rgb = blend(this.color.rgb(), this.color2.rgb(), blend);
      } else {
         rgb = this.color.rgb();
      }

      double strength = this.intensity.get();
      int red = clamp((int) (((rgb >> 16) & 255) * strength));
      int green = clamp((int) (((rgb >> 8) & 255) * strength));
      int blue = clamp((int) ((rgb & 255) * strength));
      return (red << 16) | (green << 8) | blue;
   }

   private static int blend(int first, int second, double amount) {
      int red = (int) (((first >> 16) & 255) * (1.0 - amount) + ((second >> 16) & 255) * amount);
      int green = (int) (((first >> 8) & 255) * (1.0 - amount) + ((second >> 8) & 255) * amount);
      int blue = (int) ((first & 255) * (1.0 - amount) + (second & 255) * amount);
      return (red << 16) | (green << 8) | blue;
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }
}

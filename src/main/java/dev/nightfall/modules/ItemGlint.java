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
   private final ColorSetting color2 = this.add(new ColorSetting("Farbe 2", 0x3C78FF));
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 1.0, 0.1, 5.0, 0.1));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensitaet", 1.2, 0.2, 3.0, 0.1));

   public ItemGlint() {
      super("Item Glint", "Vanilla-Glint auf allen Items mit eigener Farbe und Animation.", Category.VISUALS);
   }

   public boolean forceGlint(class_1799 stack) {
      if (!this.isEnabled() || stack == null) {
         return false;
      }

      return true;
   }

   public boolean shouldTint() {
      return this.isEnabled();
   }

   public int currentGlintColor() {
      double t = System.currentTimeMillis() / 1000.0 * this.speed.get();
      int rgb;

      if (this.mode.is("Rainbow")) {
         rgb = Color.HSBtoRGB((float) ((t * 0.1) % 1.0), 1.0F, 1.0F) & 0xFFFFFF;
      } else if (this.mode.is("Pulse")) {
         double k = (Math.sin(t * 2.0) + 1.0) / 2.0;
         int a = this.color.rgb();
         int b = this.color2.rgb();
         int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
         int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
         int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
         rgb = (r << 16) | (g << 8) | bl;
      } else {
         rgb = this.color.rgb();
      }

      double power = this.intensity.get();
      int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * power));
      int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * power));
      int bl = Math.min(255, (int) ((rgb & 0xFF) * power));
      return (r << 16) | (g << 8) | bl;
   }
}

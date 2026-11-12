package dev.nightfall.modules;

import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.util.Theme;
import java.awt.Color;

public final class GUITheme extends Module {
   private final ModeSetting preset = this.add(new ModeSetting("Preset", "Nightfall", "Nightfall", "Violet", "Crimson", "Emerald", "Ocean", "Mono", "Custom"));
   private final ModeSetting design = this.add(new ModeSetting("Design", "Gradient", "Gradient", "Flat", "Glass"));
   private final ColorSetting accent = this.add(new ColorSetting("Akzentfarbe", 3530960));
   private final ColorSetting secondary = this.add(new ColorSetting("Sekundärfarbe", 6898118));
   private final ColorSetting panel = this.add(new ColorSetting("Panel-Farbe", 1315864));
   private final ColorSetting text = this.add(new ColorSetting("Textfarbe", 15263984));
   private final ColorSetting dimText = this.add(new ColorSetting("Sekundärtext", 10132142));
   private final ColorSetting overlay = this.add(new ColorSetting("Overlay-Farbe", 526349));

   public GUITheme() {
      super("GUITheme", "ClickGUI-Presets, Design und frei einstellbare RGB-Farben.", Category.VISUALS);
      this.setEnabled(true);
   }

   public boolean prepareColorEdit(ColorSetting selected) {
      if (selected != this.accent
         && selected != this.secondary
         && selected != this.panel
         && selected != this.text
         && selected != this.dimText
         && selected != this.overlay) {
         return false;
      } else {
         this.preset.set("Custom");
         return true;
      }
   }

   public void setAccentHue(float hue) {
      float normalized = (hue % 360.0F + 360.0F) % 360.0F;
      this.accent.set(Color.HSBtoRGB(normalized / 360.0F, 0.78F, 1.0F) & 16777215);
      this.preset.set("Custom");
      this.applyTheme();
   }

   @Override
   protected void onEnable() {
      this.applyTheme();
   }

   @Override
   protected void onDisable() {
      Theme.reset();
   }

   @Override
   public void onTick() {
      this.applyTheme();
   }

   private void applyTheme() {
      String hue = this.preset.get();
      switch (hue) {
         case "Violet":
            this.overlay.set(525837);
            this.accent.set(11955455);
            this.secondary.set(5977528);
            this.panel.set(1511970);
            this.text.set(15854334);
            this.dimText.set(11707596);
            break;
         case "Crimson":
            this.overlay.set(1050122);
            this.accent.set(16732778);
            this.secondary.set(9184581);
            this.panel.set(1773848);
            this.text.set(16773362);
            this.dimText.set(12953776);
            break;
         case "Emerald":
            this.overlay.set(397323);
            this.accent.set(4843170);
            this.secondary.set(1604450);
            this.panel.set(1055769);
            this.text.set(15335414);
            this.dimText.set(10208949);
            break;
         case "Ocean":
            this.overlay.set(330258);
            this.accent.set(4569599);
            this.secondary.set(2645179);
            this.panel.set(1055015);
            this.text.set(15398655);
            this.dimText.set(10205392);
            break;
         case "Mono":
            this.overlay.set(526344);
            this.accent.set(16777215);
            this.secondary.set(7829367);
            this.panel.set(1381653);
            this.text.set(16777215);
            this.dimText.set(11184810);
            break;
         case "Nightfall":
            this.overlay.set(526349);
            this.accent.set(3530960);
            this.secondary.set(6898118);
            this.panel.set(1315864);
            this.text.set(15263984);
            this.dimText.set(10132142);
      }

      float[] hsb = Color.RGBtoHSB(this.accent.rgb() >> 16 & 0xFF, this.accent.rgb() >> 8 & 0xFF, this.accent.rgb() & 0xFF, null);
      Config.accentHue = hsb[0] * 360.0F;
      Theme.apply(this.accent.rgb(), this.secondary.rgb(), this.panel.rgb(), this.text.rgb(), this.dimText.rgb(), this.overlay.rgb(), this.design.get());
   }
}

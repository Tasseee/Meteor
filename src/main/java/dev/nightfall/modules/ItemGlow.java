package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;
import net.minecraft.class_1799;

/**
 * Replaces the vanilla enchanted-item shimmer with a brighter, configurable
 * colored glow. The existing Minecraft glint render layers are retained so
 * item lighting, depth and rendering order remain compatible.
 */
public final class ItemGlow extends Module {
   private final BoolSetting allItems = this.add(new BoolSetting("Alle Items", true));
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Neon", "Neon", "Rainbow", "Pulse", "Dual"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0xFF2878));
   private final ColorSetting color2 = this.add(new ColorSetting("Zweite Farbe", 0x3C78FF));
   private final NumberSetting speed = this.add(new NumberSetting("Geschwindigkeit", 1.0, 0.1, 4.0, 0.1));
   private final NumberSetting brightness = this.add(new NumberSetting("Helligkeit", 1.35, 0.5, 2.5, 0.05));
   private final NumberSetting opacity = this.add(new NumberSetting("Deckkraft", 1.35, 0.2, 2.0, 0.05));

   public ItemGlow() {
      super("Item Glow", "Farbiger, heller Leuchteffekt auf Items mit Neon-, Rainbow- und Pulse-Modus.", Category.VISUALS);
   }

   /**
    * When Alle Items is disabled, Minecraft's normal enchantment check is
    * left untouched and only already-enchanted items keep their glint.
    */
   public boolean forceGlint(class_1799 stack) {
      return this.isEnabled() && this.allItems.get() && stack != null;
   }

   public boolean shouldTint() {
      return this.isEnabled();
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

      double power = this.brightness.get();
      int red = clamp((int)Math.round((rgb >> 16 & 0xFF) * power));
      int green = clamp((int)Math.round((rgb >> 8 & 0xFF) * power));
      int blue = clamp((int)Math.round((rgb & 0xFF) * power));
      return red << 16 | green << 8 | blue;
   }

   public double currentGlowOpacity() {
      return this.opacity.get();
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

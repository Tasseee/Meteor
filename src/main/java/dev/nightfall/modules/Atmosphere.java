package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_310;
import net.minecraft.class_332;

public final class Atmosphere extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x14DC14));
   private final NumberSetting density = this.add(new NumberSetting("Nebeldichte", 0.40, 0.0, 1.0, 0.01));
   private final NumberSetting vignette = this.add(new NumberSetting("Vignette", 0.60, 0.0, 1.0, 0.01));
   private final BoolSetting particles = this.add(new BoolSetting("Partikel", true));
   private final NumberSetting particleAmount = this.add(new NumberSetting("Partikel-Anzahl", 60.0, 0.0, 150.0, 5.0));

   public Atmosphere() {
      super("Atmosphere", "Farbiger Nebel, wandernde Dunstbänder, Vignette und Leuchtpartikel.", Category.VISUALS);
   }

   public void renderAtmosphere(class_332 context) {
      class_310 mc = class_310.method_1551();
      if (!this.isEnabled() || mc.field_1724 == null || mc.field_1690.field_1842 || mc.field_1755 != null) return;

      int width = context.method_51421();
      int height = context.method_51443();
      int rgb = this.color.rgb();
      float fog = this.density.get().floatValue();
      double time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9;

      context.method_25294(0, 0, width, height, argb(fog * 0.30F, rgb));

      int clear = argb(0.0F, rgb);
      int haze = argb(fog * 0.22F, rgb);
      for (int i = 0; i < 4; i++) {
         float centerY = height * (0.15F + 0.23F * i)
            + (float)Math.sin(time * 0.35 + i * 1.7) * height * 0.06F;
         int half = (int)(height * 0.22F);
         int top = (int)centerY - half;
         int middle = (int)centerY;
         int bottom = (int)centerY + half;
         context.method_25296(0, top, width, middle, clear, haze);
         context.method_25296(0, middle, width, bottom, haze, clear);
      }

      float edgeStrength = this.vignette.get().floatValue() * 0.75F;
      if (edgeStrength > 0.001F) {
         int dark = scale(rgb, 0.55F);
         int bandX = (int)(width * 0.22F);
         int bandY = (int)(height * 0.22F);
         context.method_25296(0, 0, width, bandY, argb(edgeStrength, dark), argb(0.0F, dark));
         context.method_25296(0, height - bandY, width, height, argb(0.0F, dark), argb(edgeStrength, dark));

         int steps = 20;
         for (int i = 0; i < steps; i++) {
            float fade = 1.0F - i / (float)steps;
            int c = argb(edgeStrength * fade * fade, dark);
            int x0 = i * bandX / steps;
            int x1 = (i + 1) * bandX / steps;
            context.method_25294(x0, 0, x1, height, c);
            context.method_25294(width - x1, 0, width - x0, height, c);
         }
      }

      if (this.particles.get()) {
         int amount = this.particleAmount.getInt();
         int bright = mix(rgb, 0xFFFFFF, 0.5F);
         for (int i = 0; i < amount; i++) {
            float sx = hash(i * 2 + 1);
            float sy = hash(i * 2 + 2);
            float speed = 0.02F + hash(i * 7 + 3) * 0.05F;
            int size = 1 + (int)(hash(i * 5 + 4) * 2.5F);
            float px = sx * width + (float)Math.sin(time * 0.6 + i) * 14.0F;
            float py = (((sy - (float)(time * speed)) % 1.0F) + 1.0F) % 1.0F * height;
            float twinkle = 0.5F + 0.5F * (float)Math.sin(time * 2.0 + i * 1.3);
            int x = (int)px;
            int y = (int)py;
            context.method_25294(x - 1, y - 1, x + size + 1, y + size + 1, argb(0.10F * twinkle, rgb));
            context.method_25294(x, y, x + size, y + size, argb(0.15F + 0.55F * twinkle, bright));
         }
      }
   }

   private static float hash(int n) {
      double value = Math.sin(n * 12.9898) * 43758.5453;
      return (float)(value - Math.floor(value));
   }

   private static int scale(int rgb, float factor) {
      return rgb(Math.round((rgb >> 16 & 0xFF) * factor),
         Math.round((rgb >> 8 & 0xFF) * factor),
         Math.round((rgb & 0xFF) * factor));
   }

   private static int mix(int first, int second, float amount) {
      int red = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * amount);
      int green = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * amount);
      int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * amount);
      return rgb(red, green, blue);
   }

   private static int argb(float alpha, int rgb) {
      int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      return a << 24 | (rgb & 0xFFFFFF);
   }

   private static int rgb(int red, int green, int blue) {
      return clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
   }

   private static int clamp(int value) {
      return Math.max(0, Math.min(255, value));
   }
}
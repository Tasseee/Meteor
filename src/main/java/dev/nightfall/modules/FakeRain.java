package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_310;
import net.minecraft.class_332;

/**
 * Client-only rain overlay. It draws lightweight HUD streaks and never
 * changes world weather, time, packets or server-side state.
 */
public final class FakeRain extends Module {
   private final NumberSetting drops = this.add(new NumberSetting("Tropfen", 140.0, 20.0, 500.0, 10.0));
   private final NumberSetting speed = this.add(new NumberSetting("Fallgeschwindigkeit", 0.85, 0.10, 2.5, 0.05));
   private final NumberSetting opacity = this.add(new NumberSetting("Deckkraft", 0.60, 0.10, 1.0, 0.05));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0xA8D8FF));

   public FakeRain() {
      super("FakeRain", "Zeigt clientseitige Regentropfen als HUD-Overlay, ohne das echte Wetter zu veraendern.", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1755 != null) return;

      int width = context.method_51421();
      int height = context.method_51443();
      if (width <= 0 || height <= 0) return;

      double time = System.nanoTime() / 1_000_000_000.0;
      int alpha = Math.max(1, Math.min(255, Math.round(opacity.get().floatValue() * 255.0F)));
      int argb = alpha << 24 | (color.rgb() & 0xFFFFFF);
      int count = drops.getInt();

      for (int i = 0; i < count; i++) {
         double seedX = hash(i * 2 + 11);
         double seedY = hash(i * 2 + 37);
         double drift = Math.sin(time * 0.9 + i * 0.71) * 3.0;
         int x = (int)Math.round(seedX * (width - 1) + drift);
         double progress = (seedY + time * speed.get()) % 1.0;
         if (progress < 0.0) progress += 1.0;
         int y = (int)(progress * height);
         int length = 4 + (int)(hash(i * 5 + 3) * 9.0);
         int thickness = hash(i * 7 + 5) > 0.88 ? 2 : 1;

         if (x < 0) x = 0;
         if (x >= width) x = width - 1;
         int bottom = Math.min(height, y + length);
         context.method_25294(x, y, Math.min(width, x + thickness), bottom, argb);
      }
   }

   private static double hash(int value) {
      double v = Math.sin(value * 12.9898) * 43758.5453;
      return v - Math.floor(v);
   }
}

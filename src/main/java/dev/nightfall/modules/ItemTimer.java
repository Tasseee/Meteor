package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class ItemTimer extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 40.0, 5.0, 128.0, 1.0));
   private final NumberSetting despawnTicks = this.add(new NumberSetting("Despawn-Ticks", 6000.0, 200.0, 12000.0, 100.0));
   private final NumberSetting warnSeconds = this.add(new NumberSetting("Warnzeit", 30.0, 0.0, 120.0, 5.0));
   private final BoolSetting showName = this.add(new BoolSetting("Name anzeigen", true));
   private final BoolSetting pulse = this.add(new BoolSetting("Warnen/Blinken", true));
   private final NumberSetting scale = this.add(new NumberSetting("Skalierung", 1.0, 0.6, 2.0, 0.05));
   private final NumberSetting maxItems = this.add(new NumberSetting("Max. Items", 40.0, 5.0, 150.0, 5.0));

   public ItemTimer() {
      super("ItemTimer", "Zeigt über gedroppten Items die verbleibende Despawn-Zeit.", Category.VISUALS);
   }

   @Override
   public void renderHud(net.minecraft.class_332 ctx) {
      if (mc.field_1724 == null || mc.field_1687 == null || !Projection.valid()) return;

      class_327 text = mc.field_1772;
      int drawn = 0;
      double[] screen = new double[2];

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (!(entity instanceof class_1542 item)) continue;
         if (drawn >= maxItems.getInt()) break;

         double distance = mc.field_1724.method_5739(item);
         if (distance > range.get()) continue;

         int age = item.method_6985();
         if (age < 0) continue;

         int remaining = Math.max(0, despawnTicks.getInt() - age);
         double seconds = remaining / 20.0;
         if (!Projection.project(item.method_23317(), item.method_23318() + 0.8, item.method_23321(), screen)) continue;

         drawn++;
         double fraction = Math.max(0.0, Math.min(1.0, remaining / despawnTicks.get()));
         float s = (float)(scale.get() * Math.max(0.6, Math.min(1.0, 1.15 - distance / 70.0)));
         int rgb = gradient(fraction);

         int alpha = 255;
         if (seconds <= warnSeconds.get() && pulse.get()) {
            alpha = (int)(170.0 + 85.0 * Math.sin(System.currentTimeMillis() / 100.0));
         }

         String timer = String.format("%d:%02d", (int)(seconds / 60), (int)(seconds % 60));
         int tw = text.method_1727(timer);
         float x = (float)screen[0];
         float y = (float)screen[1];

         ctx.method_25294(Math.round(x - 15 * s), Math.round(y - 15 * s), Math.round(x + 15 * s), Math.round(y + 15 * s), 0xB80A0A10);
         drawCircle(ctx, x, y, 12 * s, 0x35FFFFFF, 1);
         drawArc(ctx, x, y, 12 * s, fraction, rgb | (alpha << 24), 2);

         ctx.method_51433(text, timer, Math.round(x - tw / 2.0f), Math.round(y - 4 * s), rgb | 0xFF000000, true);

         if (showName.get()) {
            String name = item.method_6983().method_7964().getString();
            if (name.length() > 18) name = name.substring(0, 17) + "…";
            String label = item.method_6983().method_7947() + "x " + name;
            int nw = text.method_1727(label);
            ctx.method_51433(text, label, Math.round(x - nw / 2.0f), Math.round(y + 13 * s), 0xFFC8CDDC, true);
         }
      }
   }

   private void drawCircle(net.minecraft.class_332 ctx, float cx, float cy, float r, int argb, int thickness) {
      drawArc(ctx, cx, cy, r, 1.0, argb, thickness);
   }

   private void drawArc(net.minecraft.class_332 ctx, float cx, float cy, float r, double fraction, int argb, int thickness) {
      int segments = 48;
      double end = Math.max(0.0, Math.min(1.0, fraction));
      for (int i = 0; i < segments && i / (double)segments < end; i++) {
         double a0 = -Math.PI / 2 + Math.PI * 2 * i / segments;
         double a1 = -Math.PI / 2 + Math.PI * 2 * Math.min(i + 1, end * segments) / segments;
         Draw.line(ctx,
            cx + (float)Math.cos(a0) * r, cy + (float)Math.sin(a0) * r,
            cx + (float)Math.cos(a1) * r, cy + (float)Math.sin(a1) * r,
            argb, thickness, true);
      }
   }

   private int gradient(double f) {
      int red = 0xFF5050, yellow = 0xFFD246, green = 0x50E678;
      if (f > 0.5) return lerpRgb(yellow, green, (f - 0.5) * 2.0);
      return lerpRgb(red, yellow, f * 2.0);
   }

   private int lerpRgb(int a, int b, double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      int r = (int)(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
      int g = (int)(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
      int bl = (int)((a & 255) * (1 - t) + ((b & 255) & 255) * t);
      return (r << 16) | (g << 8) | bl;
   }
}

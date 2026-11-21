package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class ItemTimer extends Module {
   private static final int ARC_SEGMENTS = 48;
   private static final float[] ARC_COS = new float[ARC_SEGMENTS + 1];
   private static final float[] ARC_SIN = new float[ARC_SEGMENTS + 1];

   static {
      for (int i = 0; i <= ARC_SEGMENTS; i++) {
         double angle = -Math.PI / 2.0 + Math.PI * 2.0 * i / ARC_SEGMENTS;
         ARC_COS[i] = (float)Math.cos(angle);
         ARC_SIN[i] = (float)Math.sin(angle);
      }
   }

   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 40.0, 5.0, 128.0, 1.0));
   private final NumberSetting despawnTicks = this.add(new NumberSetting("Despawn-Ticks", 6000.0, 200.0, 12000.0, 100.0));
   private final NumberSetting warnSeconds = this.add(new NumberSetting("Warnzeit", 30.0, 0.0, 120.0, 5.0));
   private final BoolSetting showName = this.add(new BoolSetting("Name anzeigen", true));
   private final BoolSetting pulse = this.add(new BoolSetting("Warnen/Blinken", true));
   private final NumberSetting scale = this.add(new NumberSetting("Skalierung", 1.0, 0.6, 2.0, 0.05));
   private final NumberSetting maxItems = this.add(new NumberSetting("Max. Items", 40.0, 5.0, 150.0, 5.0));
   private final List<TimedItem> cachedItems = new ArrayList<>();
   private final double[] screen = new double[2];
   private int refreshTimer;

   public ItemTimer() {
      super("ItemTimer", "Zeigt über gedroppten Items die verbleibende Despawn-Zeit.", Category.VISUALS);
   }

   @Override
   protected void onDisable() {
      this.cachedItems.clear();
      this.refreshTimer = 0;
   }

   @Override
   public void onWorldLeave() {
      this.cachedItems.clear();
      this.refreshTimer = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.cachedItems.clear();
         this.refreshTimer = 0;
         return;
      }

      if (this.refreshTimer-- <= 0) {
         this.refreshTimer = 4;
         this.cachedItems.clear();
         double maxDistanceSquared = this.range.get() * this.range.get();
         int maxAge = this.despawnTicks.getInt();
         int cacheLimit = Math.max(64, Math.min(600, this.maxItems.getInt() * 4));

         for (class_1297 entity : mc.field_1687.method_18112()) {
            if (!(entity instanceof class_1542 item)) continue;
            int age = item.method_6985();
            if (age < 0 || age >= maxAge) continue;

            double dx = item.method_23317() - mc.field_1724.method_23317();
            double dy = item.method_23318() - mc.field_1724.method_23318();
            double dz = item.method_23321() - mc.field_1724.method_23321();
            if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) continue;

            String displayName = item.method_6983().method_7964().getString();
            if (displayName.length() > 18) displayName = displayName.substring(0, 17) + "…";
            String label = item.method_6983().method_7947() + "x " + displayName;
            this.cachedItems.add(new TimedItem(item, label));
            if (this.cachedItems.size() >= cacheLimit) break;
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || mc.field_1687 == null || !Projection.valid()) return;

      class_327 text = mc.field_1772;
      int drawn = 0;
      int maxDrawn = this.maxItems.getInt();
      double maxDistanceSquared = this.range.get() * this.range.get();

      for (TimedItem cached : this.cachedItems) {
         if (drawn >= maxDrawn) break;
         class_1542 item = cached.entity();
         if (item.method_6985() < 0) continue;

         double dx = item.method_23317() - mc.field_1724.method_23317();
         double dy = item.method_23318() - mc.field_1724.method_23318();
         double dz = item.method_23321() - mc.field_1724.method_23321();
         double distanceSquared = dx * dx + dy * dy + dz * dz;
         if (distanceSquared > maxDistanceSquared) continue;
         double distance = Math.sqrt(distanceSquared);

         int age = item.method_6985();
         int remaining = Math.max(0, this.despawnTicks.getInt() - age);
         double seconds = remaining / 20.0;
         if (!Projection.project(item.method_23317(), item.method_23318() + 0.8, item.method_23321(), this.screen)) continue;

         drawn++;
         double fraction = Math.max(0.0, Math.min(1.0, remaining / this.despawnTicks.get()));
         float s = (float)(this.scale.get() * Math.max(0.6, Math.min(1.0, 1.15 - distance / 70.0)));
         int rgb = gradient(fraction);

         int alpha = 255;
         if (seconds <= this.warnSeconds.get() && this.pulse.get()) {
            alpha = (int)(170.0 + 85.0 * Math.sin(System.currentTimeMillis() / 100.0));
         }

         String timer = formatTime((int)seconds);
         int tw = text.method_1727(timer);
         float x = (float)this.screen[0];
         float y = (float)this.screen[1];

         ctx.method_25294(Math.round(x - 15 * s), Math.round(y - 15 * s), Math.round(x + 15 * s), Math.round(y + 15 * s), 0xB80A0A10);
         drawCircle(ctx, x, y, 12 * s, 0x35FFFFFF, 1);
         drawArc(ctx, x, y, 12 * s, fraction, rgb | (alpha << 24), 2);

         ctx.method_51433(text, timer, Math.round(x - tw / 2.0f), Math.round(y - 4 * s), rgb | 0xFF000000, true);

         if (this.showName.get()) {
            int nw = text.method_1727(cached.label());
            ctx.method_51433(text, cached.label(), Math.round(x - nw / 2.0f), Math.round(y + 13 * s), 0xFFC8CDDC, true);
         }
      }
   }

   private static String formatTime(int totalSeconds) {
      int minutes = totalSeconds / 60;
      int seconds = totalSeconds % 60;
      return minutes + (seconds < 10 ? ":0" : ":") + seconds;
   }

   private void drawCircle(class_332 ctx, float cx, float cy, float radius, int argb, int thickness) {
      drawArc(ctx, cx, cy, radius, 1.0, argb, thickness);
   }

   private void drawArc(class_332 ctx, float cx, float cy, float radius, double fraction, int argb, int thickness) {
      double end = Math.max(0.0, Math.min(1.0, fraction));
      double segmentEnd = end * ARC_SEGMENTS;
      int completeSegments = (int)segmentEnd;

      for (int i = 0; i < completeSegments; i++) {
         Draw.line(ctx,
            cx + ARC_COS[i] * radius, cy + ARC_SIN[i] * radius,
            cx + ARC_COS[i + 1] * radius, cy + ARC_SIN[i + 1] * radius,
            argb, thickness, true);
      }

      double partial = segmentEnd - completeSegments;
      if (partial > 1.0E-6 && completeSegments < ARC_SEGMENTS) {
         double angle = -Math.PI / 2.0 + Math.PI * 2.0 * end;
         float endX = cx + (float)Math.cos(angle) * radius;
         float endY = cy + (float)Math.sin(angle) * radius;
         Draw.line(ctx,
            cx + ARC_COS[completeSegments] * radius, cy + ARC_SIN[completeSegments] * radius,
            endX, endY, argb, thickness, true);
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

   private record TimedItem(class_1542 entity, String label) {
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_332;

/**
 * Floating sky ribbons with optional ground sheen and projected reflections.
 *
 * The renderer uses only Nightfall's filled world geometry pipeline. Ground
 * heights are cached and refreshed at a low frequency to avoid per-frame
 * block-state scans.
 */
public final class SkyRibbons extends Module {
   private final NumberSetting ribbons = this.add(new NumberSetting("Lichtbänder", 7.0, 1.0, 16.0, 1.0));
   private final NumberSetting segments = this.add(new NumberSetting("Segmente", 56.0, 16.0, 120.0, 1.0));
   private final NumberSetting skyRadius = this.add(new NumberSetting("Abstand", 55.0, 20.0, 120.0, 1.0));
   private final NumberSetting skyWidth = this.add(new NumberSetting("Bandbreite", 1.4, 0.2, 5.0, 0.1));
   private final NumberSetting flowSpeed = this.add(new NumberSetting("Tempo", 1.0, 0.1, 4.0, 0.05));
   private final NumberSetting intensity = this.add(new NumberSetting("Helligkeit", 0.9, 0.1, 1.0, 0.05));
   private final NumberSetting sparkles = this.add(new NumberSetting("Funken", 35.0, 0.0, 120.0, 1.0));
   private final BoolSetting onlyOutdoors = this.add(new BoolSetting("Nur im Freien", true));

   private final BoolSetting groundEnabled = this.add(new BoolSetting("Boden-Look", true));
   private final NumberSetting groundRadius = this.add(new NumberSetting("Boden-Radius", 12.0, 4.0, 20.0, 1.0));
   private final NumberSetting darken = this.add(new NumberSetting("Boden abdunkeln", 0.35, 0.0, 0.9, 0.05));
   private final NumberSetting groundGlow = this.add(new NumberSetting("Boden-Schimmer", 0.30, 0.0, 1.0, 0.05));
   private final BoolSetting reflect = this.add(new BoolSetting("Spiegelung", true));
   private final NumberSetting reflectStrength = this.add(new NumberSetting("Spiegel-Stärke", 0.30, 0.05, 1.0, 0.05));

   private final ModeSetting palette = this.add(new ModeSetting(
      "Farbschema", "Gold", "Gold", "Violett", "Ozean", "Sonnenuntergang", "Mint", "Eisblau", "Regenbogen", "Eigene"
   ));
   private final ColorSetting customInner = this.add(new ColorSetting("Eigene Farbe Start", 0xFFA01E));
   private final ColorSetting customOuter = this.add(new ColorSetting("Eigene Farbe Ende", 0xFFE178));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Rainbow-Tempo", 0.6, 0.1, 3.0, 0.05));
   private final BoolSetting atmosphere = this.add(new BoolSetting("Himmel-Atmosphäre", true));
   private final NumberSetting atmosphereStrength = this.add(new NumberSetting("Himmel-Stärke", 0.45, 0.05, 0.9, 0.05));
   private final NumberSetting vignette = this.add(new NumberSetting("Vignette", 0.35, 0.0, 0.9, 0.05));

   private boolean centerInitialized;
   private double centerX;
   private double centerY;
   private double centerZ;
   private double skyAlpha;
   private long lastFrameNanos;
   private long lastSkyCheckNanos;
   private boolean cachedSkyVisible = true;

   private double[] topCache;
   private int cacheRadius = -1;
   private int cacheSize;
   private long cacheTimeNanos;
   private class_2338 cacheCenter;
   private double groundYS = Double.NaN;

   public SkyRibbons() {
      super("SkyRibbons", "Goldene und farbige Lichtbänder mit schwebenden Funken und optionalem Boden-Schimmer.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.resetState();
   }

   @Override
   protected void onDisable() {
      this.resetState();
   }

   @Override
   public void onWorldLeave() {
      this.resetState();
   }

   private void resetState() {
      this.centerInitialized = false;
      this.centerX = 0.0;
      this.centerY = 0.0;
      this.centerZ = 0.0;
      this.skyAlpha = 0.0;
      this.lastFrameNanos = 0L;
      this.lastSkyCheckNanos = 0L;
      this.cachedSkyVisible = true;
      this.topCache = null;
      this.cacheRadius = -1;
      this.cacheSize = 0;
      this.cacheTimeNanos = 0L;
      this.cacheCenter = null;
      this.groundYS = Double.NaN;
   }

   /** Called once per world render frame before geometry is submitted. */
   public void prepareFrame(long nowNanos, double eyeX, double eyeY, double eyeZ) {
      double dt = this.lastFrameNanos == 0L
         ? 0.0
         : Math.max(0.0, Math.min(0.1, (nowNanos - this.lastFrameNanos) / 1_000_000_000.0));
      this.lastFrameNanos = nowNanos;

      if (!this.centerInitialized
         || square(this.centerX - eyeX) + square(this.centerY - eyeY) + square(this.centerZ - eyeZ) > 1600.0) {
         this.centerX = eyeX;
         this.centerY = eyeY;
         this.centerZ = eyeZ;
         this.centerInitialized = true;
      } else {
         double follow = 1.0 - Math.exp(-dt * 2.5);
         this.centerX += (eyeX - this.centerX) * follow;
         this.centerY += (eyeY - this.centerY) * follow;
         this.centerZ += (eyeZ - this.centerZ) * follow;
      }

      boolean visible = !this.onlyOutdoors.get() || this.isSkyVisible(nowNanos);
      double targetAlpha = visible ? 1.0 : 0.0;
      this.skyAlpha += (targetAlpha - this.skyAlpha) * (1.0 - Math.exp(-dt * 2.5));

      if (this.groundEnabled.get() && mc.field_1724 != null && mc.field_1687 != null) {
         this.updateGround(nowNanos, dt, eyeX, eyeZ);
      }
   }

   private boolean isSkyVisible(long nowNanos) {
      if (nowNanos - this.lastSkyCheckNanos < 250_000_000L) return this.cachedSkyVisible;
      this.lastSkyCheckNanos = nowNanos;

      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.cachedSkyVisible = false;
         return false;
      }

      class_2338 feet = mc.field_1724.method_24515();
      // A nearby non-empty outline above the player means a roof, cave ceiling or overhang.
      for (int dy = 1; dy <= 128; dy++) {
         class_2338 above = feet.method_10086(dy);
         if (!mc.field_1687.method_8320(above).method_26220(mc.field_1687, above).method_1110()) {
            this.cachedSkyVisible = false;
            return false;
         }
      }

      this.cachedSkyVisible = true;
      return true;
   }

   private void updateGround(long nowNanos, double dt, double eyeX, double eyeZ) {
      int radius = this.groundRadius.getInt();
      int size = radius * 2 + 1;
      boolean moved = this.cacheCenter == null
         || Math.abs(this.cacheCenter.method_10263() + 0.5 - eyeX) > 1.5
         || Math.abs(this.cacheCenter.method_10260() + 0.5 - eyeZ) > 1.5;

      if (this.topCache == null || this.cacheRadius != radius) {
         this.topCache = new double[size * size];
         this.cacheRadius = radius;
         this.cacheSize = size;
         this.cacheTimeNanos = 0L;
         moved = true;
      }

      if (this.cacheTimeNanos == 0L || moved || nowNanos - this.cacheTimeNanos >= 150_000_000L) {
         this.cacheTimeNanos = nowNanos;
         this.cacheCenter = new class_2338(
            (int)Math.floor(eyeX),
            (int)Math.floor(mc.field_1724.method_23318() - 0.5),
            (int)Math.floor(eyeZ)
         );

         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int index = (dx + radius) * size + dz + radius;
               class_2338 top = this.findTop(this.cacheCenter.method_10069(dx, 0, dz));
               if (top == null) {
                  this.topCache[index] = Double.NaN;
               } else {
                  class_2680 state = mc.field_1687.method_8320(top);
                  double localTop = state.method_26220(mc.field_1687, top).method_1107().field_1325;
                  this.topCache[index] = top.method_10264() + localTop;
               }
            }
         }
      }

      double target = this.topCache[radius * size + radius];
      if (!Double.isFinite(target)) target = mc.field_1724.method_23318();
      if (!Double.isFinite(this.groundYS)) this.groundYS = target;
      else this.groundYS += (target - this.groundYS) * (1.0 - Math.exp(-dt * 8.0));
   }

   private class_2338 findTop(class_2338 column) {
      for (int dy = 1; dy >= -3; dy--) {
         class_2338 pos = column.method_10086(dy);
         class_2680 state = mc.field_1687.method_8320(pos);
         if (state.method_26220(mc.field_1687, pos).method_1110()) continue;

         class_2338 above = pos.method_10084();
         if (!mc.field_1687.method_8320(above).method_26220(mc.field_1687, above).method_1110()) continue;
         return pos;
      }
      return null;
   }

   public double groundYAt(double x, double z) {
      if (this.topCache == null || this.cacheCenter == null || this.cacheRadius < 0) return Double.NaN;
      int dx = (int)Math.floor(x) - this.cacheCenter.method_10263();
      int dz = (int)Math.floor(z) - this.cacheCenter.method_10260();
      if (dx < -this.cacheRadius || dx > this.cacheRadius || dz < -this.cacheRadius || dz > this.cacheRadius) return Double.NaN;
      return this.topCache[(dx + this.cacheRadius) * this.cacheSize + dz + this.cacheRadius];
   }

   private static double square(double value) {
      return value * value;
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * t);
   }

   private static int[] channels(int rgb) {
      return new int[] {rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255};
   }

   public int color(double position, double time) {
      double p = Math.max(0.0, Math.min(1.0, position));
      if (this.palette.is("Regenbogen")) {
         float hue = (float)((time * this.rainbowSpeed.get() * 0.1 + p * 0.35) % 1.0);
         return Color.HSBtoRGB(hue, 0.65F, 1.0F) & 0xFFFFFF;
      }

      int[] inner;
      int[] outer;
      if (this.palette.is("Violett")) {
         inner = new int[] {140, 90, 255}; outer = new int[] {255, 120, 220};
      } else if (this.palette.is("Ozean")) {
         inner = new int[] {60, 140, 255}; outer = new int[] {90, 240, 235};
      } else if (this.palette.is("Sonnenuntergang")) {
         inner = new int[] {255, 100, 80}; outer = new int[] {255, 200, 90};
      } else if (this.palette.is("Mint")) {
         inner = new int[] {70, 220, 160}; outer = new int[] {170, 255, 120};
      } else if (this.palette.is("Eisblau")) {
         inner = new int[] {150, 200, 255}; outer = new int[] {235, 245, 255};
      } else if (this.palette.is("Eigene")) {
         inner = channels(this.customInner.rgb());
         outer = channels(this.customOuter.rgb());
      } else {
         inner = new int[] {255, 160, 30}; outer = new int[] {255, 225, 120};
      }

      return mix(inner[0], outer[0], p) << 16
         | mix(inner[1], outer[1], p) << 8
         | mix(inner[2], outer[2], p);
   }


   /** Screen-space tint and edge shading, matching the selected ribbon palette. */
   public void renderSkyAtmosphere(class_332 context) {
      if (!this.isEnabled() || !this.atmosphere.get() || this.skyAlpha < 0.01
         || mc.field_1724 == null || mc.field_1687 == null || mc.field_1755 != null) return;
      int width = context.method_51421();
      int height = context.method_51443();
      double time = (System.nanoTime() % 3_600_000_000_000L) / 1.0E9;
      int base = this.color(0.15, time);
      int warm = mix(base, 0x000000, 0.62);
      int deep = mix(base, 0x000000, 0.88);
      double strength = this.atmosphereStrength.get() * this.skyAlpha * (0.93 + 0.07 * Math.sin(time * 0.4));
      int top = argb(255.0 * strength, warm);
      int mid = argb(255.0 * strength * 0.30, warm);
      int bottom = argb(255.0 * strength * 0.55, deep);
      context.method_25296(0, 0, width, height / 2, top, mid);
      context.method_25296(0, height / 2, width, height, mid, bottom);
      double edge = this.vignette.get() * this.skyAlpha;
      if (edge > 0.01) {
         int dark = argb(255.0 * edge, 0x000000);
         int clear = 0;
         context.method_25296(0, 0, width, (int)(height * 0.28), dark, clear);
         context.method_25296(0, (int)(height * 0.72), width, height, clear, dark);
      }
   }


   private static int argb(double alpha, int rgb) {
      int a = (int)Math.max(0, Math.min(255, Math.round(alpha)));
      return a << 24 | rgb & 0xFFFFFF;
   }

   public int ribbonCount() { return this.ribbons.getInt(); }
   public int segments() { return this.segments.getInt(); }
   public double skyRadius() { return this.skyRadius.get(); }
   public double skyWidth() { return this.skyWidth.get(); }
   public double flowSpeed() { return this.flowSpeed.get(); }
   public double intensity() { return this.intensity.get(); }
   public int sparkleCount() { return this.sparkles.getInt(); }
   public boolean groundEnabled() { return this.groundEnabled.get(); }
   public int groundRadius() { return this.groundRadius.getInt(); }
   public double darken() { return this.darken.get(); }
   public double groundGlow() { return this.groundGlow.get(); }
   public boolean reflect() { return this.reflect.get(); }
   public double reflectStrength() { return this.reflectStrength.get(); }
   public double skyAlpha() { return this.skyAlpha; }
   public double centerX() { return this.centerX; }
   public double centerY() { return this.centerY; }
   public double centerZ() { return this.centerZ; }
   public int cacheCenterX() { return this.cacheCenter == null ? 0 : this.cacheCenter.method_10263(); }
   public int cacheCenterZ() { return this.cacheCenter == null ? 0 : this.cacheCenter.method_10260(); }
   public double groundYS() { return this.groundYS; }
   public boolean hasGroundCache() { return this.topCache != null && this.cacheCenter != null; }
}

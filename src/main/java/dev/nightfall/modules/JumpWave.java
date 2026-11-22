package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

/**
 * Client-side jump and landing waves.
 *
 * Ground columns are sampled once per wave and kept as immutable render data,
 * so the world renderer does not repeat block-state lookups every frame.
 */
public final class JumpWave extends Module {
   private final NumberSetting radius = this.add(new NumberSetting("Radius", 7.0, 2.0, 16.0, 0.5));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer (ms)", 900.0, 200.0, 2500.0, 50.0));
   private final NumberSetting ringWidth = this.add(new NumberSetting("Wellenbreite", 2.6, 0.8, 5.0, 0.1));
   private final NumberSetting lift = this.add(new NumberSetting("Block-Anhebung", 0.20, 0.0, 0.8, 0.01));
   private final NumberSetting heightRange = this.add(new NumberSetting("Gelände-Höhe", 3.0, 1.0, 6.0, 1.0));
   private final NumberSetting minFall = this.add(new NumberSetting("Mindest-Fallhöhe", 1.0, 0.0, 12.0, 0.5));
   private final NumberSetting maxWaves = this.add(new NumberSetting("Max. Wellen", 3.0, 1.0, 8.0, 1.0));
   private final BoolSetting jumpWave = this.add(new BoolSetting("Beim Absprung", true));
   private final BoolSetting landingWave = this.add(new BoolSetting("Beim Landen", true));

   private final ModeSetting renderMode = this.add(
      new ModeSetting("Render-Modus", "Energy", "Blöcke", "Neon", "Hologramm", "Energy", "Minimal")
   );
   private final ModeSetting palette = this.add(
      new ModeSetting("Farbschema", "Violett", "Violett", "Ozean", "Sonnenuntergang", "Mint", "Eisblau", "Regenbogen", "Eigene")
   );
   private final ColorSetting customInner = this.add(new ColorSetting("Farbe Innen", 0x8C5AFF));
   private final ColorSetting customOuter = this.add(new ColorSetting("Farbe Außen", 0xFF78DC));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Rainbow-Tempo", 0.6, 0.1, 3.0, 0.05));
   private final NumberSetting surfaceAlpha = this.add(new NumberSetting("Flächen-Deckkraft", 0.22, 0.03, 0.8, 0.01));
   private final NumberSetting edgeAlpha = this.add(new NumberSetting("Kanten-Deckkraft", 0.95, 0.0, 1.0, 0.01));
   private final BoolSetting pulse = this.add(new BoolSetting("Sanft pulsieren", true));
   private final NumberSetting pulseStrength = this.add(new NumberSetting("Puls-Stärke", 0.12, 0.0, 0.35, 0.01));

   private static final int MAX_SCAN_COLUMNS_PER_TICK = 256;
   private final List<Wave> waves = new ArrayList<>();
   private final List<WaveScan> pendingScans = new ArrayList<>();
   private boolean wasOnGround = true;
   private double trackedFallDistance;
   private Object trackedWorld;

   public JumpWave() {
      super("JumpWave", "Animierte Bodenwellen beim Springen und Landen – rein visuell.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.resetState();
      this.wasOnGround = mc.field_1724 == null || mc.field_1724.method_24828();
      this.trackedWorld = mc.field_1687;
   }

   @Override
   protected void onDisable() {
      this.resetState();
   }

   @Override
   public void onWorldLeave() {
      this.resetState();
      this.trackedWorld = null;
      this.wasOnGround = true;
   }

   private void resetState() {
      this.waves.clear();
      this.pendingScans.clear();
      this.trackedFallDistance = 0.0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.resetState();
         this.trackedWorld = null;
         this.wasOnGround = true;
         return;
      }

      if (this.trackedWorld != mc.field_1687) {
         this.resetState();
         this.trackedWorld = mc.field_1687;
         this.wasOnGround = mc.field_1724.method_24828();
      }

      long now = System.nanoTime();
      long lifetime = (long)(this.duration.get() * 1_000_000.0);
      this.waves.removeIf(wave -> now - wave.startNanos() >= lifetime);
      this.pendingScans.removeIf(scan -> !this.waves.contains(scan.wave));

      boolean onGround = mc.field_1724.method_24828();
      double x = mc.field_1724.method_23317();
      double y = mc.field_1724.method_23318();
      double z = mc.field_1724.method_23321();

      if (this.wasOnGround && !onGround && mc.field_1724.method_18798().field_1351 > 0.05) {
         if (this.jumpWave.get()) this.addWave(x, y - 0.15, z, 0.28, false);
         this.trackedFallDistance = 0.0;
      }

      if (!onGround) {
         this.trackedFallDistance = Math.max(this.trackedFallDistance, mc.field_1724.field_6017);
      } else if (!this.wasOnGround) {
         double fallDistance = Math.max(this.trackedFallDistance, mc.field_1724.field_6017);
         if (this.landingWave.get() && fallDistance >= this.minFall.get()) {
            // A longer fall produces a wider, brighter and higher impact wave.
            double impactPower = Math.max(0.18, Math.min(1.65, fallDistance / 8.0));
            this.addWave(x, y - 0.08, z, impactPower, true);
         }
         this.trackedFallDistance = 0.0;
      }

      this.wasOnGround = onGround;
      this.processPendingScans();
   }

   private void addWave(double x, double y, double z, double power, boolean impact) {
      class_2338 center = new class_2338((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z));
      int scanRadius = (int)Math.ceil(this.radius.get() * 1.7 + this.ringWidth.get());
      List<ScanOffset> offsets = buildScanOffsets(scanRadius);
      Wave wave = new Wave(x, z, System.nanoTime(), power, impact, new ArrayList<>());
      this.waves.add(wave);
      this.pendingScans.add(new WaveScan(wave, center, offsets, this.heightRange.getInt()));

      while (this.waves.size() > this.maxWaves.getInt()) {
         Wave removed = this.waves.remove(0);
         this.pendingScans.removeIf(scan -> scan.wave == removed);
      }
   }

   /**
    * Scan from the center outwards. This lets the visible leading ring have nearby
    * ground samples almost immediately while the far edge is completed over later ticks.
    */
   private static List<ScanOffset> buildScanOffsets(int radius) {
      int radiusSquared = radius * radius;
      List<ScanOffset> offsets = new ArrayList<>((int)(Math.PI * radiusSquared));
      for (int ring = 0; ring <= radius; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || dx * dx + dz * dz > radiusSquared) continue;
               offsets.add(new ScanOffset(dx, dz));
            }
         }
      }
      return offsets;
   }

   private void processPendingScans() {
      int budget = MAX_SCAN_COLUMNS_PER_TICK;
      for (int scanIndex = 0; scanIndex < this.pendingScans.size() && budget > 0;) {
         WaveScan scan = this.pendingScans.get(scanIndex);
         while (scan.cursor < scan.offsets.size() && budget > 0) {
            ScanOffset offset = scan.offsets.get(scan.cursor++);
            Cell cell = this.findTop(scan.center.method_10069(offset.dx, 0, offset.dz), scan.verticalRange);
            if (cell != null) scan.wave.cells().add(cell);
            budget--;
         }

         if (scan.cursor >= scan.offsets.size()) {
            this.pendingScans.remove(scanIndex);
         } else {
            scanIndex++;
         }
      }
   }

   private Cell findTop(class_2338 column, int verticalRange) {
      for (int dy = verticalRange; dy >= -verticalRange; dy--) {
         class_2338 pos = column.method_10086(dy);
         class_2680 state = mc.field_1687.method_8320(pos);
         var shape = state.method_26220(mc.field_1687, pos);
         if (shape.method_1110()) continue;

         class_2338 above = pos.method_10084();
         if (!mc.field_1687.method_8320(above).method_26220(mc.field_1687, above).method_1110()) continue;
         double topY = pos.method_10264() + shape.method_1107().field_1325;
         return new Cell(pos.method_10263(), pos.method_10264(), pos.method_10260(), topY);
      }
      return null;
   }

   private static final class WaveScan {
      final Wave wave;
      final class_2338 center;
      final List<ScanOffset> offsets;
      final int verticalRange;
      int cursor;

      WaveScan(Wave wave, class_2338 center, List<ScanOffset> offsets, int verticalRange) {
         this.wave = wave;
         this.center = center;
         this.offsets = offsets;
         this.verticalRange = verticalRange;
      }
   }

   private record ScanOffset(int dx, int dz) {}

   public List<Wave> waves() { return this.waves; }
   public String renderMode() { return this.renderMode.get(); }
   public String palette() { return this.palette.get(); }
   public double radius() { return this.radius.get(); }
   public double durationMs() { return this.duration.get(); }
   public double ringWidth() { return this.ringWidth.get(); }
   public double lift() { return this.lift.get(); }
   public double surfaceAlpha() { return this.surfaceAlpha.get(); }
   public double edgeAlpha() { return this.edgeAlpha.get(); }
   public boolean pulse() { return this.pulse.get(); }
   public double pulseStrength() { return this.pulseStrength.get(); }
   public double rainbowSpeed() { return this.rainbowSpeed.get(); }
   public int customInner() { return this.customInner.rgb(); }
   public int customOuter() { return this.customOuter.rgb(); }

   public int color(double position, long nowNanos) {
      double p = Math.max(0.0, Math.min(1.0, position));
      if (this.palette.is("Regenbogen")) {
         float hue = (float)((nowNanos / 1_000_000_000.0 * this.rainbowSpeed.get() + p * 0.35) % 1.0);
         return Color.HSBtoRGB(hue, 0.68F, 1.0F) & 0xFFFFFF;
      }

      int[] inner;
      int[] outer;
      if (this.palette.is("Eigene")) {
         inner = channels(this.customInner.rgb());
         outer = channels(this.customOuter.rgb());
      } else if (this.palette.is("Ozean")) {
         inner = new int[] {60, 140, 255}; outer = new int[] {90, 240, 235};
      } else if (this.palette.is("Sonnenuntergang")) {
         inner = new int[] {255, 110, 90}; outer = new int[] {255, 200, 90};
      } else if (this.palette.is("Mint")) {
         inner = new int[] {70, 220, 160}; outer = new int[] {170, 255, 120};
      } else if (this.palette.is("Eisblau")) {
         inner = new int[] {150, 200, 255}; outer = new int[] {235, 245, 255};
      } else {
         inner = new int[] {140, 90, 255}; outer = new int[] {255, 120, 220};
      }

      int r = mix(inner[0], outer[0], p);
      int g = mix(inner[1], outer[1], p);
      int b = mix(inner[2], outer[2], p);
      return r << 16 | g << 8 | b;
   }

   private static int[] channels(int rgb) {
      return new int[] {rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255};
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * t);
   }

   public record Cell(int x, int y, int z, double topY) {}
   public record Wave(double centerX, double centerZ, long startNanos, double power, boolean impact, List<Cell> cells) {}
}

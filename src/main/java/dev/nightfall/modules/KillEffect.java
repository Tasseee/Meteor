package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_243;

/** Client-side kill celebration. It only observes the entity currently attacked by the local player. */
public final class KillEffect extends Module {
   private final BoolSetting onlyPlayers = this.add(new BoolSetting("Nur Spieler", true));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer (ms)", 1800.0, 600.0, 4000.0, 100.0));
   private final NumberSetting scale = this.add(new NumberSetting("Größe", 1.0, 0.3, 3.0, 0.05));
   private final NumberSetting maxEffects = this.add(new NumberSetting("Max. Effekte", 5.0, 1.0, 12.0, 1.0));
   private final BoolSetting ring = this.add(new BoolSetting("Schockwelle", true));
   private final BoolSetting pillar = this.add(new BoolSetting("Lichtsäule", true));
   private final NumberSetting sparks = this.add(new NumberSetting("Funken", 40.0, 0.0, 120.0, 1.0));
   private final BoolSetting souls = this.add(new BoolSetting("Seelen-Spiralen", true));
   private final ModeSetting palette = this.add(new ModeSetting("Farbschema", "Gold", "Gold", "Violett", "Ozean", "Rot", "Mint", "Eisblau", "Regenbogen", "Eigene"));
   private final ColorSetting customInner = this.add(new ColorSetting("Eigene Farbe Start", 0xFFA01E));
   private final ColorSetting customOuter = this.add(new ColorSetting("Eigene Farbe Ende", 0xFFEB96));

   private final List<Effect> effects = new ArrayList<>();
   private class_1309 lastTarget;
   private long lastAttackNanos;

   public KillEffect() {
      super("KillEffect", "Spektakuläre Schockwelle, Lichtsäule, Funken und Seelen beim eigenen Kill.", Category.VISUALS);
   }

   @Override protected void onEnable() { reset(); }
   @Override protected void onDisable() { reset(); }
   @Override public void onWorldLeave() { reset(); }

   private void reset() {
      effects.clear();
      lastTarget = null;
      lastAttackNanos = 0L;
   }

   @Override
   public void onTick() {
      long now = System.nanoTime();
      long lifetime = (long)(this.duration.get() * 1_000_000.0);
      effects.removeIf(effect -> now - effect.startNanos() >= lifetime);

      if (mc.field_1724 == null || mc.field_1687 == null) {
         lastTarget = null;
         return;
      }

      // Remember the crosshair target only while the local attack input is being pressed.
      class_1297 crosshair = mc.field_1692;
      if (mc.field_1690.field_1881.method_1434() && crosshair instanceof class_1309 living && living != mc.field_1724) {
         lastTarget = living;
         lastAttackNanos = now;
      }

      if (lastTarget != null && now - lastAttackNanos <= 4_000_000_000L) {
         boolean dead = lastTarget.method_6032() <= 0.0F || !lastTarget.method_5805();
         if (dead && (!onlyPlayers.get() || lastTarget instanceof net.minecraft.class_1657)) {
            class_243 pos = new class_243(lastTarget.method_23317(), lastTarget.method_23318(), lastTarget.method_23321());
            effects.add(new Effect(pos, now, System.nanoTime() ^ lastTarget.method_5667().getLeastSignificantBits()));
            while (effects.size() > maxEffects.getInt()) effects.remove(0);
            lastTarget = null;
         }
      } else if (now - lastAttackNanos > 4_000_000_000L) {
         lastTarget = null;
      }
   }

   public List<Effect> effects() { return effects; }
   public long durationNanos() { return (long)(duration.get() * 1_000_000.0); }
   public double scale() { return scale.get(); }
   public boolean ringEnabled() { return ring.get(); }
   public boolean pillarEnabled() { return pillar.get(); }
   public int sparkCount() { return sparks.getInt(); }
   public boolean soulsEnabled() { return souls.get(); }

   public int color(double position, long seed, long now) {
      double p = Math.max(0.0, Math.min(1.0, position));
      if (palette.is("Regenbogen")) {
         float hue = (float)((now / 1_000_000_000.0 * 0.22 + p * 0.55 + (seed & 255) / 255.0) % 1.0);
         return java.awt.Color.HSBtoRGB(hue, 0.72F, 1.0F) & 0xFFFFFF;
      }
      int[] a, b;
      switch (palette.get()) {
         case "Violett" -> { a = new int[]{140, 90, 255}; b = new int[]{255, 120, 220}; }
         case "Ozean" -> { a = new int[]{60, 140, 255}; b = new int[]{120, 245, 240}; }
         case "Rot" -> { a = new int[]{255, 40, 50}; b = new int[]{255, 160, 110}; }
         case "Mint" -> { a = new int[]{70, 220, 160}; b = new int[]{190, 255, 130}; }
         case "Eisblau" -> { a = new int[]{150, 200, 255}; b = new int[]{240, 248, 255}; }
         case "Eigene" -> { a = channels(customInner.rgb()); b = channels(customOuter.rgb()); }
         default -> { a = new int[]{255, 160, 30}; b = new int[]{255, 235, 150}; }
      }
      return mix(a[0], b[0], p) << 16 | mix(a[1], b[1], p) << 8 | mix(a[2], b[2], p);
   }

   private static int[] channels(int rgb) { return new int[]{rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255}; }
   private static int mix(int a, int b, double t) { return (int)Math.round(a + (b - a) * t); }

   public record Effect(class_243 pos, long startNanos, long seed) {}
}

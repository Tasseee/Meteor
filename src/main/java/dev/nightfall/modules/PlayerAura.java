package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;

/** High-detail, client-side energy aura around the local player. */
public final class PlayerAura extends Module {
   private final NumberSetting scale = this.add(new NumberSetting("Größe", 1.0, 0.5, 2.5, 0.05));
   private final NumberSetting speed = this.add(new NumberSetting("Tempo", 1.0, 0.2, 3.0, 0.05));
   private final NumberSetting intensity = this.add(new NumberSetting("Helligkeit", 0.9, 0.1, 1.0, 0.01));
   private final BoolSetting firstPerson = this.add(new BoolSetting("In erster Person", true));

   private final BoolSetting rings = this.add(new BoolSetting("Energieringe", true));
   private final NumberSetting orbs = this.add(new NumberSetting("Lichtkugeln", 6.0, 0.0, 14.0, 1.0));
   private final NumberSetting embers = this.add(new NumberSetting("Funken", 32.0, 0.0, 100.0, 1.0));
   private final BoolSetting sigil = this.add(new BoolSetting("Bodensiegel", true));
   private final BoolSetting shell = this.add(new BoolSetting("Körperschein", true));

   private final ModeSetting palette = this.add(new ModeSetting(
      "Farbschema", "Violett", "Violett", "Gold", "Ozean", "Rot", "Mint", "Eisblau", "Regenbogen", "Eigene"
   ));
   private final ColorSetting customInner = this.add(new ColorSetting("Eigene Farbe Start", 0x8C5AFF));
   private final ColorSetting customOuter = this.add(new ColorSetting("Eigene Farbe Ende", 0xFF78DC));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Regenbogen-Tempo", 0.6, 0.1, 3.0, 0.05));

   private long activatedAtNanos;

   public PlayerAura() {
      super("PlayerAura", "Aufwendige Energie-Aura mit Kometenringen, Lichtkugeln, Funken und Bodensiegel.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      activatedAtNanos = System.nanoTime();
   }

   @Override
   protected void onDisable() {
      activatedAtNanos = 0L;
   }

   @Override
   public void onWorldLeave() {
      activatedAtNanos = System.nanoTime();
   }

   public double scale() { return scale.get(); }
   public double speed() { return speed.get(); }
   public double intensity() { return intensity.get(); }
   public boolean firstPerson() { return firstPerson.get(); }
   public boolean ringsEnabled() { return rings.get(); }
   public int orbCount() { return orbs.getInt(); }
   public int emberCount() { return embers.getInt(); }
   public boolean sigilEnabled() { return sigil.get(); }
   public boolean shellEnabled() { return shell.get(); }
   public long activatedAtNanos() { return activatedAtNanos; }
   public double rainbowSpeed() { return rainbowSpeed.get(); }
   public int customInner() { return customInner.rgb(); }
   public int customOuter() { return customOuter.rgb(); }
   public boolean rainbow() { return palette.is("Regenbogen"); }

   public int color(double position, double timeSeconds) {
      double p = Math.max(0.0, Math.min(1.0, position));
      if (palette.is("Regenbogen")) {
         float hue = (float)((timeSeconds * rainbowSpeed.get() * 0.1 + p * 0.35) % 1.0);
         return java.awt.Color.HSBtoRGB(hue, 0.68F, 1.0F) & 0xFFFFFF;
      }

      int innerRgb;
      int outerRgb;
      if (palette.is("Eigene")) {
         innerRgb = customInner.rgb();
         outerRgb = customOuter.rgb();
      } else if (palette.is("Gold")) {
         innerRgb = 0xFFA01E; outerRgb = 0xFFEB96;
      } else if (palette.is("Ozean")) {
         innerRgb = 0x3C8CFF; outerRgb = 0x78F5F0;
      } else if (palette.is("Rot")) {
         innerRgb = 0xFF2832; outerRgb = 0xFFA06E;
      } else if (palette.is("Mint")) {
         innerRgb = 0x46DCA0; outerRgb = 0xBEFF82;
      } else if (palette.is("Eisblau")) {
         innerRgb = 0x96C8FF; outerRgb = 0xF0F8FF;
      } else {
         innerRgb = 0x8C5AFF; outerRgb = 0xFF78DC;
      }

      int r = mix(innerRgb >> 16 & 255, outerRgb >> 16 & 255, p);
      int g = mix(innerRgb >> 8 & 255, outerRgb >> 8 & 255, p);
      int b = mix(innerRgb & 255, outerRgb & 255, p);
      return r << 16 | g << 8 | b;
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * t);
   }
}

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

      int[] inner;
      int[] outer;
      if (palette.is("Eigene")) {
         inner = channels(customInner.rgb());
         outer = channels(customOuter.rgb());
      } else if (palette.is("Gold")) {
         inner = new int[]{255, 160, 30}; outer = new int[]{255, 235, 150};
      } else if (palette.is("Ozean")) {
         inner = new int[]{60, 140, 255}; outer = new int[]{120, 245, 240};
      } else if (palette.is("Rot")) {
         inner = new int[]{255, 40, 50}; outer = new int[]{255, 160, 110};
      } else if (palette.is("Mint")) {
         inner = new int[]{70, 220, 160}; outer = new int[]{190, 255, 130};
      } else if (palette.is("Eisblau")) {
         inner = new int[]{150, 200, 255}; outer = new int[]{240, 248, 255};
      } else {
         inner = new int[]{140, 90, 255}; outer = new int[]{255, 120, 220};
      }

      int r = mix(inner[0], outer[0], p);
      int g = mix(inner[1], outer[1], p);
      int b = mix(inner[2], outer[2], p);
      return r << 16 | g << 8 | b;
   }

   private static int[] channels(int rgb) {
      return new int[]{rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255};
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * t);
   }
}

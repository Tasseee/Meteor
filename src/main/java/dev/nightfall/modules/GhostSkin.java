package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;

public final class GhostSkin extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x00FF40));
   private final NumberSetting alpha = this.add(new NumberSetting("Deckkraft", 0.55, 0.05, 1.0, 0.05));
   private final BoolSetting halo = this.add(new BoolSetting("Aura", true));
   private final NumberSetting haloAlpha = this.add(new NumberSetting("Aura-Deckkraft", 0.28, 0.02, 0.8, 0.02));
   private final NumberSetting haloScale = this.add(new NumberSetting("Aura-Größe", 1.10, 1.02, 1.4, 0.01));
   private final NumberSetting pulse = this.add(new NumberSetting("Pulsieren", 0.25, 0.0, 0.6, 0.01));
   private final BoolSetting rainbow = this.add(new BoolSetting("Regenbogen", false));
   private final NumberSetting shells = this.add(new NumberSetting("3D-Schichten", 4.0, 0.0, 8.0, 1.0));
   private final NumberSetting shellSpread = this.add(new NumberSetting("Schicht-Abstand", 0.035, 0.01, 0.09, 0.005));
   private final NumberSetting shellAlpha = this.add(new NumberSetting("Schicht-Licht", 0.22, 0.02, 0.6, 0.02));
   private final NumberSetting wave = this.add(new NumberSetting("Wabern", 0.45, 0.0, 1.0, 0.05));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Regenbogen-Tempo", 0.25, 0.02, 1.5, 0.01));

   public GhostSkin() {
      super("GhostSkin", "Halbtransparentes, pulsierendes 3D-Skin-Overlay mit mehreren Leuchtschichten – nur dein eigener Spieler.", Category.VISUALS);
   }

   public int color() { return this.color(0.0F); }

   public int color(float offset) {
      if (!this.rainbow.get()) return this.color.rgb();
      double time = System.nanoTime() / 1_000_000_000.0;
      return Color.HSBtoRGB((float)(time * this.rainbowSpeed.get() + offset), 0.85F, 1.0F) & 0xFFFFFF;
   }

   public float alpha() { return this.alpha.get().floatValue(); }
   public boolean halo() { return this.halo.get(); }
   public float haloAlpha() { return this.haloAlpha.get().floatValue(); }
   public float haloScale() { return this.haloScale.get().floatValue(); }
   public float pulse() { return this.pulse.get().floatValue(); }
   public int shells() { return this.shells.getInt(); }
   public float shellSpread() { return this.shellSpread.get().floatValue(); }
   public float shellAlpha() { return this.shellAlpha.get().floatValue(); }
   public float wave() { return this.wave.get().floatValue(); }
}
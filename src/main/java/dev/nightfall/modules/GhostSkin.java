package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;

public final class GhostSkin extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x00FF40));
   private final NumberSetting alpha = this.add(new NumberSetting("Deckkraft", 0.55, 0.05, 1.0, 0.05));
   private final BoolSetting halo = this.add(new BoolSetting("Aura", true));
   private final NumberSetting haloAlpha = this.add(new NumberSetting("Aura-Deckkraft", 0.28, 0.02, 0.8, 0.02));
   private final NumberSetting haloScale = this.add(new NumberSetting("Aura-Größe", 1.10, 1.02, 1.4, 0.01));
   private final NumberSetting pulse = this.add(new NumberSetting("Pulsieren", 0.25, 0.0, 0.6, 0.01));

   public GhostSkin() {
      super("GhostSkin", "Halbtransparentes, leuchtendes Skin-Overlay auf deinem eigenen Spieler (Third-Person).", Category.VISUALS);
   }

   public int color() { return this.color.rgb(); }
   public float alpha() { return this.alpha.get().floatValue(); }
   public boolean halo() { return this.halo.get(); }
   public float haloAlpha() { return this.haloAlpha.get().floatValue(); }
   public float haloScale() { return this.haloScale.get().floatValue(); }
   public float pulse() { return this.pulse.get().floatValue(); }
}
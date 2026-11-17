package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;

public final class ItemGlow extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x39FF14));
   private final NumberSetting radius = this.add(new NumberSetting("Glow-Radius", 34.0, 8.0, 90.0, 1.0));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensität", 0.85, 0.05, 1.0, 0.05));
   private final BoolSetting pulse = this.add(new BoolSetting("Pulsieren", true));
   private final BoolSetting rainbow = this.add(new BoolSetting("Regenbogen", false));
   private final BoolSetting sparks = this.add(new BoolSetting("Ring & Funken", true));
   private final BoolSetting offhand = this.add(new BoolSetting("Zweithand", true));
   private final BoolSetting hotbar = this.add(new BoolSetting("Hotbar", true));
   private final BoolSetting inventory = this.add(new BoolSetting("Inventar", true));
   private final NumberSetting positionX = this.add(new NumberSetting("Position X", 0.74, 0.50, 0.95, 0.01));
   private final NumberSetting positionY = this.add(new NumberSetting("Position Y", 0.80, 0.50, 0.95, 0.01));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Regenbogen-Tempo", 0.25, 0.02, 1.5, 0.01));

   public ItemGlow() {
      super("Item Glow", "Neon-Aura mit Ring und Funken um gehaltene Items sowie Hotbar- und Inventar-Symbole.", Category.VISUALS);
   }

   public int color() { return this.color.rgb(); }

   public int color(float hueOffset) {
      if (!this.rainbow.get()) return this.color.rgb();
      double time = System.nanoTime() / 1_000_000_000.0;
      return Color.HSBtoRGB((float)(time * this.rainbowSpeed.get() + hueOffset), 0.85F, 1.0F) & 0xFFFFFF;
   }

   public int radius() { return this.radius.getInt(); }
   public float intensity() { return this.intensity.get().floatValue(); }
   public boolean pulse() { return this.pulse.get(); }
   public boolean rainbow() { return this.rainbow.get(); }
   public boolean sparks() { return this.sparks.get(); }
   public boolean offhand() { return this.offhand.get(); }
   public boolean hotbar() { return this.hotbar.get(); }
   public boolean inventory() { return this.inventory.get(); }
   public float positionX() { return this.positionX.get().floatValue(); }
   public float positionY() { return this.positionY.get().floatValue(); }
}
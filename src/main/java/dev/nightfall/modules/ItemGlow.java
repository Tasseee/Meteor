package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;

public final class ItemGlow extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x39FF14));
   private final NumberSetting radius = this.add(new NumberSetting("Glow-Radius", 6.0, 1.0, 12.0, 1.0));
   private final NumberSetting intensity = this.add(new NumberSetting("Intensität", 0.85, 0.1, 1.0, 0.05));
   private final BoolSetting pulse = this.add(new BoolSetting("Pulsieren", true));
   private final BoolSetting hotbar = this.add(new BoolSetting("Hotbar", true));
   private final BoolSetting inventory = this.add(new BoolSetting("Inventar", true));

   public ItemGlow() {
      super("Item Glow", "Neon-Rand um Item-Symbole in Hotbar, Offhand und Inventar.", Category.VISUALS);
   }

   public int color() { return this.color.rgb(); }
   public int radius() { return this.radius.getInt(); }
   public float intensity() { return this.intensity.get().floatValue(); }
   public boolean pulse() { return this.pulse.get(); }
   public boolean hotbar() { return this.hotbar.get(); }
   public boolean inventory() { return this.inventory.get(); }
}
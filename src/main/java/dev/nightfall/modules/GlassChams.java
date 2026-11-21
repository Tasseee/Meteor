package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.awt.Color;
import net.minecraft.class_1657;

/**
 * Translucent, animated glass overlays for player models and dropped items.
 *
 * Player models are rendered by a normal feature renderer so Minecraft keeps
 * ownership of the model pose and limb animation. Dropped items use a simple
 * translucent world-space shell.
 */
public final class GlassChams extends Module {
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting items = this.add(new BoolSetting("Dropped Items", true));
   private final BoolSetting showSelf = this.add(new BoolSetting("Eigener Spieler", false));
   private final BoolSetting showInvisible = this.add(new BoolSetting("Unsichtbare Spieler", false));
   private final BoolSetting coverSkin = this.add(new BoolSetting("Skin überdecken", false));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 64.0, 8.0, 200.0, 4.0));

   private final ColorSetting tint = this.add(new ColorSetting("Glasfarbe", 0xBEAAFF));
   private final NumberSetting fillAlpha = this.add(new NumberSetting("Glas-Deckkraft", 0.30, 0.05, 0.90, 0.05));
   private final NumberSetting glowLayers = this.add(new NumberSetting("Glow-Schichten", 3.0, 0.0, 5.0, 1.0));
   private final NumberSetting glowSpread = this.add(new NumberSetting("Glow-Abstand", 0.035, 0.01, 0.10, 0.005));
   private final NumberSetting glowAlpha = this.add(new NumberSetting("Glow-Deckkraft", 0.20, 0.02, 0.50, 0.02));
   private final BoolSetting shimmer = this.add(new BoolSetting("Schimmern", true));
   private final BoolSetting rainbow = this.add(new BoolSetting("Rainbow", false));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Rainbow-Tempo", 0.25, 0.02, 1.5, 0.01));

   public GlassChams() {
      super("Glass Chams", "Leuchtende Glas-Overlays für Spielermodelle und gedroppte Items.", Category.VISUALS);
   }

   public boolean items() { return this.items.get(); }
   public boolean showInvisible() { return this.showInvisible.get(); }
   public boolean coverSkin() { return this.coverSkin.get(); }
   public float fillAlpha() { return this.fillAlpha.get().floatValue(); }
   public int glowLayers() { return this.glowLayers.getInt(); }
   public float glowSpread() { return this.glowSpread.get().floatValue(); }
   public float glowAlpha() { return this.glowAlpha.get().floatValue(); }
   public boolean shimmer() { return this.shimmer.get(); }

   public int color(float hueOffset) {
      if (!this.rainbow.get()) return this.tint.rgb();
      double time = System.nanoTime() / 1_000_000_000.0;
      return Color.HSBtoRGB((float)(time * this.rainbowSpeed.get() + hueOffset), 0.55F, 1.0F) & 0xFFFFFF;
   }

   public boolean shouldRenderPlayer(int entityId) {
      if (!this.isEnabled() || !this.players.get() || mc.field_1724 == null || mc.field_1687 == null) return false;

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player.method_5628() != entityId) continue;

         if (player == mc.field_1724) {
            return this.showSelf.get() && !mc.field_1690.method_31044().method_31034();
         }

         if (player.method_7325()) return false;
         if (player.method_5767() && !this.showInvisible.get()) return false;
         return player.method_5858(mc.field_1724) <= this.range.get() * this.range.get();
      }

      return false;
   }

   public boolean inRange(class_1657 player) {
      return mc.field_1724 != null && player.method_5858(mc.field_1724) <= this.range.get() * this.range.get();
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_332;

public final class Atmosphere extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Stil", "Nordlicht", "Weltraum", "Nordlicht", "Planet"));
   private final NumberSetting strength = this.add(new NumberSetting("Intensität", 18.0, 4.0, 45.0, 1.0));
   private final ColorSetting custom = this.add(new ColorSetting("Farbe", 4577224));

   public Atmosphere() {
      super("Atmosphere", "Dezente optionale Farbatmosphäre am oberen Bildschirmrand (kein echter Himmel-Shader).", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && !mc.field_1690.field_1842 && mc.field_1755 == null) {
         int w = ctx.method_51421();
         int h = ctx.method_51443();
         int base = this.style.is("Weltraum") ? 3418687 : (this.style.is("Planet") ? 12081968 : this.custom.rgb());
         int alpha = this.strength.getInt();

         for (int i = 0; i < 8; i++) {
            int a = Math.max(0, alpha - i * alpha / 8);
            ctx.method_25294(0, i * h / 12, w, (i + 1) * h / 12, a << 24 | base & 16777215);
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_332;

public final class CustomCrosshair extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Stil", "Kreuz", "Kreuz", "Punkt", "X"));
   private final NumberSetting size = this.add(new NumberSetting("Größe", 5.0, 2.0, 12.0, 1.0));
   private final NumberSetting gap = this.add(new NumberSetting("Abstand", 3.0, 0.0, 8.0, 1.0));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 5636010));

   public CustomCrosshair() {
      super("CustomCrosshair", "Anpassbares Fadenkreuz mit Stil, Größe, Abstand und Farbe.", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (!mc.field_1690.field_1842 && mc.field_1724 != null && mc.field_1687 != null && mc.field_1755 == null) {
         int cx = ctx.method_51421() / 2;
         int cy = ctx.method_51443() / 2;
         int s = this.size.getInt();
         int g = this.gap.getInt();
         int c = 0xFF000000 | this.color.rgb();
         if (this.style.is("Punkt")) {
            ctx.method_25294(cx - 1, cy - 1, cx + 2, cy + 2, c);
         } else if (this.style.is("X")) {
            for (int i = -s; i <= s; i++) {
               ctx.method_25294(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
               ctx.method_25294(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
            }
         } else {
            ctx.method_25294(cx - s - g, cy, cx - g, cy + 1, c);
            ctx.method_25294(cx + g + 1, cy, cx + g + s + 1, cy + 1, c);
            ctx.method_25294(cx, cy - s - g, cx + 1, cy - g, c);
            ctx.method_25294(cx, cy + g + 1, cx + 1, cy + g + s + 1, c);
         }
      }
   }
}

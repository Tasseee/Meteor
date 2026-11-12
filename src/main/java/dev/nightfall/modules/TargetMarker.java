package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import java.util.Locale;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_4184;

public final class TargetMarker extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Stil", "Karte", "Karte", "Kompakt", "Fadenkreuz"));
   private final ColorSetting color = this.add(new ColorSetting("Akzentfarbe", 9133302));
   private final BoolSetting distance = this.add(new BoolSetting("Distanz anzeigen", true));
   private final BoolSetting health = this.add(new BoolSetting("Leben anzeigen", true));

   public TargetMarker() {
      super("TargetMarker", "Zusätzliche Markierung für das Ziel unter dem Fadenkreuz.", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null && !mc.field_1690.field_1842 && mc.field_1755 == null) {
         class_1297 e = mc.field_1692;
         if (e != null && e != mc.field_1724 && e.method_5805()) {
            class_4184 camera = mc.field_1773.method_19418();
            class_243 cameraPos = camera != null && camera.method_19332() ? camera.method_71156() : mc.field_1724.method_33571();
            int cx = ctx.method_51421() / 2;
            int cy = ctx.method_51443() / 2;
            int c = 0xFF000000 | this.color.rgb();
            if (this.style.is("Fadenkreuz")) {
               ctx.method_25294(cx - 7, cy - 7, cx + 8, cy - 5, c);
               ctx.method_25294(cx - 7, cy + 5, cx + 8, cy + 7, c);
               ctx.method_25294(cx - 7, cy - 5, cx - 5, cy + 5, c);
               ctx.method_25294(cx + 5, cy - 5, cx + 7, cy + 5, c);
            }

            String label = e.method_5477().getString();
            if (this.health.get() && e instanceof class_1309 le) {
               label = label + "  " + Math.round(le.method_6032()) + " HP";
            }

            if (this.distance.get()) {
               label = label
                  + "  "
                  + String.format(Locale.ROOT, "%.1fm", Math.sqrt(e.method_5649(cameraPos.field_1352, cameraPos.field_1351, cameraPos.field_1350)));
            }

            int w = mc.field_1772.method_1727(label);
            int x = (ctx.method_51421() - w) / 2;
            int y = cy + 18;
            if (this.style.is("Karte")) {
               ctx.method_25294(x - 5, y - 3, x + w + 5, y + 9 + 4, -1191182336);
            }

            ctx.method_25294(x - 3, y - 2, x - 1, y + 9 + 2, c);
            ctx.method_51433(mc.field_1772, label, x, y, -1, false);
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;

public class Coordinates extends HudModule {
   private final BoolSetting dimension = this.add(new BoolSetting("Nether/Overworld Umrechnung", true));

   public Coordinates() {
      super("Coordinates", "Zeigt XYZ.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, sh - 16};
   }

   @Override
   protected void draw(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         int x = mc.field_1724.method_31477();
         int y = mc.field_1724.method_31478();
         int z = mc.field_1724.method_31479();
         String line = "XYZ " + x + " " + y + " " + z;
         String key = mc.field_1687.method_27983().method_29177().method_12832();
         if (this.dimension.get()) {
            if (key.equals("the_nether")) {
               line = line + "  (OW " + x * 8 + " " + z * 8 + ")";
            } else if (key.equals("overworld")) {
               line = line + "  (N " + x / 8 + " " + z / 8 + ")";
            }
         }

         this.w = mc.field_1772.method_1727(line) + 10;
         this.h = 12;
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, line, 6, 2, Theme.TEXT, true);
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_332;

public final class ArmorHud extends HudModule {
   private final BoolSetting durabilityText = this.add(new BoolSetting("Haltbarkeit anzeigen", true));

   public ArmorHud() {
      super("ArmorHUD", "Ruestung mit Haltbarkeit und Status anzeigen.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw / 2 - 72, sh - 58};
   }

   @Override
   protected void draw(class_332 ctx) {
      if (mc.field_1724 != null) {
         this.w = 144;
         this.h = 38;
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, this.w, 2, Theme.HUD_EDGE);

         for (int i = 0; i < 4; i++) {
            class_1799 stack = switch (i) {
               case 0 -> mc.field_1724.method_6118(class_1304.field_6166);
               case 1 -> mc.field_1724.method_6118(class_1304.field_6172);
               case 2 -> mc.field_1724.method_6118(class_1304.field_6174);
               default -> mc.field_1724.method_6118(class_1304.field_6169);
            };
            int x = 6 + i * 35;
            int y = 4;
            if (!stack.method_7960()) {
               ctx.method_51427(stack, x + 7, y);
               if (stack.method_7963()) {
                  int max = stack.method_7936();
                  int remaining = Math.max(0, max - stack.method_7919());
                  float ratio = max <= 0 ? 1.0F : (float)remaining / max;
                  int color = ratio > 0.5F ? -12397678 : (ratio > 0.2F ? -14249 : -41620);
                  ctx.method_25294(x + 2, 24, x + 31, 27, -13619142);
                  ctx.method_25294(x + 2, 24, x + 2 + Math.round(29.0F * ratio), 27, color);
                  if (this.durabilityText.get()) {
                     String pct = Math.round(ratio * 100.0F) + "%";
                     ctx.method_51433(mc.field_1772, pct, x + (35 - mc.field_1772.method_1727(pct)) / 2, 28, color, false);
                  }
               } else {
                  ctx.method_51433(mc.field_1772, "OK", x + 12, 28, Theme.TEXT_DIM, false);
               }
            } else {
               ctx.method_51433(mc.field_1772, "-", x + 14, 12, Theme.TEXT_DIM, false);
               ctx.method_51433(mc.field_1772, "Leer", x + 6, 28, Theme.TEXT_DIM, false);
            }
         }
      }
   }
}

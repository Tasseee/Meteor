package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_332;

public final class CooldownHud extends HudModule {
   private final BoolSetting pearls = this.add(new BoolSetting("Enderperle", true));
   private final BoolSetting windCharges = this.add(new BoolSetting("Wind Charge", true));
   private final BoolSetting chorus = this.add(new BoolSetting("Chorusfrucht", false));

   public CooldownHud() {
      super("CooldownHUD", "Zeigt relevante Item-Abklingzeiten.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - 126, Math.max(4, sh - 166)};
   }

   @Override
   protected void draw(class_332 ctx) {
      if (mc.field_1724 != null) {
         class_1799[] items = new class_1799[]{class_1802.field_8634.method_7854(), class_1802.field_49098.method_7854(), class_1802.field_8233.method_7854()};
         String[] names = new String[]{"Pearl", "Wind", "Chorus"};
         boolean[] enabled = new boolean[]{this.pearls.get(), this.windCharges.get(), this.chorus.get()};
         int count = 0;

         for (boolean e : enabled) {
            if (e) {
               count++;
            }
         }

         this.w = 120;
         this.h = 7 + count * 16;
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
         int line = 0;

         for (int i = 0; i < items.length; i++) {
            if (enabled[i]) {
               float cooldown = mc.field_1724.method_7357().method_7905(items[i], 0.0F);
               int y = 4 + line * 16;
               String status = cooldown > 0.001F ? Math.round(cooldown * 100.0F) + "%" : "Ready";
               ctx.method_51427(items[i], 5, y - 2);
               ctx.method_51433(mc.field_1772, names[i], 25, y, Theme.TEXT, false);
               int right = this.w - 6;
               int labelWidth = mc.field_1772.method_1727(status);
               ctx.method_51433(mc.field_1772, status, right - labelWidth, y, cooldown > 0.001F ? -14249 : -12397678, false);
               ctx.method_25294(25, y + 10, right, y + 12, -13619142);
               float remaining = Math.max(0.0F, Math.min(1.0F, cooldown));
               ctx.method_25294(25, y + 10, right - Math.round((right - 25) * (1.0F - remaining)), y + 12, cooldown > 0.001F ? -14249 : -12397678);
               line++;
            }
         }
      }
   }
}

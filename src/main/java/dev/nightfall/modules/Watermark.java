package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;

public class Watermark extends HudModule {
   public Watermark() {
      super("Watermark", "Zeigt den eigenen Minecraft-Profilnamen.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, 4};
   }

   @Override
   protected void draw(class_332 ctx) {
      String playerName = mc.field_1724 != null ? mc.field_1724.method_7334().name() : "Nicht verbunden";
      this.w = mc.field_1772.method_1727(playerName) + 10;
      this.h = 12;
      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, playerName, 6, 2, Theme.TEXT, true);
   }
}

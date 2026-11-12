package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;

public class DebugHud extends HudModule {
   public DebugHud() {
      super("Debug", "Scanner-, ESP- und Fehlerstatistik.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, 164};
   }

   @Override
   protected void draw(class_332 ctx) {
      BaseFinder bf = ModuleManager.get(BaseFinder.class);
      String[] lines = new String[]{
         "FPS " + mc.method_47599(),
         "Scanner: " + (bf.isEnabled() ? "an" : "aus") + "  " + String.format("%.2f ms/Tick", bf.lastScanMs()),
         "Chunks/Tick " + bf.lastScanned() + "  Queue " + bf.queueSize(),
         "Ergebnisse " + bf.resultCount() + "  Markiert " + bf.marks().size(),
         "ESP-Objekte (Frame) " + EspBase.drawn,
         "Modul-Fehler " + ModuleManager.errorCount
      };
      int mw = 0;

      for (String l : lines) {
         mw = Math.max(mw, mc.field_1772.method_1727(l));
      }

      this.w = mw + 10;
      this.h = lines.length * 10 + 4;
      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);

      for (int i = 0; i < lines.length; i++) {
         ctx.method_51433(mc.field_1772, lines[i], 6, 3 + i * 10, Theme.TEXT, false);
      }
   }
}

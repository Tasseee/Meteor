package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import java.util.List;
import net.minecraft.class_332;

public class ScannerHud extends HudModule {
   public ScannerHud() {
      super("ScannerStatus", "Top-Verdachtschunks des BaseFinders.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, 22};
   }

   @Override
   protected void draw(class_332 ctx) {
      BaseFinder bf = ModuleManager.get(BaseFinder.class);
      if (!bf.isEnabled()) {
         this.w = 0;
         this.h = 0;
      } else {
         List<ChunkResult> top = bf.suspects();
         int gone = 0;

         for (ChunkResult r : bf.marks()) {
            if (r.status == ChunkResult.Status.GONE) {
               gone++;
            }
         }

         int shown = Math.min(top.size(), 5);
         this.w = 190;
         this.h = 28 + shown * 10 + (gone > 0 ? 10 : 0);
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
         ctx.method_51433(
            mc.field_1772,
            "BaseFinder" + (bf.isPaused() ? "  PAUSE" : "") + "  " + String.format("%.1f ms", bf.lastScanMs()) + "  q" + bf.queueSize(),
            6,
            2,
            bf.isPaused() ? -14249 : Theme.TEXT_DIM,
            false
         );
         double progress = bf.progress();
         ctx.method_25294(6, 12, this.w - 6, 15, -14079690);
         ctx.method_25294(6, 12, 6 + (int)((this.w - 12) * progress), 15, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, bf.scannedSinceRefill() + "/" + bf.candidatesLastRefill() + " chunks", 6, 16, Theme.TEXT_DIM, false);
         int y = 27;

         for (int i = 0; i < shown; i++) {
            ChunkResult rx = top.get(i);
            ctx.method_51433(mc.field_1772, "SUS " + rx.blockX() + ", " + rx.blockZ() + "  " + String.format("%.1f", rx.score), 6, y, Theme.TEXT, false);
            y += 10;
         }

         if (gone > 0) {
            ctx.method_51433(mc.field_1772, "SUS WEG: " + gone, 6, y, -9524993, false);
         }
      }
   }
}

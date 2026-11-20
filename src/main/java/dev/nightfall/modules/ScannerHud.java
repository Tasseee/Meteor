package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import java.util.List;
import net.minecraft.class_332;

public class ScannerHud extends HudModule {
   public ScannerHud() {
      super("ScannerStatus", "Status, Fehler und Top-Ergebnisse des BaseFinders.");
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
         return;
      }

      List<ChunkResult> top = bf.suspects();
      int gone = bf.goneCount();
      int errors = bf.scanErrors();
      int shown = Math.min(top.size(), 5);
      String kelpSummary = "Ausgew. Kelp: " + bf.matureKelpCount()
         + " in " + bf.matureKelpChunkCount() + " Chunks";

      this.w = Math.max(204, mc.field_1772.method_1727(kelpSummary) + 12);
      this.h = 48 + shown * 10 + (gone > 0 ? 10 : 0) + (errors > 0 ? 10 : 0);
      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
      ctx.method_51433(
         mc.field_1772,
         "BaseFinder" + (bf.isPaused() ? "  PAUSE" : "") + "  " + String.format(java.util.Locale.ROOT, "%.1f ms", bf.lastScanMs()) + "  q" + bf.queueSize(),
         6,
         2,
         bf.isPaused() ? -14249 : Theme.TEXT_DIM,
         false
      );

      double progress = bf.progress();
      ctx.method_25294(6, 12, this.w - 6, 15, -14079690);
      ctx.method_25294(6, 12, 6 + (int)((this.w - 12) * progress), 15, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, bf.scannedSinceRefill() + "/" + bf.candidatesLastRefill() + " chunks", 6, 16, Theme.TEXT_DIM, false);

      String summary = "SUS " + top.size() + "  |  WEG " + gone + "  |  Erfasst " + bf.resultCount();
      ctx.method_51433(mc.field_1772, summary, 6, 27, Theme.TEXT, false);

      int y = 38;
      ctx.method_51433(mc.field_1772, kelpSummary, 6, y, Theme.TEXT_DIM, false);
      y += 10;

      for (int i = 0; i < shown; i++) {
         ChunkResult result = top.get(i);
         String line = "SUS " + result.blockX() + ", " + result.blockZ() + "  " + String.format(java.util.Locale.ROOT, "%.1f", result.score);
         ctx.method_51433(mc.field_1772, line, 6, y, Theme.TEXT, false);
         y += 10;
      }

      if (gone > 0) {
         ctx.method_51433(mc.field_1772, "SUS WEG: " + gone, 6, y, -9524993, false);
         y += 10;
      }
      if (errors > 0) {
         ctx.method_51433(mc.field_1772, "Scanfehler: " + errors, 6, y, -14249, false);
      }
   }
}

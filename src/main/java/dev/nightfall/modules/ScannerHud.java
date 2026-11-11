package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

public class ScannerHud extends HudModule {
    public ScannerHud() { super("ScannerStatus", "Top-Verdachtschunks des BaseFinders."); setEnabled(true); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, 22}; }

    @Override
    protected void draw(DrawContext ctx) {
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        if (!bf.isEnabled()) { w = 0; h = 0; return; }
        List<ChunkResult> top = bf.suspects();
        int gone = 0;
        for (ChunkResult r : bf.marks()) if (r.status == ChunkResult.Status.GONE) gone++;
        int shown = Math.min(top.size(), 5);
        w = 190;
        // Header ends at y=25; entries start at y=27 and need their glyph height too.
        h = 28 + shown * 10 + (gone > 0 ? 10 : 0);
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, "BaseFinder" + (bf.isPaused() ? "  PAUSE" : "")
                + "  " + String.format("%.1f ms", bf.lastScanMs()) + "  q" + bf.queueSize(),
                6, 2, bf.isPaused() ? 0xFFFFC857 : Theme.TEXT_DIM, false);
        double progress = bf.progress();
        ctx.fill(6, 12, w - 6, 15, 0xFF292936);
        ctx.fill(6, 12, 6 + (int) ((w - 12) * progress), 15, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, bf.scannedSinceRefill() + "/" + bf.candidatesLastRefill() + " chunks",
                6, 16, Theme.TEXT_DIM, false);
        int y = 27;
        for (int i = 0; i < shown; i++) {
            ChunkResult r = top.get(i);
            ctx.drawText(mc.textRenderer, "SUS " + r.blockX() + ", " + r.blockZ() + "  " + String.format("%.1f", r.score),
                    6, y, Theme.TEXT, false);
            y += 10;
        }
        if (gone > 0) ctx.drawText(mc.textRenderer, "SUS WEG: " + gone, 6, y, 0xFF6EA8FF, false);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

public class DebugHud extends HudModule {
    public DebugHud() { super("Debug", "Scanner-, ESP- und Fehlerstatistik."); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, 164}; }

    @Override
    protected void draw(DrawContext ctx) {
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        String[] lines = {
                "FPS " + mc.getCurrentFps(),
                "Scanner: " + (bf.isEnabled() ? "an" : "aus") + "  " + String.format("%.2f ms/Tick", bf.lastScanMs()),
                "Chunks/Tick " + bf.lastScanned() + "  Queue " + bf.queueSize(),
                "Ergebnisse " + bf.resultCount() + "  Markiert " + bf.marks().size(),
                "ESP-Objekte (Frame) " + EspBase.drawn,
                "Modul-Fehler " + ModuleManager.errorCount
        };
        int mw = 0;
        for (String l : lines) mw = Math.max(mw, mc.textRenderer.getWidth(l));
        w = mw + 10; h = lines.length * 10 + 4;
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        for (int i = 0; i < lines.length; i++) ctx.drawText(mc.textRenderer, lines[i], 6, 3 + i * 10, Theme.TEXT, false);
    }
}

package dev.nightfall.modules;

import dev.nightfall.Nightfall;
import dev.nightfall.module.HudModule;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Compact NIGHTFALL brand plate for the in-game HUD. */
public class Watermark extends HudModule {
    public Watermark() { super("Watermark", "Premium NIGHTFALL brand plate."); setEnabled(true); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{12, 12}; }

    @Override
    protected void draw(DrawContext ctx) {
        String title = "NIGHTFALL";
        String subtitle = "CLIENT  /  " + Nightfall.VERSION;
        int titleW = mc.textRenderer.getWidth(title);
        int subW = mc.textRenderer.getWidth(subtitle);
        w = Math.max(titleW, subW) + 38;
        h = 30;
        // Layered translucent plate and subtle violet edge.
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, w, 1, Theme.withAlpha(Theme.ACCENT, 190));
        ctx.fill(0, 1, 3, h, Theme.ACCENT);
        ctx.fill(3, h - 1, w, h, Theme.withAlpha(Theme.ACCENT, 90));
        ctx.fill(9, 7, 27, 25, Theme.withAlpha(Theme.ACCENT, 55));
        ctx.drawText(mc.textRenderer, "N", 14, 12, Theme.ACCENT, true);
        ctx.drawText(mc.textRenderer, title, 33, 6, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, subtitle, 33, 18, Theme.TEXT_DIM, false);
    }
}

package dev.nightfall.modules;

import dev.nightfall.Nightfall;
import dev.nightfall.module.HudModule;
import dev.nightfall.util.Theme;
import dev.nightfall.util.HudStyle;
import net.minecraft.client.gui.DrawContext;

public class Watermark extends HudModule {
    public Watermark() { super("Watermark", "Zeigt Name und Version."); setEnabled(true); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, 4}; }

    @Override
    protected void draw(DrawContext ctx) {
        String t = Nightfall.NAME + " " + Nightfall.VERSION;
        w = mc.textRenderer.getWidth(t) + 10; h = 12;
        HudStyle.panel(ctx, w, h);
        ctx.drawText(mc.textRenderer, t, 6, 2, Theme.TEXT, true);
    }
}

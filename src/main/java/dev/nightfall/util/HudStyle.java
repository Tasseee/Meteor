package dev.nightfall.util;

import net.minecraft.client.gui.DrawContext;

/** Shared Nightfall HUD surface: dark gradient, thin frame and accent rail. */
public final class HudStyle {
    private HudStyle() {}

    public static void panel(DrawContext ctx, int width, int height) {
        if (width <= 0 || height <= 0) return;
        ctx.fillGradient(0, 0, width, height, 0xE51B1D29, 0xD9101119);
        ctx.fill(0, 0, width, 1, Theme.withAlpha(Theme.ACCENT, 220));
        ctx.fill(0, height - 1, width, height, 0x553FFFFF);
        ctx.fill(0, 1, 1, height - 1, 0x553FFFFF);
        ctx.fill(width - 1, 1, width, height - 1, 0x553FFFFF);
        ctx.fill(0, 1, 2, height - 1, Theme.withAlpha(Theme.ACCENT, 145));
    }
}

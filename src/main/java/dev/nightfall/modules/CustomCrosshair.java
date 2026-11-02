package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Configurable replacement-style crosshair overlay. */
public final class CustomCrosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Stil", "Kreuz", "Kreuz", "Punkt", "X"));
    private final NumberSetting size = add(new NumberSetting("Größe", 5, 2, 12, 1));
    private final NumberSetting gap = add(new NumberSetting("Abstand", 3, 0, 8, 1));
    private final ColorSetting color = add(new ColorSetting("Farbe", 0x55FFAA));

    public CustomCrosshair() { super("CustomCrosshair", "Anpassbares Fadenkreuz mit Stil, Größe, Abstand und Farbe.", Category.VISUALS); }

    @Override public void renderHud(DrawContext ctx) {
        if (mc.options.hudHidden || mc.player == null || mc.world == null || mc.currentScreen != null) return;
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int s = size.getInt(), g = gap.getInt(), c = 0xFF000000 | color.rgb();
        if (style.is("Punkt")) {
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, c);
        } else if (style.is("X")) {
            for (int i = -s; i <= s; i++) {
                ctx.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
                ctx.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, c);
            }
        } else {
            ctx.fill(cx - s - g, cy, cx - g, cy + 1, c);
            ctx.fill(cx + g + 1, cy, cx + g + s + 1, cy + 1, c);
            ctx.fill(cx, cy - s - g, cx + 1, cy - g, c);
            ctx.fill(cx, cy + g + 1, cx + 1, cy + g + s + 1, c);
        }
    }
}

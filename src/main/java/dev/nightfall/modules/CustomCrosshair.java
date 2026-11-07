package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/** Configurable, high-contrast client-side crosshair overlay. */
public final class CustomCrosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Stil", "Kreuz", "Kreuz", "Punkt", "X"));
    private final NumberSetting size = add(new NumberSetting("Groesse", 5, 2, 12, 1));
    private final NumberSetting gap = add(new NumberSetting("Abstand", 3, 0, 8, 1));
    private final NumberSetting thickness = add(new NumberSetting("Dicke", 1, 1, 3, 1));
    private final ColorSetting color = add(new ColorSetting("Farbe", 0x55FFAA));
    private final BoolSetting outline = add(new BoolSetting("Kontrast-Umrandung", true));
    private final BoolSetting targetFeedback = add(new BoolSetting("Ziel-Farbwechsel", true));
    private final ColorSetting targetColor = add(new ColorSetting("Zielfarbe", 0xFF5D6C));

    public CustomCrosshair() {
        super("CustomCrosshair", "Anpassbares Fadenkreuz mit Stil, Dicke, Abstand, Kontrast und Ziel-Farbfeedback.", Category.VISUALS);
    }

    private void segment(DrawContext ctx, int x1, int y1, int x2, int y2, int argb) {
        if (x2 <= x1 || y2 <= y1) return;
        if (outline.get()) {
            ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xB8000000);
        }
        ctx.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.options.hudHidden || mc.player == null || mc.world == null || mc.currentScreen != null) return;

        int centerX = ctx.getScaledWindowWidth() / 2;
        int centerY = ctx.getScaledWindowHeight() / 2;
        int arm = size.getInt();
        int centerGap = gap.getInt();
        int line = thickness.getInt();
        int half = line / 2;
        boolean onTarget = targetFeedback.get() && mc.targetedEntity != null;
        int mainColor = 0xFF000000 | (onTarget ? targetColor.rgb() : color.rgb());

        if (style.is("Punkt")) {
            segment(ctx, centerX - half, centerY - half,
                    centerX - half + line, centerY - half + line, mainColor);
            return;
        }

        if (style.is("X")) {
            for (int offset = -arm; offset <= arm; offset++) {
                if (Math.abs(offset) <= centerGap) continue;
                segment(ctx, centerX + offset - half, centerY + offset - half,
                        centerX + offset - half + line, centerY + offset - half + line, mainColor);
                segment(ctx, centerX + offset - half, centerY - offset - half,
                        centerX + offset - half + line, centerY - offset - half + line, mainColor);
            }
            return;
        }

        // Four separate arms leave a clean, configurable opening around the aim point.
        segment(ctx, centerX - arm - centerGap, centerY - half,
                centerX - centerGap, centerY - half + line, mainColor);
        segment(ctx, centerX + centerGap + 1, centerY - half,
                centerX + centerGap + arm + 1, centerY - half + line, mainColor);
        segment(ctx, centerX - half, centerY - arm - centerGap,
                centerX - half + line, centerY - centerGap, mainColor);
        segment(ctx, centerX - half, centerY + centerGap + 1,
                centerX - half + line, centerY + centerGap + arm + 1, mainColor);
    }
}

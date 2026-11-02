package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Pulsing warning banner when the player's health drops below a configurable threshold. */
public final class LowHealthWarning extends HudModule {
    private final NumberSetting threshold = add(new NumberSetting("Schwelle Herzen", 4.0, 1.0, 10.0, 0.5));
    private boolean wasLow;

    public LowHealthWarning() {
        super("LowHealthWarning", "Pulsierende Warnung bei wenig Leben.");
        setEnabled(true);
    }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw / 2 - 60, 36}; }

    @Override public void onTick() {
        if (mc.player == null) { wasLow = false; return; }
        boolean low = mc.player.getHealth() <= threshold.get() * 2.0;
        if (low && !wasLow) {
            NotificationCenter.push("Wenig Leben", String.format("Noch %.1f Herzen", mc.player.getHealth() / 2.0f), 4_500L, 0xFFFF5D6C);
        }
        if (!low && mc.player.getHealth() > (threshold.get() + 1.0) * 2.0) wasLow = false;
        else if (low) wasLow = true;
    }

    @Override protected void draw(DrawContext ctx) {
        boolean preview = mc.currentScreen instanceof HudEditorScreen;
        if (mc.player == null || (mc.player.getHealth() > threshold.get() * 2.0f && !preview)) { w = 120; h = 18; return; }
        String text = preview && mc.player.getHealth() > threshold.get() * 2.0f
                ? "LOW HEALTH  3.5 ♥ (Preview)"
                : String.format("LOW HEALTH  %.1f ♥", mc.player.getHealth() / 2.0f);
        w = mc.textRenderer.getWidth(text) + 18;
        h = 18;
        float pulse = (float) (0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 150.0));
        int alpha = Math.max(120, Math.min(255, (int) (220 * pulse)));
        ctx.fill(0, 0, w, h, (alpha << 24) | 0x5D1018);
        ctx.fill(0, 0, 3, h, 0xFFFF5D6C);
        ctx.drawText(mc.textRenderer, text, 8, 5, 0xFFFFE6E8, true);
    }
}

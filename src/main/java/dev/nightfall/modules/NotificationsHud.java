package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Shared toast stack for module alerts and health warnings. */
public final class NotificationsHud extends HudModule {
    private final NumberSetting maxVisible = add(new NumberSetting("Max. Meldungen", 4, 1, 6, 1));

    public NotificationsHud() {
        super("Notifications", "Animierte Status- und Warnmeldungen.");
        setEnabled(true);
    }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw - 184, Math.max(8, sh - (maxVisible.getInt() * 34 + 8))}; }

    @Override protected void draw(DrawContext ctx) {
        List<NotificationCenter.Toast> toasts = NotificationCenter.active(System.currentTimeMillis());
        boolean preview = mc.currentScreen instanceof HudEditorScreen;
        int count = Math.min(toasts.size(), maxVisible.getInt());
        if (count == 0 && preview) count = 1;
        w = 176;
        h = count * 34;
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            NotificationCenter.Toast toast = toasts.isEmpty()
                    ? null : toasts.get(toasts.size() - 1 - Math.min(i, toasts.size() - 1));
            int y = i * 34;
            float progress = toast == null ? 0.72f : toast.progress(now);
            int offset = Math.round((1f - progress) * 8f);
            ctx.fill(offset, y, w, y + 30, Theme.HUD_PANEL);
            int color = toast == null ? Theme.ACCENT : toast.color;
            String title = toast == null ? "Nightfall" : toast.title;
            String message = toast == null ? "Beispiel-Benachrichtigung" : toast.message;
            ctx.fill(offset, y, offset + 3, y + 30, color);
            ctx.drawText(mc.textRenderer, trim(title, 23), offset + 8, y + 4, Theme.TEXT, true);
            ctx.drawText(mc.textRenderer, trim(message, 25), offset + 8, y + 15, Theme.TEXT_DIM, false);
            int barWidth = w - offset - 8;
            ctx.fill(offset + 4, y + 27, offset + 4 + barWidth, y + 29, 0xFF30303A);
            ctx.fill(offset + 4, y + 27, offset + 4 + Math.round(barWidth * progress), y + 29, color);
        }
    }

    private String trim(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)) + "…";
    }
}

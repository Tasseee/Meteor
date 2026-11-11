package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reports explicit anti-cheat state announcements received as game/system messages.
 * It deliberately never infers that an anti-cheat is disabled from lag or corrections.
 */
public final class AntiCheatMonitor extends HudModule {
    public enum Status { UNKNOWN, ACTIVE, INACTIVE }

    private static final Pattern ENABLED = Pattern.compile(
            "(?i).*(?:anti.?cheat|anticheat).*(?:enabled|active|activated|started|on).*");
    private static final Pattern DISABLED = Pattern.compile(
            "(?i).*(?:anti.?cheat|anticheat).*(?:disabled|inactive|deactivated|stopped|off).*");

    private final BoolSetting showOverlay = add(new BoolSetting("Status anzeigen", true));
    private final BoolSetting showLastSignal = add(new BoolSetting("Letzte Meldung anzeigen", true));

    private Status status = Status.UNKNOWN;
    private String lastSignal = "Keine eindeutige Servermeldung erkannt";
    private long lastSignalAt = 0L;

    public AntiCheatMonitor() {
        super("AntiCheatMonitor", "Zeigt erkannte Servermeldungen zum Anti-Cheat-Status als verschiebbares HUD-Panel.", Category.MISC);
    }

    /** Called only for game/system messages, not ordinary player chat. */
    public void onServerMessage(String raw) {
        if (raw == null) return;
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (ENABLED.matcher(normalized).matches()) {
            status = Status.ACTIVE;
        } else if (DISABLED.matcher(normalized).matches()) {
            status = Status.INACTIVE;
        } else {
            return;
        }
        lastSignal = raw.length() > 180 ? raw.substring(0, 180) + "..." : raw;
        lastSignalAt = System.currentTimeMillis();
    }

    @Override
    public void onWorldLeave() {
        status = Status.UNKNOWN;
        lastSignal = "Keine eindeutige Servermeldung erkannt";
        lastSignalAt = 0L;
    }

    @Override
    protected int[] defaultPos(int sw, int sh) {
        return new int[]{8, 116};
    }

    @Override
    protected void draw(DrawContext ctx) {
        if (!showOverlay.get()) {
            w = 0;
            h = 0;
            return;
        }

        String label = switch (status) {
            case ACTIVE -> "Anti-Cheat  ·  ACTIVE";
            case INACTIVE -> "Anti-Cheat  ·  INACTIVE";
            case UNKNOWN -> "Anti-Cheat  ·  UNKNOWN";
        };
        int color = switch (status) {
            case ACTIVE -> 0xFF55D68B;
            case INACTIVE -> 0xFFFF657A;
            case UNKNOWN -> 0xFFFFC66D;
        };

        String message = showLastSignal.get() ? fit(lastSignal, 224) : null;
        String age = showLastSignal.get() && lastSignalAt > 0L
                ? "Signal vor " + Math.max(0L, (System.currentTimeMillis() - lastSignalAt) / 1000L) + " s"
                : null;

        w = Math.max(164, mc.textRenderer.getWidth(label) + 18);
        if (message != null) w = Math.max(w, mc.textRenderer.getWidth(message) + 18);
        if (age != null) w = Math.max(w, mc.textRenderer.getWidth(age) + 18);
        h = 20 + (message == null ? 0 : 12) + (age == null ? 0 : 11);

        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, color);
        ctx.fill(2, 0, w, 1, Theme.withAlpha(color, 150));
        ctx.drawText(mc.textRenderer, label, 9, 5, color, true);

        int y = 19;
        if (message != null) {
            ctx.drawText(mc.textRenderer, message, 9, y, Theme.TEXT, false);
            y += 12;
        }
        if (age != null) {
            ctx.drawText(mc.textRenderer, age, 9, y, Theme.TEXT_DIM, false);
        }
    }

    private String fit(String text, int maxWidth) {
        if (mc.textRenderer.getWidth(text) <= maxWidth) return text;
        String shortened = text;
        while (shortened.length() > 1 && mc.textRenderer.getWidth(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }
}

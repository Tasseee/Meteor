package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reports explicit anti-cheat state announcements received as game/system messages.
 * It deliberately never infers that an anti-cheat is disabled from lag or corrections.
 */
public final class AntiCheatMonitor extends Module {
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
        super("AntiCheatMonitor", "Erkennt ausdrueckliche Anti-Cheat-Statusmeldungen des Servers.", Category.MISC);
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
        lastSignal = raw.length() > 100 ? raw.substring(0, 100) + "..." : raw;
        lastSignalAt = System.currentTimeMillis();
    }

    @Override
    public void onWorldLeave() {
        status = Status.UNKNOWN;
        lastSignal = "Keine eindeutige Servermeldung erkannt";
        lastSignalAt = 0L;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!showOverlay.get()) return;
        String label = switch (status) {
            case ACTIVE -> "Anti-Cheat: ACTIVE (Servermeldung)";
            case INACTIVE -> "Anti-Cheat: INACTIVE (Servermeldung)";
            case UNKNOWN -> "Anti-Cheat: UNKNOWN";
        };
        int color = switch (status) {
            case ACTIVE -> 0xFF55D68B;
            case INACTIVE -> 0xFFFF657A;
            case UNKNOWN -> 0xFFFFC66D;
        };
        int x = 8;
        int y = 72;
        ctx.drawText(mc.textRenderer, label, x, y, color, true);
        if (showLastSignal.get() && lastSignalAt > 0L) {
            long seconds = Math.max(0L, (System.currentTimeMillis() - lastSignalAt) / 1000L);
            ctx.drawText(mc.textRenderer, "Letztes Signal vor " + seconds + " s", x, y + 11, 0xFFE8EAF2, true);
        }
    }
}

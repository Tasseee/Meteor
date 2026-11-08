package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Detects likely staff from client-visible tab-list formatting only.
 * This is heuristic: a rank marker is not proof of staff permissions and hidden players cannot be detected.
 */
public final class StaffWatch extends Module {
    /** Optional manual overrides; automatic detection works without this list. */
    public final ListSetting staffNames = add(new ListSetting("Zusätzliche Namen (optional)"));

    private static final Pattern STAFF_ROLE = Pattern.compile(
            "(?i)(?<![a-z])(?:owner|co-owner|admin|administrator|sr\\.?\\s*admin|jr\\.?\\s*admin|moderator|mod|sr\\.?\\s*mod|jr\\.?\\s*mod|helper|staff|developer|support)(?![a-z])");
    private static final Pattern STAFF_STAR = Pattern.compile("[★☆✦✧✪✯✰⭐✵✶✷✸✹✺]");

    public record DetectedStaff(String name, String reason) {}

    public StaffWatch() {
        super("StaffWatch", "Erkennt wahrscheinliche Staff-Ränge anhand sichtbarer Tablist-Marker; keine Garantie.", Category.MISC);
    }

    /** Returns candidates visible in the tab list, deduplicated by username. */
    public List<DetectedStaff> getDetectedStaff() {
        if (mc.getNetworkHandler() == null) return List.of();
        Map<String, DetectedStaff> found = new LinkedHashMap<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() == null || entry.getProfile().name() == null) continue;
            String name = entry.getProfile().name();
            String prefix = "";
            Team team = entry.getScoreboardTeam();
            if (team != null && team.getPrefix() != null) prefix = team.getPrefix().getString();
            String display = entry.getDisplayName() == null ? "" : entry.getDisplayName().getString();

            String evidence = null;
            // DonutSMP community references describe a star marker in the tab list.
            if (STAFF_STAR.matcher(prefix).find() || STAFF_STAR.matcher(display).find()) {
                evidence = "Stern-Marker";
            } else if (STAFF_ROLE.matcher(prefix).find()) {
                evidence = "Rang-Präfix";
            }
            if (evidence != null) found.putIfAbsent(name.toLowerCase(Locale.ROOT), new DetectedStaff(name, evidence));
        }
        return new ArrayList<>(found.values());
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;

        List<DetectedStaff> detected = getDetectedStaff();
        Map<String, String> names = new LinkedHashMap<>();
        for (DetectedStaff staff : detected) names.put(staff.name(), staff.reason());
        for (String name : staffNames.get()) names.putIfAbsent(name, "manuell");

        int x = 8, y = 44;
        ctx.drawText(mc.textRenderer, "StaffWatch · automatische Tablist-Hinweise", x, y, Theme.ACCENT, true);
        y += 12;
        if (names.isEmpty()) {
            ctx.drawText(mc.textRenderer, "Keine eindeutigen Marker sichtbar", x, y, 0xFFFFC857, true);
            y += 11;
        }
        int count = 0;
        for (Map.Entry<String, String> candidate : names.entrySet()) {
            if (count++ >= 8) break;
            String name = candidate.getKey();
            String reason = candidate.getValue();
            ctx.drawText(mc.textRenderer, name + " — " + reason, x, y,
                    reason.equals("manuell") ? 0xFFB7B7C8 : 0xFF6EE7A8, true);
            y += 11;
        }
        if (names.size() > 0) {
            ctx.drawText(mc.textRenderer, "Hinweis: nur sichtbare Marker; Fehlalarme möglich", x, y, 0xFFFFC857, false);
        }
    }
}

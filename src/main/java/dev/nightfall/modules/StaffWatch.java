package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-only staff watch. It reports only data visible to this client (tab list and loaded entities).
 * Missing entries are explicitly labelled as inconclusive; this is not a vanish detector.
 */
public final class StaffWatch extends Module {
    public final ListSetting staffNames = add(new ListSetting("Beobachtete Namen"));

    public StaffWatch() {
        super("StaffWatch", "Clientseitige Präsenzhinweise für selbst eingetragene Namen; keine Vanish-Bestätigung.", Category.MISC);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null || staffNames.get().isEmpty()) return;
        Set<String> tab = new HashSet<>();
        if (mc.getNetworkHandler() != null) {
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && entry.getProfile().name() != null)
                    tab.add(entry.getProfile().getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Set<String> loaded = new HashSet<>();
        for (var player : mc.world.getPlayers()) loaded.add(player.getGameProfile().name().toLowerCase(java.util.Locale.ROOT));

        int x = 8, y = 44;
        ctx.drawText(mc.textRenderer, "StaffWatch · Client-Hinweise", x, y, Theme.ACCENT, true);
        y += 12;
        int count = 0;
        for (String name : staffNames.get()) {
            if (count++ >= 8) break;
            String status;
            int color;
            boolean inWorld = loaded.contains(name);
            boolean inTab = tab.contains(name);
            if (inWorld && inTab) { status = "Entity geladen + Tab-Eintrag"; color = 0xFF6EE7A8; }
            else if (inWorld) { status = "Entity geladen · kein Tab-Eintrag"; color = 0xFFFFC857; }
            else if (inTab) { status = "Tab-Eintrag · Entity nicht geladen"; color = 0xFFFFC857; }
            else { status = "weder Entity noch Tab · unklar"; color = 0xFFB7B7C8; }
            ctx.drawText(mc.textRenderer, name + " — " + status, x, y, color, true);
            y += 11;
        }
    }
}

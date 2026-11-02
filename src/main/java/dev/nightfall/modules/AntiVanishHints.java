package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Displays only client-observable list/entity presence. It intentionally does not
 * claim to detect vanish: absence can have many causes and is not proof of staff activity.
 */
public final class AntiVanishHints extends Module {
    public AntiVanishHints() {
        super("AntiVanishHints", "Unverbindliche Client-Hinweise; Abwesenheit beweist keinen Vanish.", Category.MISC);
    }

    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        Set<String> tab = new HashSet<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() != null && entry.getProfile().getName() != null)
                tab.add(entry.getProfile().getName().toLowerCase(Locale.ROOT));
        }
        Set<String> loaded = new HashSet<>();
        mc.world.getPlayers().forEach(p -> loaded.add(p.getGameProfile().getName().toLowerCase(Locale.ROOT)));

        StaffWatch staff = dev.nightfall.module.ModuleManager.get(StaffWatch.class);
        if (staff.staffNames.get().isEmpty()) return;
        int x = 8, y = 90;
        ctx.drawText(mc.textRenderer, "AntiVanish · Beobachtbare Hinweise", x, y, Theme.ACCENT, true);
        y += 12;
        ctx.drawText(mc.textRenderer, "Abwesenheit ist kein Vanish-Beweis", x, y, 0xFFFFC857, true);
        y += 12;
        int shown = 0;
        for (String name : staff.staffNames.get()) {
            boolean inTab = tab.contains(name);
            boolean inWorld = loaded.contains(name);
            String signal;
            int color;
            if (inTab && inWorld) { signal = "Tab + Entity sichtbar"; color = 0xFF6EE7A8; }
            else if (inTab) { signal = "Tab da, Entity nicht geladen"; color = 0xFFFFC857; }
            else if (inWorld) { signal = "Entity da, Tab-Eintrag fehlt"; color = 0xFFFFC857; }
            else { signal = "keine sichtbaren Daten · unklar"; color = 0xFFB7B7C8; }
            ctx.drawText(mc.textRenderer, name + " · " + signal, x, y, color, false);
            y += 11;
            if (++shown >= 5) break;
        }
    }
}

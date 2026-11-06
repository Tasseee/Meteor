package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerEsp extends EntityScanEsp {
    private final BoolSetting showRank = add(new BoolSetting("Rang anzeigen", true));

    public PlayerEsp() { super("PlayerESP", "Markiert andere Spieler und zeigt bekannte Server-Raenge.", 256, 512, 0xEF4444); }

    @Override
    protected boolean accept(Entity e) { return e instanceof PlayerEntity; }

    @Override
    protected String nameOf(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || !showRank.get()) return entity.getName().getString();
        if (mc.getNetworkHandler() == null) return player.getName().getString();
        String username = player.getName().getString();
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry != null && entry.getDisplayName() != null) {
            String decorated = entry.getDisplayName().getString().trim();
            if (!decorated.isEmpty() && !decorated.equalsIgnoreCase(username))
                return decorated.toLowerCase(java.util.Locale.ROOT).contains(username.toLowerCase(java.util.Locale.ROOT))
                        ? decorated : decorated + " " + username;
        }

        // Some servers expose prefixes through scoreboard teams instead of tab display names.
        var team = player.getScoreboardTeam();
        if (team != null) {
            String prefix = team.getPrefix().getString().trim();
            if (!prefix.isEmpty()) return prefix + " " + username;
        }
        return username;
    }
}

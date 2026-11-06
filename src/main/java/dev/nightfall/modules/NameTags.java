package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Locale;

/** 2D player nametags with optional rank and local-only self-tag hiding. */
public final class NameTags extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 128, 8, 512, 8));
    private final BoolSetting showRank = add(new BoolSetting("Rang anzeigen", true));
    private final BoolSetting showDistance = add(new BoolSetting("Distanz anzeigen", true));
    private final BoolSetting showHealth = add(new BoolSetting("Leben anzeigen", true));
    private final BoolSetting hideOwnName = add(new BoolSetting("Eigenen Namen lokal verstecken", true));
    private final double[] point = new double[2];

    public NameTags() {
        super("NameTags", "Zeigt Spielernamen und bekannte Ränge; versteckt optional deinen eigenen Namen nur lokal.", Category.VISUALS);
    }

    public static String formattedName(PlayerEntity player, boolean showRank) {
        String username = player.getName().getString();
        if (!showRank || mc.getNetworkHandler() == null) return username;
        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
        if (entry != null && entry.getDisplayName() != null) {
            String decorated = entry.getDisplayName().getString().trim();
            if (!decorated.isEmpty() && !decorated.equalsIgnoreCase(username))
                return decorated.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT))
                        ? decorated : decorated + " " + username;
        }

        // Fall back to the visible scoreboard-team prefix when tab display names are plain.
        var team = player.getScoreboardTeam();
        if (team != null) {
            String prefix = team.getPrefix().getString().trim();
            if (!prefix.isEmpty()) return prefix + " " + username;
        }
        return username;
    }

    public static boolean shouldHideOwnName(Entity entity) {
        if (entity != mc.player) return false;
        try {
            NameTags tags = dev.nightfall.module.ModuleManager.get(NameTags.class);
            return tags.isEnabled() && tags.hideOwnName.get();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;
        double maxDistanceSq = range.get() * range.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved()) continue;
            double dx = player.getX() - Projection.eyeX();
            double dy = player.getY() - Projection.eyeY();
            double dz = player.getZ() - Projection.eyeZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistanceSq) continue;
            double distance = Math.sqrt(distSq);
            if (!Projection.project(player.getX(), player.getY() + player.getHeight() + 0.25,
                    player.getZ(), point)) continue;

            StringBuilder text = new StringBuilder(formattedName(player, showRank.get()));
            if (showDistance.get()) text.append(" ").append((int) distance).append("m");
            if (showHealth.get()) text.append(" ").append(Math.round(player.getHealth())).append("hp");
            String label = text.toString();
            int width = mc.textRenderer.getWidth(label);
            int x = (int) point[0], y = (int) point[1];
            ctx.fill(x - width / 2 - 3, y - 2, x + width / 2 + 3, y + 10, 0xA8101018);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, label, x, y, 0xFFFFFFFF);
        }
    }
}

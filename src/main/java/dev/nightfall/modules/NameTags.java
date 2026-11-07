package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 2D player name cards with optional rank, distance, health and local self-tag hiding. */
public final class NameTags extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 128, 8, 512, 8));
    private final NumberSetting maxTags = add(new NumberSetting("Max. Tags", 32, 4, 64, 4));
    private final BoolSetting showRank = add(new BoolSetting("Rang anzeigen", true));
    private final BoolSetting showDistance = add(new BoolSetting("Distanz anzeigen", true));
    private final BoolSetting showHealth = add(new BoolSetting("Leben als Text", true));
    private final BoolSetting healthBar = add(new BoolSetting("Lebensbalken", true));
    private final BoolSetting hideOwnName = add(new BoolSetting("Eigenen Namen lokal verstecken", true));
    private final double[] point = new double[2];

    public NameTags() {
        super("NameTags", "Zeigt Spieler als kompakte Namenskarten mit Rang, Distanz und Lebensanzeige.", Category.VISUALS);
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
        double maxDistanceSquared = range.get() * range.get();
        List<PlayerEntity> players = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isRemoved() || !player.isAlive()) continue;
            if (player.squaredDistanceTo(mc.player) > maxDistanceSquared) continue;
            players.add(player);
        }
        players.sort(Comparator.comparingDouble(player -> player.squaredDistanceTo(mc.player)));

        int shown = 0;
        for (PlayerEntity player : players) {
            if (shown >= maxTags.getInt()) break;
            double dx = player.getX() - Projection.eyeX();
            double dy = player.getY() - Projection.eyeY();
            double dz = player.getZ() - Projection.eyeZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > maxDistanceSquared) continue;

            if (!Projection.project(player.getX(), player.getY() + player.getHeight() + 0.25,
                    player.getZ(), point)) continue;

            String name = formattedName(player, showRank.get());
            StringBuilder subtitle = new StringBuilder();
            if (showDistance.get()) subtitle.append(Math.round(Math.sqrt(distanceSquared))).append(" m");
            if (showHealth.get()) {
                if (subtitle.length() > 0) subtitle.append("  ·  ");
                subtitle.append(String.format(Locale.ROOT, "%.1f HP", player.getHealth()));
            }

            int nameWidth = mc.textRenderer.getWidth(name);
            int subtitleWidth = subtitle.length() == 0 ? 0 : mc.textRenderer.getWidth(subtitle.toString());
            int cardWidth = Math.max(nameWidth, subtitleWidth) + 14;
            int cardHeight = 13 + (subtitle.length() > 0 ? 9 : 0) + (healthBar.get() ? 4 : 0);
            int centerX = (int) Math.round(point[0]);
            int topY = (int) Math.round(point[1]) - cardHeight - 3;
            int left = centerX - cardWidth / 2;

            // Layered background and slim accent edge keep text readable on any world.
            ctx.fill(left - 1, topY - 1, left + cardWidth + 1, topY + cardHeight + 1, 0xA0000000);
            ctx.fill(left, topY, left + cardWidth, topY + cardHeight, 0xD910141D);
            ctx.fill(left, topY, left + 2, topY + cardHeight, Theme.ACCENT);
            ctx.drawText(mc.textRenderer, name, left + 7, topY + 3, Theme.TEXT, true);

            int nextY = topY + 13;
            if (subtitle.length() > 0) {
                ctx.drawText(mc.textRenderer, subtitle.toString(), left + 7, nextY, Theme.TEXT_DIM, false);
                nextY += 9;
            }

            if (healthBar.get()) {
                float ratio = player.getMaxHealth() <= 0 ? 0f
                        : Math.max(0f, Math.min(1f, player.getHealth() / player.getMaxHealth()));
                int barRight = left + cardWidth - 6;
                int barLeft = left + 6;
                int barColor = 0xFF000000 | Theme.lerp(0xFFFF5D6C, 0xFF42D392, ratio);
                ctx.fill(barLeft, nextY + 1, barRight, nextY + 3, 0xFF303541);
                ctx.fill(barLeft, nextY + 1,
                        barLeft + Math.round((barRight - barLeft) * ratio), nextY + 3, barColor);
            }
            shown++;
        }
    }
}

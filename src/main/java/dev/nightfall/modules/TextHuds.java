package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.util.Theme;
import dev.nightfall.util.HudStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Einzeilige HUD-Elemente. */
public final class TextHuds {
    private TextHuds() {}

    abstract static class Line extends HudModule {
        private final int defY;
        Line(String name, String desc, int defY) { super(name, desc); this.defY = defY; }
        abstract String text();
        @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, defY}; }
        @Override protected void draw(DrawContext ctx) {
            String t = text();
            w = mc.textRenderer.getWidth(t) + 10; h = 12;
            HudStyle.panel(ctx, w, h);
            ctx.drawText(mc.textRenderer, t, 6, 2, Theme.TEXT, true);
        }
    }

    public static class Fps extends Line {
        public Fps() { super("FPS", "Zeigt die Bildrate.", 98); }
        @Override String text() { return mc.getCurrentFps() + " FPS"; }
    }

    public static class Ping extends Line {
        public Ping() { super("Ping", "Zeigt die Latenz zum Server.", 110); }
        @Override String text() {
            if (mc.player == null || mc.getNetworkHandler() == null) return "Ping -";
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            return e == null ? "Ping -" : e.getLatency() + " ms";
        }
    }

    public static class Clock extends Line {
        private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm:ss");
        public Clock() { super("Clock", "Systemzeit.", 122); }
        @Override String text() { return LocalTime.now().format(F); }
    }

    public static class Direction extends Line {
        public Direction() { super("Direction", "Blickrichtung.", 134); }
        @Override String text() {
            if (mc.player == null) return "-";
            float yaw = mc.player.getYaw();
            String[] n = {"S (+Z)", "SW", "W (-X)", "NW", "N (-Z)", "NE", "E (+X)", "SE"};
            int i = Math.floorMod(Math.round(yaw / 45f), 8);
            return n[i] + String.format("  %.0f", ((yaw % 360) + 360) % 360);
        }
    }

    public static class BiomeHud extends Line {
        public BiomeHud() { super("Biome", "Aktuelles Biom.", 146); }
        @Override String text() {
            if (mc.world == null || mc.player == null) return "-";
            return mc.world.getBiome(mc.player.getBlockPos()).getKey()
                    .map(k -> k.getValue().getPath()).orElse("?");
        }
    }
}

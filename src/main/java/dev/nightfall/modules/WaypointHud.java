package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class WaypointHud extends HudModule {
    private final List<Waypoint> tmp = new ArrayList<>();

    public WaypointHud() { super("WaypointList", "Naechste Waypoints mit Distanz."); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, 164 + 70}; }

    @Override
    protected void draw(DrawContext ctx) {
        if (mc.player == null) return;
        tmp.clear();
        String dim = Storage.dim();
        for (Waypoint wp : WaypointManager.all()) if (wp.dim.equals(dim)) tmp.add(wp);
        double px = mc.player.getX(), pz = mc.player.getZ();
        tmp.sort((a, b) -> Double.compare(Math.hypot(a.x - px, a.z - pz), Math.hypot(b.x - px, b.z - pz)));
        int n = Math.min(5, tmp.size());
        String[] lines = new String[n];
        int widest = mc.textRenderer.getWidth("Waypoints");
        for (int i = 0; i < n; i++) {
            Waypoint waypoint = tmp.get(i);
            lines[i] = waypoint.name + " " + (int) Math.hypot(waypoint.x - px, waypoint.z - pz) + "m";
            widest = Math.max(widest, mc.textRenderer.getWidth(lines[i]));
        }
        w = Math.max(120, Math.min(210, widest + 12));
        h = 12 + n * 10;
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, "Waypoints", 6, 2, Theme.TEXT_DIM, false);
        for (int i = 0; i < n; i++) {
            Waypoint waypoint = tmp.get(i);
            String line = trim(lines[i], w - 12);
            ctx.drawText(mc.textRenderer, line, 6, 12 + i * 10, 0xFF000000 | waypoint.color, false);
        }
    }

    private String trim(String text, int maxWidth) {
        while (mc.textRenderer.getWidth(text) > maxWidth && text.length() > 1) text = text.substring(0, text.length() - 2) + "…";
        return text;
    }
}

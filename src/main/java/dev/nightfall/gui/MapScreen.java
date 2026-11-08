package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Vollbildkarte (M). Ziehen = verschieben, Mausrad = zoomen, 1/2/3 = Filter (alle/verdaechtig/verschwunden),
 * C = auf Spieler zentrieren, Rechtsklick = Waypoint setzen bzw. loeschen (auf Waypoint), M/Esc = schliessen.
 * Die Karte zeigt die Ergebnisse des BaseFinders (geladene + gespeicherte Markierungen), keine Terrainfarben.
 */
public class MapScreen extends Screen {
    private static double cx, cz;
    private static double zoom = 12;      // Pixel pro Chunk
    private static int filter = 1;
    private static boolean init;

    private boolean prevL, prevR, prevM, prevC, prev1, prev2, prev3;
    private double lastMx, lastMy;

    public MapScreen() {
        super(Text.literal("Map"));
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!init && mc.player != null) { cx = mc.player.getX(); cz = mc.player.getZ(); init = true; }
        long h = mc.getWindow().getHandle();
        prevL = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        prevR = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        prevM = GLFW.glfwGetKey(h, Config.mapKey) == GLFW.GLFW_PRESS;
        prevC = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        prev1 = prev2 = prev3 = true; // Oeffnen soll keine Filter schalten
        prev1 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS;
        prev2 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS;
        prev3 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        double old = zoom;
        zoom = Math.max(2, Math.min(64, zoom * (vAmount > 0 ? 1.2 : 1 / 1.2)));
        // Zoom zum Mauszeiger
        double bx = cx + (mx - width / 2.0) / old * 16.0;
        double bz = cz + (my - height / 2.0) / old * 16.0;
        cx = bx - (mx - width / 2.0) / zoom * 16.0;
        cz = bz - (my - height / 2.0) / zoom * 16.0;
        return true;
    }

    private double sx(double bx) { return width / 2.0 + (bx - cx) / 16.0 * zoom; }
    private double sy(double bz) { return height / 2.0 + (bz - cz) / 16.0 * zoom; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean mk = GLFW.glfwGetKey(h, Config.mapKey) == GLFW.GLFW_PRESS;
        boolean ck = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        boolean k1 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_1) == GLFW.GLFW_PRESS;
        boolean k2 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_2) == GLFW.GLFW_PRESS;
        boolean k3 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_3) == GLFW.GLFW_PRESS;
        boolean clickR = r && !prevR;
        if (mk && !prevM) { close(); return; }
        if (k1 && !prev1) filter = 1;
        if (k2 && !prev2) filter = 2;
        if (k3 && !prev3) filter = 3;
        if (ck && !prevC && mc.player != null) { cx = mc.player.getX(); cz = mc.player.getZ(); }
        if (l && prevL) { cx -= (mx - lastMx) / zoom * 16.0; cz -= (my - lastMy) / zoom * 16.0; }
        prevL = l; prevR = r; prevM = mk; prevC = ck; prev1 = k1; prev2 = k2; prev3 = k3;
        lastMx = mx; lastMy = my;

        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        ctx.fill(0, 0, width, height, 0xF00B0B0F);

        // Chunk-Zellen
        int cell = Math.max(1, (int) Math.round(zoom));
        for (ChunkResult res : bf.results()) {
            if (res.status == ChunkResult.Status.HIDDEN) continue;
            boolean show = switch (filter) {
                case 2 -> res.status == ChunkResult.Status.SUS;
                case 3 -> res.status == ChunkResult.Status.GONE;
                default -> true;
            };
            if (!show) continue;
            int x0 = (int) Math.round(sx(res.cx * 16.0)), y0 = (int) Math.round(sy(res.cz * 16.0));
            if (x0 + cell < 0 || y0 + cell < 0 || x0 > width || y0 > height) continue;
            int rgb = switch (res.status) {
                case SUS -> Theme.lerp(0x9B5CF6, 0xFF3B5C, (float) (res.score / (bf.thresholdValue() * 3.0)));
                case GONE -> 0x2F6FDB;
                default -> 0x24242E;
            };
            ctx.fill(x0, y0, x0 + cell, y0 + cell, 0xFF000000 | rgb);
        }

        // Gitter
        if (zoom >= 8) {
            int c0 = (int) Math.floor((cx - width / 2.0 / zoom * 16.0) / 16.0), c1 = (int) Math.ceil((cx + width / 2.0 / zoom * 16.0) / 16.0);
            int z0 = (int) Math.floor((cz - height / 2.0 / zoom * 16.0) / 16.0), z1 = (int) Math.ceil((cz + height / 2.0 / zoom * 16.0) / 16.0);
            for (int c = c0; c <= c1; c++) { int x = (int) Math.round(sx(c * 16.0)); ctx.fill(x, 0, x + 1, height, c % 8 == 0 ? 0x30FFFFFF : 0x12FFFFFF); }
            for (int z = z0; z <= z1; z++) { int y = (int) Math.round(sy(z * 16.0)); ctx.fill(0, y, width, y + 1, z % 8 == 0 ? 0x30FFFFFF : 0x12FFFFFF); }
        }

        // Waypoints
        String dim = Storage.dim();
        Waypoint hoverWp = null;
        for (Waypoint w : WaypointManager.all()) {
            if (!w.dim.equals(dim)) continue;
            int x = (int) sx(w.x + 0.5), y = (int) sy(w.z + 0.5);
            if (x < -10 || y < -10 || x > width + 10 || y > height + 10) continue;
            boolean fake = dev.nightfall.modules.FakeBases.CATEGORY.equalsIgnoreCase(w.category);
            int markerColor = fake ? 0xFFFF5F75 : (0xFF000000 | w.color);
            ctx.fill(x - 4, y - 4, x + 5, y + 5, markerColor);
            ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            if (zoom >= 6) ctx.drawText(mc.textRenderer, (fake ? "[TEST/DECOY] " : "") + w.name, x + 6, y - 4, fake ? 0xFFFF8797 : Theme.TEXT, true);
            if (Math.abs(mx - x) <= 5 && Math.abs(my - y) <= 5) hoverWp = w;
        }

        // Spieler
        if (mc.player != null) {
            int px = (int) sx(mc.player.getX()), py = (int) sy(mc.player.getZ());
            ctx.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
            double yr = Math.toRadians(mc.player.getYaw());
            int fx = (int) (px - Math.sin(yr) * 8), fy = (int) (py + Math.cos(yr) * 8);
            ctx.fill(fx - 1, fy - 1, fx + 2, fy + 2, Theme.ACCENT);
        }

        // Hover-Info
        double bx = cx + (mx - width / 2.0) / zoom * 16.0, bz = cz + (my - height / 2.0) / zoom * 16.0;
        int hcx = (int) Math.floor(bx / 16.0), hcz = (int) Math.floor(bz / 16.0);
        ChunkResult hr = bf.get(hcx, hcz);
        String coords = "Block " + (int) Math.floor(bx) + ", " + (int) Math.floor(bz) + "   Chunk " + hcx + ", " + hcz;
        ctx.drawText(mc.textRenderer, coords, 8, 8, Theme.TEXT, true);
        if (hr != null && hr.status != ChunkResult.Status.CLEAN && hr.status != ChunkResult.Status.HIDDEN) {
            String t1 = (hr.status == ChunkResult.Status.SUS ? "SUS" : "SUS WEG") + "  Score " + String.format("%.1f", hr.score);
            String t2 = String.join(", ", hr.reasons);
            String t3 = hr.foundAtMillis > 0 ? "erkannt vor " + ago(System.currentTimeMillis() - hr.foundAtMillis) : "";
            int tw = Math.max(mc.textRenderer.getWidth(t1), Math.max(mc.textRenderer.getWidth(t2), mc.textRenderer.getWidth(t3))) + 10;
            int tx = Math.min(mx + 10, width - tw - 4), ty = Math.min(my + 10, height - 40);
            ctx.fill(tx, ty, tx + tw, ty + 34, 0xF0141418);
            ctx.fill(tx, ty, tx + 2, ty + 34, Theme.ACCENT);
            ctx.drawText(mc.textRenderer, t1, tx + 6, ty + 3, Theme.TEXT, false);
            ctx.drawText(mc.textRenderer, t2, tx + 6, ty + 13, Theme.TEXT_DIM, false);
            ctx.drawText(mc.textRenderer, t3, tx + 6, ty + 23, Theme.TEXT_DIM, false);
        }

        // Waypoint setzen/loeschen
        if (clickR) {
            if (hoverWp != null) WaypointManager.remove(hoverWp);
            else if (mc.player != null) WaypointManager.add("WP" + (WaypointManager.all().size() + 1), "Default",
                    (int) Math.floor(bx), mc.player.getBlockY(), (int) Math.floor(bz));
        }

        // Legende + Filter
        String[] names = {"Alle", "Verdaechtig", "Verschwunden"};
        ctx.fill(6, height - 52, 150, height - 6, 0xE6141418);
        ctx.fill(6, height - 52, 8, height - 6, Theme.ACCENT);
        legend(ctx, mc, 14, height - 46, 0x9B5CF6, "SUS (Score steigt: rot)");
        legend(ctx, mc, 14, height - 34, 0x2F6FDB, "SUS WEG");
        legend(ctx, mc, 14, height - 22, 0x24242E, "gescannt, unauffaellig");
        String f = "Filter [1/2/3]: " + names[filter - 1] + "   C: zentrieren   RMB: Waypoint";
        ctx.drawText(mc.textRenderer, f, width - mc.textRenderer.getWidth(f) - 8, 8, Theme.TEXT_DIM, true);
    }

    private static void legend(DrawContext ctx, MinecraftClient mc, int x, int y, int rgb, String t) {
        ctx.fill(x, y, x + 8, y + 8, 0xFF000000 | rgb);
        ctx.drawText(mc.textRenderer, t, x + 12, y, Theme.TEXT, false);
    }

    private static String ago(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "min";
        return (s / 3600) + "h";
    }
}

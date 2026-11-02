package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

public class Minimap extends HudModule {
    private final NumberSetting size    = add(new NumberSetting("Groesse", 96, 50, 220, 2));
    private final NumberSetting range   = add(new NumberSetting("Reichweite (Chunks)", 8, 2, 24, 1));
    private final NumberSetting opacity = add(new NumberSetting("Transparenz", 0.75, 0.2, 1.0, 0.05));
    private final BoolSetting   rotate  = add(new BoolSetting("Rotieren", true));
    private final BoolSetting   coords  = add(new BoolSetting("Koordinaten", true));

    public Minimap() {
        super("Minimap", "Chunk-Radar mit SUS-/SUS-WEG-Markierungen.");
        scalable = false; // Scissor arbeitet in Bildschirmkoordinaten -> Groesse ueber Einstellung
        setEnabled(true);
    }

    @Override protected int[] defaultPos(int sw, int sh) { int s = size.getInt(); return new int[]{sw - s - 6, sh - s - 6}; }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        int s = size.getInt();
        w = s; h = s;
        int[] p = pos(ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        int x = p[0], y = p[1];
        int a = (int) (opacity.get() * 255);
        float cx = x + s / 2f, cy = y + s / 2f;
        int r = range.getInt();
        float cell = s / (2f * r + 1f);

        double px = mc.player.getX(), pz = mc.player.getZ();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        float fx = (float) ((px - pcx * 16.0) / 16.0), fz = (float) ((pz - pcz * 16.0) / 16.0);
        float yaw = mc.player.getYaw();
        double theta = rotate.get() ? Math.toRadians(180.0 - yaw) : 0.0;

        ctx.fill(x, y, x + s, y + s, Theme.withAlpha(Theme.MAP_BG, a));
        ctx.enableScissor(x, y, x + s, y + s);

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        if (rotate.get()) m.rotate((float) theta);
        m.translate(-fx * cell, -fz * cell);

        int ext = rotate.get() ? (int) Math.ceil(r * 1.5) : r;
        for (int dz = -ext; dz <= ext; dz++) {
            for (int dx = -ext; dx <= ext; dx++) {
                ChunkResult res = bf.get(pcx + dx, pcz + dz);
                int rgb;
                if (res == null) rgb = 0x1A1A22;
                else switch (res.status) {
                    case SUS -> rgb = Theme.lerp(0x9B5CF6, 0xFF3B5C, (float) (res.score / (bf.thresholdValue() * 3.0)));
                    case GONE -> rgb = 0x2F6FDB;
                    default -> rgb = 0x2C2C38;
                }
                int x0 = Math.round(dx * cell), x1 = Math.round((dx + 1) * cell);
                int z0 = Math.round(dz * cell), z1 = Math.round((dz + 1) * cell);
                ctx.fill(x0, z0, x1 - 1, z1 - 1, Theme.withAlpha(rgb, Math.min(255, a + 30)));
            }
        }
        m.popMatrix();
        ctx.disableScissor();

        ctx.fill((int) cx - 1, (int) cy - 1, (int) cx + 2, (int) cy + 2, 0xFFFFFFFF);
        int fxm, fym;
        if (rotate.get()) { fxm = (int) cx; fym = (int) cy - 8; }
        else {
            double yr = Math.toRadians(yaw);
            fxm = (int) (cx - Math.sin(yr) * 8); fym = (int) (cy + Math.cos(yr) * 8);
        }
        ctx.fill(fxm, fym, fxm + 2, fym + 2, Theme.ACCENT);

        double rad = s / 2.0 - 7;
        int nx = (int) (cx + Math.sin(theta) * rad) - 2, ny = (int) (cy - Math.cos(theta) * rad) - 3;
        ctx.drawText(mc.textRenderer, "N", nx, ny, 0xFFFF5C7A, true);

        ctx.fill(x - 1, y - 1, x + s + 1, y, Theme.ACCENT);
        ctx.fill(x - 1, y + s, x + s + 1, y + s + 1, Theme.ACCENT);
        ctx.fill(x - 1, y, x, y + s, Theme.ACCENT);
        ctx.fill(x + s, y, x + s + 1, y + s, Theme.ACCENT);

        if (coords.get()) {
            String t = mc.player.getBlockX() + ", " + mc.player.getBlockZ();
            ctx.drawText(mc.textRenderer, t, x + s - mc.textRenderer.getWidth(t), y - 11, Theme.TEXT, true);
        }
    }

    @Override protected void draw(DrawContext ctx) { }
}

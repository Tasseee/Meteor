package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/**
 * Compact chunk radar. BaseFinder lookups are cached on client ticks so large
 * minimap ranges do not perform thousands of map lookups on every rendered frame.
 */
public class Minimap extends HudModule {
    private final NumberSetting size    = add(new NumberSetting("Groesse", 96, 50, 220, 2));
    private final NumberSetting range   = add(new NumberSetting("Reichweite (Chunks)", 8, 2, 24, 1));
    private final NumberSetting opacity = add(new NumberSetting("Transparenz", 0.75, 0.2, 1.0, 0.05));
    private final BoolSetting   rotate  = add(new BoolSetting("Rotieren", true));
    private final BoolSetting   coords  = add(new BoolSetting("Koordinaten", true));

    private byte[][] cachedStatus = new byte[0][0];
    private float[][] cachedScore = new float[0][0];
    private int cachedCenterX = Integer.MIN_VALUE;
    private int cachedCenterZ = Integer.MIN_VALUE;
    private int cachedExtent = -1;
    private int cachedRange = -1;
    private boolean cachedRotate;
    private int refreshTimer;

    public Minimap() {
        super("Minimap", "Chunk-Radar mit SUS-/SUS-WEG-Markierungen.");
        scalable = false; // Scissor arbeitet in Bildschirmkoordinaten -> Groesse ueber Einstellung
        setEnabled(true);
    }

    @Override
    protected int[] defaultPos(int sw, int sh) {
        int s = size.getInt();
        return new int[]{sw - s - 6, sh - s - 6};
    }

    @Override
    protected void onEnable() {
        invalidateCache();
    }

    @Override
    protected void onDisable() {
        invalidateCache();
    }

    private void invalidateCache() {
        cachedStatus = new byte[0][0];
        cachedScore = new float[0][0];
        cachedCenterX = Integer.MIN_VALUE;
        cachedCenterZ = Integer.MIN_VALUE;
        cachedExtent = -1;
        cachedRange = -1;
        refreshTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            invalidateCache();
            return;
        }

        int r = range.getInt();
        boolean shouldRotate = rotate.get();
        int ext = shouldRotate ? (int) Math.ceil(r * 1.5) : r;
        int centerX = mc.player.getBlockX() >> 4;
        int centerZ = mc.player.getBlockZ() >> 4;
        boolean dimensionsChanged = cachedCenterX != centerX || cachedCenterZ != centerZ
                || cachedExtent != ext || cachedRange != r || cachedRotate != shouldRotate;
        if (!dimensionsChanged && refreshTimer-- > 0) return;

        refreshTimer = 5;
        refreshCache(ModuleManager.get(BaseFinder.class), centerX, centerZ, r, ext, shouldRotate);
    }

    private void refreshCache(BaseFinder finder, int centerX, int centerZ,
                               int radius, int extent, boolean shouldRotate) {
        int side = extent * 2 + 1;
        byte[][] status = new byte[side][side];
        float[][] score = new float[side][side];

        for (int dz = -extent; dz <= extent; dz++) {
            for (int dx = -extent; dx <= extent; dx++) {
                ChunkResult result = finder.get(centerX + dx, centerZ + dz);
                if (result == null) continue;
                status[dz + extent][dx + extent] = (byte) (result.status.ordinal() + 1);
                score[dz + extent][dx + extent] = (float) result.score;
            }
        }

        cachedStatus = status;
        cachedScore = score;
        cachedCenterX = centerX;
        cachedCenterZ = centerZ;
        cachedExtent = extent;
        cachedRange = radius;
        cachedRotate = shouldRotate;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;
        BaseFinder finder = ModuleManager.get(BaseFinder.class);
        int s = size.getInt();
        w = s;
        h = s;
        int[] p = pos(ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        int x = p[0], y = p[1];
        int alpha = (int) (opacity.get() * 255);
        float cx = x + s / 2f, cy = y + s / 2f;
        int r = range.getInt();
        float cell = s / (2f * r + 1f);

        double px = mc.player.getX(), pz = mc.player.getZ();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        float fx = (float) ((px - pcx * 16.0) / 16.0);
        float fz = (float) ((pz - pcz * 16.0) / 16.0);
        float yaw = mc.player.getYaw();
        boolean shouldRotate = rotate.get();
        double theta = shouldRotate ? Math.toRadians(180.0 - yaw) : 0.0;
        int ext = shouldRotate ? (int) Math.ceil(r * 1.5) : r;

        // A one-frame fallback protects the first frame after toggling or changing
        // range/rotation before the next client tick refreshes the snapshot.
        if (cachedCenterX != pcx || cachedCenterZ != pcz || cachedExtent != ext
                || cachedRange != r || cachedRotate != shouldRotate) {
            refreshCache(finder, pcx, pcz, r, ext, shouldRotate);
        }

        ctx.fill(x, y, x + s, y + s, Theme.withAlpha(Theme.MAP_BG, alpha));
        ctx.enableScissor(x, y, x + s, y + s);

        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, cy);
        if (shouldRotate) matrices.rotate((float) theta);
        matrices.translate(-fx * cell, -fz * cell);

        for (int dz = -ext; dz <= ext; dz++) {
            int row = dz + ext;
            for (int dx = -ext; dx <= ext; dx++) {
                int col = dx + ext;
                int rgb = 0x1A1A22;
                if (row < cachedStatus.length && col < cachedStatus[row].length) {
                    int code = cachedStatus[row][col];
                    if (code > 0) {
                        ChunkResult.Status status = ChunkResult.Status.values()[code - 1];
                        switch (status) {
                            case SUS -> rgb = Theme.lerp(0x9B5CF6, 0xFF3B5C,
                                    Math.max(0f, Math.min(1f,
                                            cachedScore[row][col] / (float) (finder.thresholdValue() * 3.0))));
                            case GONE -> rgb = 0x2F6FDB;
                            case DECOY -> rgb = 0x8B5CF6;
                            default -> rgb = 0x2C2C38;
                        }
                    }
                }
                int x0 = Math.round(dx * cell), x1 = Math.round((dx + 1) * cell);
                int z0 = Math.round(dz * cell), z1 = Math.round((dz + 1) * cell);
                ctx.fill(x0, z0, x1 - 1, z1 - 1,
                        Theme.withAlpha(rgb, Math.min(255, alpha + 30)));
            }
        }
        matrices.popMatrix();
        ctx.disableScissor();

        // Player marker and direction indicator remain screen-space and crisp.
        ctx.fill((int) cx - 2, (int) cy - 2, (int) cx + 3, (int) cy + 3, 0xAA000000);
        ctx.fill((int) cx - 1, (int) cy - 1, (int) cx + 2, (int) cy + 2, 0xFFFFFFFF);
        int fxm, fym;
        if (shouldRotate) {
            fxm = (int) cx;
            fym = (int) cy - 8;
        } else {
            double yawRadians = Math.toRadians(yaw);
            fxm = (int) (cx - Math.sin(yawRadians) * 8);
            fym = (int) (cy + Math.cos(yawRadians) * 8);
        }
        ctx.fill(fxm, fym, fxm + 2, fym + 2, Theme.ACCENT);

        double northRadius = s / 2.0 - 7;
        int northX = (int) (cx + Math.sin(theta) * northRadius) - 2;
        int northY = (int) (cy - Math.cos(theta) * northRadius) - 3;
        ctx.drawText(mc.textRenderer, "N", northX, northY, 0xFFFF5C7A, true);

        // Subtle frame and directional accent.
        ctx.fill(x - 1, y - 1, x + s + 1, y, Theme.ACCENT);
        ctx.fill(x - 1, y + s, x + s + 1, y + s + 1, Theme.withAlpha(Theme.ACCENT, 150));
        ctx.fill(x - 1, y, x, y + s, Theme.withAlpha(Theme.ACCENT, 150));
        ctx.fill(x + s, y, x + s + 1, y + s, Theme.withAlpha(Theme.ACCENT, 150));

        if (coords.get()) {
            String position = mc.player.getBlockX() + ", " + mc.player.getBlockZ();
            ctx.drawText(mc.textRenderer, position,
                    x + s - mc.textRenderer.getWidth(position), y - 11, Theme.TEXT, true);
        }
    }

    @Override
    protected void draw(DrawContext ctx) { }
}

package dev.nightfall.util;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;
import org.joml.Matrix3x2fStack;

public final class Draw {
    /** Oberkante/Mitte der zuletzt gezeichneten Box (fuer Labels). */
    public static float topX, topY, botY;
    private static final double[][] C = new double[8][2];
    private static final boolean[] OK = new boolean[8];

    private Draw() {}

    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2, int argb, int th) {
        line(ctx, x1, y1, x2, y2, argb, th, true);
    }

    /** Clipped line with a subtle one-pixel alpha fringe for a cleaner, antialiased look. */
    public static void line(DrawContext ctx, float x1, float y1, float x2, float y2,
                            int argb, int th, boolean smooth) {
        float[] clipped = clipLine(x1, y1, x2, y2, Projection.width(), Projection.height());
        if (clipped == null) return;
        x1 = clipped[0]; y1 = clipped[1]; x2 = clipped[2]; y2 = clipped[3];
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || len > 20000f || th < 1) return;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x1, y1);
        m.rotate((float) Math.atan2(dy, dx));
        int half = th / 2;
        if (smooth) {
            int alpha = (argb >>> 24) & 0xFF;
            int fringe = (argb & 0x00FFFFFF) | ((alpha * 58 / 255) << 24);
            ctx.fill(-1, -half - 1, (int) Math.ceil(len) + 1, th - half + 1, fringe);
        }
        ctx.fill(0, -half, (int) Math.ceil(len), th - half, argb);
        m.popMatrix();
    }

    /** Draws a box as projected 3D edges or a 2D frame. */
    public static boolean box(DrawContext ctx, Box b, int argb, int th, boolean threeD) {
        return box(ctx, b, argb, th, threeD, true);
    }

    public static boolean box(DrawContext ctx, Box b, int argb, int th, boolean threeD, boolean smooth) {
        int any = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean all = true;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            OK[i] = Projection.project(x, y, z, C[i]);
            if (OK[i]) {
                any++;
                minX = Math.min(minX, (float) C[i][0]); maxX = Math.max(maxX, (float) C[i][0]);
                minY = Math.min(minY, (float) C[i][1]); maxY = Math.max(maxY, (float) C[i][1]);
            } else all = false;
        }
        if (any == 0) return false;
        if (maxX < 0 || maxY < 0 || minX > Projection.width() || minY > Projection.height()) return false;
        topX = Math.max(0, Math.min(Projection.width(), (minX + maxX) / 2f));
        topY = Math.max(0, Math.min(Projection.height(), minY));
        botY = Math.max(0, Math.min(Projection.height(), maxY));
        if (threeD) {
            for (int i = 0; i < 8; i++) {
                if (!OK[i]) continue;
                for (int bit = 1; bit <= 4; bit <<= 1) {
                    if ((i & bit) != 0) continue;
                    int j = i | bit;
                    if (OK[j]) line(ctx, (float) C[i][0], (float) C[i][1],
                            (float) C[j][0], (float) C[j][1], argb, th, smooth);
                }
            }
            return true;
        }
        if (!all) return false;
        int x0 = Math.max(0, Math.min(Projection.width(), Math.round(minX)));
        int y0 = Math.max(0, Math.min(Projection.height(), Math.round(minY)));
        int x1 = Math.max(0, Math.min(Projection.width(), Math.round(maxX)));
        int y1 = Math.max(0, Math.min(Projection.height(), Math.round(maxY)));
        if (x1 <= x0 || y1 <= y0) return false;
        int t = Math.max(1, Math.min(th, Math.min(x1 - x0, y1 - y0)));
        ctx.fill(x0, y0, x1, Math.min(y1, y0 + t), argb);
        ctx.fill(x0, Math.max(y0, y1 - t), x1, y1, argb);
        ctx.fill(x0, y0, Math.min(x1, x0 + t), y1, argb);
        ctx.fill(Math.max(x0, x1 - t), y0, x1, y1, argb);
        return true;
    }

    /** Fills the projected screen-space bounds with a deliberately low-alpha overlay. */
    public static boolean fillProjectedBounds(DrawContext ctx, Box b, int argb) {
        int any = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!Projection.project(x, y, z, C[i])) continue;
            any++;
            minX = Math.min(minX, (float) C[i][0]); maxX = Math.max(maxX, (float) C[i][0]);
            minY = Math.min(minY, (float) C[i][1]); maxY = Math.max(maxY, (float) C[i][1]);
        }
        if (any == 0 || maxX < 0 || maxY < 0 || minX > Projection.width() || minY > Projection.height()) return false;
        int x0 = Math.max(0, Math.min(Projection.width(), (int) Math.floor(minX)));
        int y0 = Math.max(0, Math.min(Projection.height(), (int) Math.floor(minY)));
        int x1 = Math.max(0, Math.min(Projection.width(), (int) Math.ceil(maxX)));
        int y1 = Math.max(0, Math.min(Projection.height(), (int) Math.ceil(maxY)));
        if (x1 <= x0 || y1 <= y0) return false;
        ctx.fill(x0, y0, x1, y1, argb);
        return true;
    }

    /** Liang-Barsky viewport clipping. Returns null when the whole segment is outside. */
    private static float[] clipLine(float x0, float y0, float x1, float y1, int width, int height) {
        if (width <= 0 || height <= 0 || !Float.isFinite(x0) || !Float.isFinite(y0)
                || !Float.isFinite(x1) || !Float.isFinite(y1)) return null;
        float dx = x1 - x0, dy = y1 - y0;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {x0, width - x0, y0, height - y0};
        float u0 = 0f, u1 = 1f;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0e-6f) { if (q[i] < 0f) return null; }
            else {
                float r = q[i] / p[i];
                if (p[i] < 0f) { if (r > u1) return null; u0 = Math.max(u0, r); }
                else { if (r < u0) return null; u1 = Math.min(u1, r); }
            }
        }
        return new float[]{x0 + u0 * dx, y0 + u0 * dy, x0 + u1 * dx, y0 + u1 * dy};
    }

    public static void centeredText(DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                    String s, float cx, float y, int argb) {
        ctx.drawText(tr, s, Math.round(cx - tr.getWidth(s) / 2f), Math.round(y), argb, true);
    }
}

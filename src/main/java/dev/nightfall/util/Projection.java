package dev.nightfall.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Reine Mathematik: projiziert Weltkoordinaten auf GUI-Koordinaten (Ego-Perspektive).
 * Kein Zugriff auf Render-Internals. Naeherung: beruecksichtigt weder View-Bobbing noch
 * dynamische FOV-Aenderungen (Sprint/Effekte), daher kann es leicht abweichen.
 */
public final class Projection {
    private static double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, tanH, aspect;
    private static int sw, sh;
    private static boolean valid;

    private Projection() {}

    public static boolean valid() { return valid; }
    public static int width() { return sw; }
    public static int height() { return sh; }
    public static double eyeX() { return ex; }
    public static double eyeY() { return ey; }
    public static double eyeZ() { return ez; }

    public static void update(MinecraftClient mc, int w, int h) {
        valid = false;
        if (mc.player == null || mc.world == null || w <= 0 || h <= 0
                || !mc.options.getPerspective().isFirstPerson()) return;
        Vec3d e = mc.player.getEyePos();
        ex = e.x; ey = e.y; ez = e.z;
        double yaw = Math.toRadians(mc.player.getYaw()), pitch = Math.toRadians(mc.player.getPitch());
        double sy = Math.sin(yaw), cy = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        fx = -sy * cp; fy = -sp; fz = cy * cp;
        rx = -cy; rz = -sy;
        ux = -sy * sp; uy = cp; uz = cy * sp;
        // Guard against invalid FOV values during resolution/world transitions.
        double fov = mc.options.getFov().getValue();
        if (!Double.isFinite(fov)) return;
        fov = Math.max(1.0, Math.min(179.0, fov));
        tanH = Math.tan(Math.toRadians(fov) / 2.0);
        if (!Double.isFinite(tanH) || tanH <= 0.0) return;
        sw = w; sh = h; aspect = w / (double) h;
        valid = Double.isFinite(ex) && Double.isFinite(ey) && Double.isFinite(ez);

    }

    /** @return false, wenn der Punkt hinter der Kamera liegt. out = {x, y} in GUI-Pixeln. */
    public static boolean project(double x, double y, double z, double[] out) {
        if (!valid || out == null || out.length < 2
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double depth = dx * fx + dy * fy + dz * fz;
        if (depth < 0.05) return false;
        double dr = dx * rx + dz * rz;
        double du = dx * ux + dy * uy + dz * uz;
        out[0] = (0.5 + 0.5 * (dr / depth) / (tanH * aspect)) * sw;
        out[1] = (0.5 - 0.5 * (du / depth) / tanH) * sh;
        return Double.isFinite(out[0]) && Double.isFinite(out[1]);
    }
}

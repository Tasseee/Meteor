package dev.nightfall.module;

import dev.nightfall.config.Config;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix3x2fStack;

/** Basisklasse fuer HUD-Elemente, die im HUD-Editor verschoben/skaliert werden koennen. */
public abstract class HudModule extends Module {
    /** Groesse beim letzten Zeichnen (unskaliert). */
    protected int w = 60, h = 12;
    protected boolean scalable = true;

    protected HudModule(String name, String desc) { super(name, desc, Category.HUD); }

    protected abstract int[] defaultPos(int sw, int sh);
    protected abstract void draw(DrawContext ctx);

    public boolean isScalable() { return scalable; }
    public float scale() { int[] s = Config.hud.get(getName()); return s == null ? 1f : s[2] / 100f; }
    public boolean isLocked() { int[] s = Config.hud.get(getName()); return s != null && s[3] == 1; }
    public int width() { return Math.round(w * scale()); }
    public int height() { return Math.round(h * scale()); }

    public int[] pos(int sw, int sh) {
        int[] s = Config.hud.get(getName());
        return s != null ? new int[]{s[0], s[1]} : defaultPos(sw, sh);
    }

    public int[] ensureState(int sw, int sh) {
        return Config.hud.computeIfAbsent(getName(), k -> {
            int[] p = defaultPos(sw, sh);
            return new int[]{p[0], p[1], 100, 0};
        });
    }

    public void resetState() { Config.hud.remove(getName()); }

    @Override
    public void renderHud(DrawContext ctx) {
        int[] p = pos(ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        float sc = scalable ? scale() : 1f;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(p[0], p[1]);
        m.scale(sc, sc);
        draw(ctx);
        m.popMatrix();
    }

    /** Zeichnet das Element im Editor auch dann, wenn es deaktiviert ist (Geisterrahmen). */
    public void drawForEditor(DrawContext ctx) { if (isEnabled()) renderHud(ctx); }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

/**
 * Gemeinsame ESP-Basis. Zeichnet im Screen-Space mit projizierten Weltkoordinaten (siehe Projection):
 * Boxen als 3D-Kanten oder 2D-Rahmen, optionale Tracer und Labels. Es werden nur Objekte gezeichnet,
 * die bereits geladen sind - es werden keine Chunks nachgeladen.
 */
public abstract class EspBase extends Module {
    public static int drawn;

    protected final NumberSetting range;
    protected final NumberSetting maxObjects = add(new NumberSetting("Max Objekte", 64, 4, 512, 4));
    protected final NumberSetting thickness  = add(new NumberSetting("Linienstaerke", 1, 1, 4, 1));
    protected final NumberSetting opacity    = add(new NumberSetting("Transparenz", 0.9, 0.2, 1.0, 0.05));
    protected final BoolSetting   smoothing  = add(new BoolSetting("Linien-Antialiasing", true));
    protected final BoolSetting   distanceFade = add(new BoolSetting("Distanz-Fade", true));
    protected final NumberSetting fadeStart = add(new NumberSetting("Fade ab (Anteil)", 0.55, 0.1, 0.9, 0.05));
    protected final ModeSetting   style      = add(new ModeSetting("Box", "3D", "3D", "2D", "Aus"));
    protected final BoolSetting   tracers    = add(new BoolSetting("Tracer", false));
    protected final ModeSetting   origin     = add(new ModeSetting("Tracer-Start", "Mitte", "Mitte", "Unten"));
    protected final BoolSetting   names      = add(new BoolSetting("Name", true));
    protected final BoolSetting   distance   = add(new BoolSetting("Distanz", true));
    protected final ColorSetting  color;

    private final double[] pt = new double[2];

    protected EspBase(String name, String desc, int defRange, int maxRange, int rgb) {
        super(name, desc, Category.VISUALS);
        range = add(new NumberSetting("Reichweite", defRange, 8, maxRange, 8));
        color = add(new ColorSetting("Farbe", rgb));
    }

    protected int argb(int rgb) { return argb(rgb, 1.0); }

    private int argb(int rgb, double fade) {
        int alpha = (int) Math.max(0, Math.min(255, Math.round(opacity.get() * 255.0 * fade)));
        return Theme.withAlpha(rgb, alpha);
    }

    protected boolean capReached() { return drawn >= 2048; }

    protected void drawTarget(DrawContext ctx, Box box, String label, int rgb) {
        if (!Projection.valid()) return;
        BaseFinder baseFinder = ModuleManager.get(BaseFinder.class);
        if (baseFinder != null && baseFinder.isFakeChunk(((int) Math.floor((box.minX + box.maxX) / 2.0)) >> 4,
                ((int) Math.floor((box.minZ + box.maxZ) / 2.0)) >> 4)) return;
        double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
        double fade = 1.0;
        if (distanceFade.get() && mc.player != null && range.get() > 0) {
            double distanceToTarget = mc.player.getPos().distanceTo(new net.minecraft.util.math.Vec3d(cx, cy, cz));
            double start = fadeStart.get();
            double progress = (distanceToTarget / range.get() - start) / Math.max(0.01, 1.0 - start);
            fade = Math.max(0.0, Math.min(1.0, 1.0 - progress));
        }
        int col = argb(rgb, fade);
        boolean shown = false;
        if (!style.is("Aus") && fade > 0.005)
            shown = Draw.box(ctx, box, col, thickness.getInt(), style.is("3D"), smoothing.get());
        boolean centerOk = Projection.project(cx, cy, cz, pt);
        if (shown && label != null && !label.isEmpty())
            drawCleanLabel(ctx, label, Draw.topX, Draw.topY - 11);
        else if (!shown && centerOk && label != null && !label.isEmpty())
            drawCleanLabel(ctx, label, (float) pt[0], (float) pt[1] - 5);
        if (tracers.get()) {
            float sx = Projection.width() / 2f;
            float sy = origin.is("Mitte") ? Projection.height() / 2f : Projection.height();
            if (centerOk) {
                Draw.line(ctx, sx, sy, (float) pt[0], (float) pt[1], col, thickness.getInt(), smoothing.get());
                shown = true;
            } else if (Projection.projectToEdge(cx, cy, cz, pt)) {
                // Keep a directional tracer visible when the target is outside the view,
                // including directly behind the camera.
                Draw.line(ctx, sx, sy, (float) pt[0], (float) pt[1], col, thickness.getInt());
                shown = true;
            }
        }
        if (shown) drawn++;
    }

    private void drawCleanLabel(DrawContext ctx, String label, float centerX, float topY) {
        int w=mc.textRenderer.getWidth(label),x=Math.round(centerX-w/2.0f),y=Math.round(topY);
        ctx.fill(x-3,y-2,x+w+3,y+10,0xB910121B);
        ctx.drawText(mc.textRenderer,label,x,y,0xFFF2F4FA,true);
    }

    protected String label(String name, double dist) {
        StringBuilder sb = new StringBuilder();
        if (names.get() && name != null) sb.append(name);
        if (distance.get()) { if (sb.length() > 0) sb.append(' '); sb.append((int) dist).append('m'); }
        return sb.toString();
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
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

    protected int argb(int rgb) { return Theme.withAlpha(rgb, (int) (opacity.get() * 255)); }

    protected boolean capReached() { return drawn >= 2048; }

    protected void drawTarget(DrawContext ctx, Box box, String label, int rgb) {
        if (!Projection.valid()) return;
        int col = argb(rgb);
        boolean shown = false;
        if (!style.is("Aus")) shown = Draw.box(ctx, box, col, thickness.getInt(), style.is("3D"));
        double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
        boolean centerOk = Projection.project(cx, cy, cz, pt);
        if (shown && label != null && !label.isEmpty())
            Draw.centeredText(ctx, mc.textRenderer, label, Draw.topX, Draw.topY - 10, 0xFFFFFFFF);
        else if (!shown && centerOk && label != null && !label.isEmpty())
            Draw.centeredText(ctx, mc.textRenderer, label, (float) pt[0], (float) pt[1] - 4, 0xFFFFFFFF);
        if (tracers.get()) {
            float sx = Projection.width() / 2f;
            float sy = origin.is("Mitte") ? Projection.height() / 2f : Projection.height();
            if (centerOk) {
                Draw.line(ctx, sx, sy, (float) pt[0], (float) pt[1], col, thickness.getInt());
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

    protected String label(String name, double dist) {
        StringBuilder sb = new StringBuilder();
        if (names.get() && name != null) sb.append(name);
        if (distance.get()) { if (sb.length() > 0) sb.append(' '); sb.append((int) dist).append('m'); }
        return sb.toString();
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** ESP fuer Entities: Auswahl der naechsten N Objekte, ohne Chunks zu laden. */
public abstract class EntityScanEsp extends EspBase {
    protected final BoolSetting health      = add(new BoolSetting("Health", true));
    protected final BoolSetting visibleOnly = add(new BoolSetting("Nur sichtbare", false));

    private final List<Entity> buf = new ArrayList<>();
    private static double sx, sy, sz;
    private static final Comparator<Entity> BY_DIST = Comparator.comparingDouble(e -> e.squaredDistanceTo(sx, sy, sz));

    protected EntityScanEsp(String n, String d, int r, int mr, int rgb) { super(n, d, r, mr, rgb); }

    protected abstract boolean accept(Entity e);
    protected String nameOf(Entity e) { return e.getName().getString(); }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;
        sx = Projection.eyeX(); sy = Projection.eyeY(); sz = Projection.eyeZ();
        double r2 = range.get() * range.get();
        buf.clear();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !accept(e)) continue;
            if (e.squaredDistanceTo(sx, sy, sz) > r2) continue;
            if (visibleOnly.get() && !mc.player.canSee(e)) continue;
            buf.add(e);
        }
        if (buf.size() > maxObjects.getInt()) {
            buf.sort(BY_DIST);
        }
        int n = Math.min(buf.size(), maxObjects.getInt());
        for (int i = 0; i < n && !capReached(); i++) {
            Entity e = buf.get(i);
            double d = Math.sqrt(e.squaredDistanceTo(sx, sy, sz));
            String lb = label(nameOf(e), d);
            if (health.get() && e instanceof LivingEntity le) lb += " " + Math.round(le.getHealth()) + "hp";
            drawTarget(ctx, e.getBoundingBox(), lb, color.rgb());
        }
    }
}

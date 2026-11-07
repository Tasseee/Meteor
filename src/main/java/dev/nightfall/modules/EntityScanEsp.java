package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ESP for entities. The candidate scan and nearest-target selection run on a
 * short client-tick interval rather than on every rendered frame. Rendering
 * still rechecks removal, distance and visibility so cached references cannot
 * produce stale on-screen markers.
 */
public abstract class EntityScanEsp extends EspBase {
    protected final BoolSetting health       = add(new BoolSetting("Health", true));
    protected final BoolSetting visibleOnly  = add(new BoolSetting("Nur sichtbare", false));

    private final List<Entity> buf = new ArrayList<>();
    private int scanTimer;

    protected EntityScanEsp(String n, String d, int r, int mr, int rgb) {
        super(n, d, r, mr, rgb);
    }

    protected abstract boolean accept(Entity e);

    protected String nameOf(Entity e) {
        return e.getName().getString();
    }

    @Override
    protected void onEnable() {
        scanTimer = 0;
        refreshTargets();
    }

    @Override
    protected void onDisable() {
        buf.clear();
        scanTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            buf.clear();
            return;
        }
        if (scanTimer-- > 0) return;
        scanTimer = 2;
        refreshTargets();
    }

    /**
     * Rebuild only a bounded nearest-target list on the client tick. Entity
     * position and visibility are checked again during rendering.
     */
    private void refreshTargets() {
        if (mc.world == null || mc.player == null) {
            buf.clear();
            return;
        }

        buf.clear();
        double rangeSquared = range.get() * range.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || !accept(entity)) continue;
            if (entity instanceof LivingEntity living && !living.isAlive()) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSquared) continue;
            if (visibleOnly.get() && !mc.player.canSee(entity)) continue;
            buf.add(entity);
        }

        int limit = Math.max(1, maxObjects.getInt());
        if (buf.size() > limit) {
            buf.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));
            buf.subList(limit, buf.size()).clear();
        }
    }

    /** Read-only observations for Nightfall AI; uses the same filters as this module. */
    public List<String> aiObservations(int limit) {
        List<Entity> found = new ArrayList<>();
        if (!isEnabled() || mc.world == null || mc.player == null) return List.of();
        double rangeSquared = range.get() * range.get();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.isRemoved() || !accept(entity)) continue;
            if (entity instanceof LivingEntity living && !living.isAlive()) continue;
            if (entity.squaredDistanceTo(mc.player) > rangeSquared) continue;
            if (visibleOnly.get() && !mc.player.canSee(entity)) continue;
            found.add(entity);
        }
        found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(mc.player)));

        List<String> observations = new ArrayList<>();
        for (int i = 0; i < Math.min(Math.max(0, limit), found.size()); i++) {
            Entity entity = found.get(i);
            observations.add(nameOf(entity) + " @ " + entity.getBlockX() + ","
                    + entity.getBlockY() + "," + entity.getBlockZ() + " ("
                    + Math.round(Math.sqrt(entity.squaredDistanceTo(mc.player))) + "m)");
        }
        return observations;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;
        double sx = Projection.eyeX();
        double sy = Projection.eyeY();
        double sz = Projection.eyeZ();
        double rangeSquared = range.get() * range.get();
        int n = Math.min(buf.size(), Math.max(1, maxObjects.getInt()));

        for (int i = 0; i < n && !capReached(); i++) {
            Entity entity = buf.get(i);
            if (entity == null || entity.isRemoved() || !accept(entity)) continue;
            if (entity instanceof LivingEntity living && !living.isAlive()) continue;
            if (entity.squaredDistanceTo(sx, sy, sz) > rangeSquared) continue;
            if (visibleOnly.get() && !mc.player.canSee(entity)) continue;

            double distance = Math.sqrt(entity.squaredDistanceTo(sx, sy, sz));
            String label = label(nameOf(entity), distance);
            if (health.get() && entity instanceof LivingEntity living) {
                label += " " + Math.round(living.getHealth()) + "hp";
            }
            drawTarget(ctx, entity.getBoundingBox(), label, color.rgb());
        }
    }
}

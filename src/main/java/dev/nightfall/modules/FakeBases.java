package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Local-only test/decoy markers. These are labels, not placed blocks or server-side structures. */
public final class FakeBases extends Module {
    public static final String CATEGORY = "FakeBase";
    private final BoolSetting labels = add(new BoolSetting("Label", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracer", false));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distanz", 3000, 50, 10000, 50));
    private final double[] point = new double[2];

    public FakeBases() { super("FakeBases", "Eigene lokale TEST/DECOY-Markierungen; verändert keine Weltblöcke.", Category.WORLD); }

    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || !Projection.valid()) return;
        for (Waypoint w : WaypointManager.all()) {
            if (!CATEGORY.equalsIgnoreCase(w.category) || !w.dim.equals(Storage.dim())) continue;
            double x = w.x + 0.5, y = w.y + 0.5, z = w.z + 0.5;
            double dx = x - Projection.eyeX(), dy = y - Projection.eyeY(), dz = z - Projection.eyeZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxDistance.get() || !Projection.project(x, y, z, point)) continue;
            int color = Theme.withAlpha(0xFFFF5F75, 0xE8);
            ctx.fill((int) point[0] - 3, (int) point[1] - 3, (int) point[0] + 4, (int) point[1] + 4, color);
            if (labels.get()) Draw.centeredText(ctx, mc.textRenderer, "[TEST/DECOY] " + w.name, (float) point[0], (float) point[1] - 14, 0xFFFF8797);
            if (tracers.get()) Draw.line(ctx, Projection.width() / 2f, Projection.height() / 2f, (float) point[0], (float) point[1], color, 1);
        }
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import dev.nightfall.util.Storage;
import net.minecraft.client.gui.DrawContext;

public class Waypoints extends Module {
    private final BoolSetting tracers = add(new BoolSetting("Tracer", true));
    private final BoolSetting labels  = add(new BoolSetting("Label + Distanz", true));
    private final NumberSetting maxDist = add(new NumberSetting("Max Distanz", 2000, 50, 10000, 50));
    private final double[] pt = new double[2];

    public Waypoints() { super("Waypoints", "Zeigt gespeicherte Waypoints (Befehle: /nf wp ..., Karte: Rechtsklick).", Category.WORLD); }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || !Projection.valid()) return;
        String dim = Storage.dim();
        for (Waypoint w : WaypointManager.all()) {
            // Fake-base markers are rendered separately with an unmistakable TEST/DECOY label.
            if (FakeBases.CATEGORY.equalsIgnoreCase(w.category)) continue;
            if (!w.dim.equals(dim)) continue;
            double dx = w.x + 0.5 - mc.player.getX(), dy = w.y + 0.5 - mc.player.getY(), dz = w.z + 0.5 - mc.player.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > maxDist.get()) continue;
            if (!Projection.project(w.x + 0.5, w.y + 0.5, w.z + 0.5, pt)) continue;
            int col = Theme.withAlpha(w.color, 0xE0);
            ctx.fill((int) pt[0] - 2, (int) pt[1] - 2, (int) pt[0] + 3, (int) pt[1] + 3, col);
            if (labels.get()) Draw.centeredText(ctx, mc.textRenderer, w.name + " " + (int) d + "m", (float) pt[0], (float) pt[1] - 12, 0xFFFFFFFF);
            if (tracers.get()) Draw.line(ctx, Projection.width() / 2f, Projection.height() / 2f, (float) pt[0], (float) pt[1], col, 1);
            EspBase.drawn++;
        }
    }
}

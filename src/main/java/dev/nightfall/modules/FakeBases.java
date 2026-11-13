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
import net.minecraft.class_332;

public final class FakeBases extends Module {
   public static final String CATEGORY = "FakeBase";
   private final BoolSetting labels = this.add(new BoolSetting("Label", true));
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", false));
   private final NumberSetting maxDistance = this.add(new NumberSetting("Max Distanz", 3000.0, 50.0, 10000.0, 50.0));
   private final double[] point = new double[2];

   public FakeBases() {
      super("FakeBases", "Eigene lokale TEST/DECOY-Markierungen; verändert keine Weltblöcke.", Category.WORLD);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || mc.field_1687 == null || !Projection.valid()) return;

      String dimension = Storage.dim();
      int screenWidth = Projection.width();
      int screenHeight = Projection.height();
      int color = Theme.withAlpha(-41099, 232);

      for (Waypoint waypoint : WaypointManager.all()) {
         // Ignore incomplete/corrupt records instead of letting one bad entry
         // disable the whole HUD module.
         if (waypoint == null || !"FakeBase".equalsIgnoreCase(waypoint.category)
            || !dimension.equals(waypoint.dim)) {
            continue;
         }

         double x = waypoint.x + 0.5;
         double y = waypoint.y + 0.5;
         double z = waypoint.z + 0.5;
         double dx = x - Projection.eyeX();
         double dy = y - Projection.eyeY();
         double dz = z - Projection.eyeZ();
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (!Double.isFinite(distance) || distance > this.maxDistance.get()) continue;

         boolean projected = Projection.project(x, y, z, this.point);
         boolean onScreen = projected
            && this.point[0] >= 0.0 && this.point[0] <= screenWidth
            && this.point[1] >= 0.0 && this.point[1] <= screenHeight;

         // Keep distant/off-screen markers discoverable at the screen edge.
         if (!onScreen && !Projection.projectToEdge(x, y, z, this.point)) continue;

         int px = (int)this.point[0];
         int py = (int)this.point[1];
         int halfSize = onScreen ? 3 : 4;
         ctx.method_25294(px - halfSize, py - halfSize, px + halfSize + 1, py + halfSize + 1, color);

         if (this.labels.get()) {
            String name = waypoint.name == null || waypoint.name.isBlank() ? "Unbenannt" : waypoint.name;
            String label = "[TEST/DECOY] " + name + (onScreen ? "" : " (außerhalb)");
            Draw.centeredText(ctx, mc.field_1772, label, (float)this.point[0], (float)this.point[1] - 14.0F, -30825);
         }

         if (this.tracers.get()) {
            Draw.line(ctx, screenWidth / 2.0F, screenHeight / 2.0F,
               (float)this.point[0], (float)this.point[1], color, 1);
         }
      }
   }
}

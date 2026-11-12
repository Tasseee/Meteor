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
      if (mc.field_1724 != null && Projection.valid()) {
         for (Waypoint w : WaypointManager.all()) {
            if ("FakeBase".equalsIgnoreCase(w.category) && w.dim.equals(Storage.dim())) {
               double x = w.x + 0.5;
               double y = w.y + 0.5;
               double z = w.z + 0.5;
               double dx = x - Projection.eyeX();
               double dy = y - Projection.eyeY();
               double dz = z - Projection.eyeZ();
               double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (!(distance > this.maxDistance.get()) && Projection.project(x, y, z, this.point)) {
                  int color = Theme.withAlpha(-41099, 232);
                  ctx.method_25294((int)this.point[0] - 3, (int)this.point[1] - 3, (int)this.point[0] + 4, (int)this.point[1] + 4, color);
                  if (this.labels.get()) {
                     Draw.centeredText(ctx, mc.field_1772, "[TEST/DECOY] " + w.name, (float)this.point[0], (float)this.point[1] - 14.0F, -30825);
                  }

                  if (this.tracers.get()) {
                     Draw.line(ctx, Projection.width() / 2.0F, Projection.height() / 2.0F, (float)this.point[0], (float)this.point[1], color, 1);
                  }
               }
            }
         }
      }
   }
}

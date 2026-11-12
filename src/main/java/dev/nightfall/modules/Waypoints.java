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

public class Waypoints extends Module {
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", true));
   private final BoolSetting labels = this.add(new BoolSetting("Label + Distanz", true));
   private final NumberSetting maxDist = this.add(new NumberSetting("Max Distanz", 2000.0, 50.0, 10000.0, 50.0));
   private final double[] pt = new double[2];

   public Waypoints() {
      super("Waypoints", "Zeigt gespeicherte Waypoints (Befehle: /nf wp ..., Karte: Rechtsklick).", Category.WORLD);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && Projection.valid()) {
         String dim = Storage.dim();

         for (Waypoint w : WaypointManager.all()) {
            if (!"FakeBase".equalsIgnoreCase(w.category) && w.dim.equals(dim)) {
               double dx = w.x + 0.5 - Projection.eyeX();
               double dy = w.y + 0.5 - Projection.eyeY();
               double dz = w.z + 0.5 - Projection.eyeZ();
               double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
               if (!(d > this.maxDist.get()) && Projection.project(w.x + 0.5, w.y + 0.5, w.z + 0.5, this.pt)) {
                  int col = Theme.withAlpha(w.color, 224);
                  ctx.method_25294((int)this.pt[0] - 2, (int)this.pt[1] - 2, (int)this.pt[0] + 3, (int)this.pt[1] + 3, col);
                  if (this.labels.get()) {
                     Draw.centeredText(ctx, mc.field_1772, w.name + " " + (int)d + "m", (float)this.pt[0], (float)this.pt[1] - 12.0F, -1);
                  }

                  if (this.tracers.get()) {
                     Draw.line(ctx, Projection.width() / 2.0F, Projection.height() / 2.0F, (float)this.pt[0], (float)this.pt[1], col, 1);
                  }

                  EspBase.drawn++;
               }
            }
         }
      }
   }
}

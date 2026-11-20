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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_332;

public final class FakeBases extends Module {
   public static final String CATEGORY = "FakeBase";
   private final BoolSetting labels = this.add(new BoolSetting("Label", true));
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", false));
   private final NumberSetting maxDistance = this.add(new NumberSetting("Max Distanz", 3000.0, 50.0, 10000.0, 50.0));
   private final BoolSetting groupNearby = this.add(new BoolSetting("Nahe Marker gruppieren", true));
   private final NumberSetting groupRadius = this.add(new NumberSetting("Gruppierabstand", 32.0, 4.0, 128.0, 4.0));
   private final BoolSetting showDistance = this.add(new BoolSetting("Distanz anzeigen", true));
   private final double[] point = new double[2];

   public FakeBases() {
      super("FakeBases", "Zeigt eigene lokale TEST/DECOY-Markierungen gruppiert an; veraendert keine Weltbloecke.", Category.WORLD);
   }

   private List<MarkerGroup> collectGroups() {
      String dimension = Storage.dim();
      if (dimension == null) return List.of();

      List<Waypoint> candidates = new ArrayList<>();
      for (Waypoint waypoint : WaypointManager.all()) {
         if (waypoint == null || waypoint.name == null || waypoint.name.isBlank()
            || waypoint.category == null || !CATEGORY.equalsIgnoreCase(waypoint.category)
            || waypoint.dim == null || !dimension.equals(waypoint.dim)) {
            continue;
         }
         candidates.add(waypoint);
      }

      List<MarkerGroup> groups = new ArrayList<>();
      if (candidates.isEmpty()) return groups;

      if (!this.groupNearby.get()) {
         for (Waypoint waypoint : candidates) groups.add(new MarkerGroup(List.of(waypoint)));
         return groups;
      }

      boolean[] visited = new boolean[candidates.size()];
      double radius = this.groupRadius.get();
      double radiusSquared = radius * radius;

      for (int i = 0; i < candidates.size(); i++) {
         if (visited[i]) continue;

         List<Waypoint> members = new ArrayList<>();
         ArrayDeque<Integer> queue = new ArrayDeque<>();
         visited[i] = true;
         queue.addLast(i);

         while (!queue.isEmpty()) {
            Waypoint current = candidates.get(queue.removeFirst());
            members.add(current);

            for (int j = 0; j < candidates.size(); j++) {
               if (!visited[j] && distanceSquared(current, candidates.get(j)) <= radiusSquared) {
                  visited[j] = true;
                  queue.addLast(j);
               }
            }
         }

         groups.add(new MarkerGroup(members));
      }

      return groups;
   }

   private static double distanceSquared(Waypoint a, Waypoint b) {
      double dx = a.x - b.x;
      double dy = a.y - b.y;
      double dz = a.z - b.z;
      return dx * dx + dy * dy + dz * dz;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;

      for (MarkerGroup group : this.collectGroups()) {
         double dx = group.x - Projection.eyeX();
         double dy = group.y - Projection.eyeY();
         double dz = group.z - Projection.eyeZ();
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (distance > this.maxDistance.get() || !Projection.project(group.x, group.y, group.z, this.point)) {
            continue;
         }

         int color = Theme.withAlpha(-41099, 232);
         int markerSize = group.count == 1 ? 3 : Math.min(7, 3 + group.count / 2);
         ctx.method_25294(
            (int)this.point[0] - markerSize,
            (int)this.point[1] - markerSize,
            (int)this.point[0] + markerSize + 1,
            (int)this.point[1] + markerSize + 1,
            color
         );

         if (this.labels.get()) {
            String label = group.count == 1 ? group.name : group.count + " Markierungen";
            String text = "[TEST/DECOY] " + label;
            if (this.showDistance.get()) {
               text += " · " + Math.round(distance) + "m";
            }
            Draw.centeredText(ctx, mc.field_1772, text, (float)this.point[0], (float)this.point[1] - markerSize - 12.0F, -30825);
         }

         if (this.tracers.get()) {
            Draw.line(ctx, Projection.width() / 2.0F, Projection.height() / 2.0F, (float)this.point[0], (float)this.point[1], color, 1);
         }
      }
   }

   private static final class MarkerGroup {
      final double x;
      final double y;
      final double z;
      final int count;
      final String name;

      MarkerGroup(List<Waypoint> members) {
         double sumX = 0.0;
         double sumY = 0.0;
         double sumZ = 0.0;
         for (Waypoint waypoint : members) {
            sumX += waypoint.x + 0.5;
            sumY += waypoint.y + 0.5;
            sumZ += waypoint.z + 0.5;
         }
         this.count = members.size();
         this.x = sumX / this.count;
         this.y = sumY / this.count;
         this.z = sumZ / this.count;
         this.name = this.count == 1 ? members.get(0).name : "";
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1657;
import net.minecraft.class_332;
import net.minecraft.class_640;
import net.minecraft.class_243;

public final class LogoutSpots extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Max Reichweite", 256.0, 16.0, 1024.0, 16.0));
   private final NumberSetting maxPositionAge = this.add(new NumberSetting("Max. Positionsalter (s)", 120.0, 5.0, 600.0, 5.0));
   private final NumberSetting markerDuration = this.add(new NumberSetting("Marker-Dauer (s)", 300.0, 5.0, 1800.0, 5.0));
   private final NumberSetting maxMarkers = this.add(new NumberSetting("Max. Marker", 32.0, 1.0, 128.0, 1.0));
   private final BoolSetting showCoordinates = this.add(new BoolSetting("Koordinaten", true));
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", false));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 15485081));
   private final Map<String, LastSeen> lastSeen = new HashMap<>();
   private final Set<String> online = new HashSet<>();
   private final List<Marker> markers = new ArrayList<>();
   private final double[] point = new double[2];

   public LogoutSpots() {
      super("LogoutSpots", "Markiert die letzte lokal beobachtete Position, wenn ein Spieler aus der Server-Spielerliste verschwindet.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.method_1562() == null) {
         this.clearTracking();
         return;
      }

      long now = System.currentTimeMillis();
      Map<String, String> currentOnline = new HashMap<>();
      for (class_640 entry : mc.method_1562().method_45732()) {
         if (entry == null || entry.method_2966() == null) continue;
         String name = entry.method_2966().name();
         if (name == null || name.isBlank()) continue;
         currentOnline.put(key(name), name);
      }

      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player == null || player == mc.field_1724 || player.method_7334() == null) continue;
         String name = player.method_7334().name();
         if (name == null || name.isBlank()) continue;
         double x = player.method_23317();
         double y = player.method_23318();
         double z = player.method_23321();
         this.lastSeen.put(key(name), new LastSeen(name, x, y, z, now));
      }

      for (String previous : new HashSet<>(this.online)) {
         if (!currentOnline.containsKey(previous)) {
            LastSeen seen = this.lastSeen.get(previous);
            if (seen != null && now - seen.time <= this.maxPositionAge.get() * 1000.0) {
               double dx = seen.x - mc.field_1724.method_23317();
               double dy = seen.y - mc.field_1724.method_23318();
               double dz = seen.z - mc.field_1724.method_23321();
               if (dx * dx + dy * dy + dz * dz <= this.range.get() * this.range.get()) {
                  this.markers.removeIf(marker -> marker.key.equals(previous));
                  this.markers.add(new Marker(previous, seen.name, seen.x, seen.y, seen.z, now));
               }
            }
         }
      }

      this.online.clear();
      this.online.addAll(currentOnline.keySet());
      this.markers.removeIf(marker -> now - marker.createdAt > this.markerDuration.get() * 1000.0);
      while (this.markers.size() > this.maxMarkers.getInt()) this.markers.remove(0);
      this.lastSeen.entrySet().removeIf(entry -> now - entry.getValue().time > this.maxPositionAge.get() * 2000.0);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;
      long now = System.currentTimeMillis();
      for (Marker marker : this.markers) {
         double dx = marker.x - Projection.eyeX();
         double dy = marker.y - Projection.eyeY();
         double dz = marker.z - Projection.eyeZ();
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (distance > this.range.get()) continue;

         int argb = Theme.withAlpha(this.color.rgb(), 230);
         if (Projection.project(marker.x, marker.y + 0.5, marker.z, this.point)) {
            int px = (int) this.point[0];
            int py = (int) this.point[1];
            ctx.method_25294(px - 3, py - 3, px + 4, py + 4, argb);
            String label = marker.name + " " + (int) distance + "m";
            if (this.showCoordinates.get()) {
               label += " [" + (int)Math.floor(marker.x) + " " + (int)Math.floor(marker.y) + " " + (int)Math.floor(marker.z) + "]";
            }
            Draw.centeredText(ctx, mc.field_1772, label, (float) px, py - 12.0F, -1);
            if (this.tracers.get()) {
               Draw.line(ctx, Projection.width() / 2.0F, Projection.height() / 2.0F, px, py, argb, 1);
            }
            EspBase.drawn++;
         } else if (Projection.projectToEdge(marker.x, marker.y + 0.5, marker.z, this.point)) {
            if (this.tracers.get()) {
               Draw.line(ctx, Projection.width() / 2.0F, Projection.height() / 2.0F, (float) this.point[0], (float) this.point[1], argb, 1);
            }
         }
      }
   }

   private void clearTracking() {
      this.lastSeen.clear();
      this.online.clear();
      this.markers.clear();
   }

   private static String key(String name) {
      return name.trim().toLowerCase(Locale.ROOT);
   }

   @Override
   protected void onDisable() {
      this.clearTracking();
   }

   @Override
   public void onWorldLeave() {
      this.clearTracking();
   }

   private record LastSeen(String name, double x, double y, double z, long time) {}
   private record Marker(String key, String name, double x, double y, double z, long createdAt) {}
}

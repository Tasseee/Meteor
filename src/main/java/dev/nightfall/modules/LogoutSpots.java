package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.class_1657;
import net.minecraft.class_238;
import net.minecraft.class_332;

/**
 * Marks the last client-observed position of players whose UUID disappears
 * from the server's current player list.
 */
public final class LogoutSpots extends Module {
   private final NumberSetting maxDistance = this.add(new NumberSetting("Max. Distanz", 3000.0, 64.0, 10000.0, 64.0));
   private final NumberSetting lifetimeMinutes = this.add(new NumberSetting("Marker-Dauer (Min.)", 60.0, 1.0, 1440.0, 1.0));
   private final NumberSetting maxMarkers = this.add(new NumberSetting("Max. Marker", 48.0, 1.0, 128.0, 1.0));
   private final BoolSetting showCoordinates = this.add(new BoolSetting("Koordinaten", true));
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", true));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0xC084FC));
   private final Map<UUID, Snapshot> lastSeen = new HashMap<>();
   private final Map<UUID, Marker> markers = new LinkedHashMap<>();
   private final Set<UUID> previousOnline = new HashSet<>();
   private boolean initialized;
   private final double[] projected = new double[2];

   public LogoutSpots() {
      super("LogoutSpots", "Markiert die zuletzt bekannte Position von Spielern nach einer Server-Abmeldung.", Category.PLAYER);
   }

   @Override
   protected void onEnable() {
      resetTracking();
   }

   @Override
   protected void onDisable() {
      resetTracking();
   }

   @Override
   public void onWorldLeave() {
      resetTracking();
   }

   private void resetTracking() {
      this.lastSeen.clear();
      this.markers.clear();
      this.previousOnline.clear();
      this.initialized = false;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) return;

      var network = mc.method_1562();
      if (network == null) {
         resetTracking();
         return;
      }

      String dimension = Storage.dim();

      // Save the latest position while a player entity is still available locally.
      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player == null || player == mc.field_1724 || player.method_7334() == null) continue;
         String name = player.method_7334().name();
         if (name == null || name.isBlank()) continue;

         this.lastSeen.put(
            player.method_5667(),
            new Snapshot(name, player.method_23317(), player.method_23318(), player.method_23321(), dimension)
         );
      }

      Set<UUID> onlineNow = new HashSet<>(network.method_31363());
      if (!this.initialized) {
         this.previousOnline.addAll(onlineNow);
         this.initialized = true;
         trimMarkers();
         return;
      }

      // A player is considered logged out only when the server removes their UUID
      // from its current player list. Merely leaving render distance is not enough.
      for (UUID uuid : this.previousOnline) {
         if (!onlineNow.contains(uuid)) {
            Snapshot snapshot = this.lastSeen.remove(uuid);
            if (snapshot != null) {
               this.markers.put(
                  uuid,
                  new Marker(snapshot.name(), snapshot.x(), snapshot.y(), snapshot.z(), snapshot.dimension(), System.currentTimeMillis())
               );
            }
         }
      }

      this.lastSeen.keySet().removeIf(uuid -> !onlineNow.contains(uuid));
      this.previousOnline.clear();
      this.previousOnline.addAll(onlineNow);
      trimMarkers();

      long maxAge = this.lifetimeMinutes.getInt() * 60_000L;
      long now = System.currentTimeMillis();
      this.markers.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > maxAge);
   }

   private void trimMarkers() {
      while (this.markers.size() > this.maxMarkers.getInt()) {
         Iterator<UUID> iterator = this.markers.keySet().iterator();
         if (!iterator.hasNext()) break;
         iterator.next();
         iterator.remove();
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;

      long now = System.currentTimeMillis();
      long maxAge = this.lifetimeMinutes.getInt() * 60_000L;
      double maxDistanceSquared = this.maxDistance.get() * this.maxDistance.get();
      String dimension = Storage.dim();

      this.markers.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > maxAge);

      for (Marker marker : this.markers.values()) {
         if (!dimension.equals(marker.dimension())) continue;

         double centerX = marker.x() + 0.5;
         double centerY = marker.y() + 0.9;
         double centerZ = marker.z() + 0.5;
         double dx = centerX - Projection.eyeX();
         double dy = centerY - Projection.eyeY();
         double dz = centerZ - Projection.eyeZ();
         double distanceSquared = dx * dx + dy * dy + dz * dz;
         if (distanceSquared > maxDistanceSquared) continue;

         double distance = Math.sqrt(distanceSquared);
         int argb = Theme.withAlpha(this.color.rgb(), 224);
         class_238 box = new class_238(
            marker.x() + 0.2, marker.y(), marker.z() + 0.2,
            marker.x() + 0.8, marker.y() + 1.8, marker.z() + 0.8
         );
         Draw.box(ctx, box, argb, 2, true);

         String label = marker.name() + " · Logout · " + (int)distance + "m";
         if (this.showCoordinates.get()) {
            label += " · " + (int)Math.floor(marker.x()) + " " + (int)Math.floor(marker.y()) + " " + (int)Math.floor(marker.z());
         }

         if (Projection.project(centerX, marker.y() + 1.95, centerZ, this.projected)) {
            Draw.centeredText(ctx, mc.field_1772, label, (float)this.projected[0], (float)this.projected[1] - 7.0F, 0xFFFFFFFF);
         }

         if (this.tracers.get() && Projection.project(centerX, centerY, centerZ, this.projected)) {
            Draw.line(
               ctx,
               Projection.width() / 2.0F,
               Projection.height() / 2.0F,
               (float)this.projected[0],
               (float)this.projected[1],
               argb,
               1
            );
         }
      }
   }

   private record Snapshot(String name, double x, double y, double z, String dimension) {}
   private record Marker(String name, double x, double y, double z, String dimension, long createdAt) {}
}

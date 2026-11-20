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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1657;
import net.minecraft.class_332;
import net.minecraft.class_640;

public final class LogoutSpots extends Module {
   private static final long TAB_REMOVE_CONFIRM_MS = 400L;
   private static final long WORLD_REMOVE_CONFIRM_MS = 1200L;

   private final NumberSetting range = this.add(new NumberSetting("Max Reichweite", 256.0, 16.0, 1024.0, 16.0));
   private final NumberSetting unlistedRange = this.add(new NumberSetting("Nicht gelistete Spieler (m)", 96.0, 32.0, 128.0, 16.0));
   private final NumberSetting maxPositionAge = this.add(new NumberSetting("Max. Positionsalter (s)", 120.0, 5.0, 600.0, 5.0));
   private final NumberSetting markerDuration = this.add(new NumberSetting("Marker-Dauer (s)", 300.0, 5.0, 1800.0, 5.0));
   private final NumberSetting maxMarkers = this.add(new NumberSetting("Max. Marker", 32.0, 1.0, 128.0, 1.0));
   private final BoolSetting showCoordinates = this.add(new BoolSetting("Koordinaten", true));
   private final BoolSetting tracers = this.add(new BoolSetting("Tracer", false));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 15485081));
   private final Map<String, LastSeen> lastSeen = new HashMap<>();
   private final Set<String> online = new HashSet<>();
   private final Set<String> unlistedObserved = new HashSet<>();
   private final Map<String, Long> tabRemovedSince = new HashMap<>();
   private final Map<String, Long> unlistedMissingSince = new HashMap<>();
   private final List<Marker> markers = new ArrayList<>();
   private final double[] point = new double[2];

   public LogoutSpots() {
      super("LogoutSpots", "Markiert Logout-Positionen auch bei lokal beobachteten Spielern ohne Tab-Eintrag.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.method_1562() == null) {
         this.clearTracking();
         return;
      }

      long now = System.currentTimeMillis();
      Set<String> currentOnline = new HashSet<>();
      for (class_640 entry : mc.method_1562().method_45732()) {
         if (entry == null || entry.method_2966() == null) continue;
         String name = entry.method_2966().name();
         if (name == null || name.isBlank()) continue;
         currentOnline.add(key(name));
      }

      Set<String> currentLoaded = new HashSet<>();
      for (class_1657 player : mc.field_1687.method_18456()) {
         if (player == null || player == mc.field_1724 || player.method_7334() == null) continue;
         String name = player.method_7334().name();
         if (name == null || name.isBlank()) continue;

         String playerKey = key(name);
         currentLoaded.add(playerKey);
         this.lastSeen.put(playerKey, new LastSeen(name, player.method_23317(), player.method_23318(), player.method_23321(), now));

         if (currentOnline.contains(playerKey)) {
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
         } else {
            // Keep players that were actually loaded in the world, even if the
            // server does not include them in the client-side player list.
            this.unlistedObserved.add(playerKey);
         }
      }

      // A player-list removal is the stronger signal. Wait briefly for the
      // corresponding world entity to disappear before placing the marker.
      for (String previous : this.online) {
         if (!currentOnline.contains(previous)) this.tabRemovedSince.putIfAbsent(previous, now);
      }
      for (String playerKey : new HashSet<>(this.tabRemovedSince.keySet())) {
         if (currentOnline.contains(playerKey)) {
            this.tabRemovedSince.remove(playerKey);
            continue;
         }
         if (currentLoaded.contains(playerKey)) continue;

         long removedAt = this.tabRemovedSince.get(playerKey);
         if (now - removedAt >= TAB_REMOVE_CONFIRM_MS) {
            this.addMarker(playerKey, now, this.range.get());
            this.tabRemovedSince.remove(playerKey);
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
         }
      }

      // Fallback for players that were never present in the player list.
      // It is deliberately limited to a smaller radius and a longer grace
      // period because entity unloads and server-side tracking limits can
      // look identical to a disconnect from the client.
      double fallbackRange = Math.min(this.range.get(), this.unlistedRange.get());
      double fallbackRangeSquared = fallbackRange * fallbackRange;
      for (String playerKey : new HashSet<>(this.unlistedObserved)) {
         if (currentLoaded.contains(playerKey)) {
            this.unlistedMissingSince.remove(playerKey);
            continue;
         }
         if (currentOnline.contains(playerKey)) {
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
            continue;
         }

         LastSeen seen = this.lastSeen.get(playerKey);
         if (seen == null || now - seen.time > this.maxPositionAge.get() * 1000.0) {
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
            continue;
         }

         double dx = seen.x - mc.field_1724.method_23317();
         double dy = seen.y - mc.field_1724.method_23318();
         double dz = seen.z - mc.field_1724.method_23321();
         if (dx * dx + dy * dy + dz * dz > fallbackRangeSquared) {
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
            continue;
         }

         long missingAt = this.unlistedMissingSince.computeIfAbsent(playerKey, ignored -> now);
         if (now - missingAt >= WORLD_REMOVE_CONFIRM_MS) {
            this.addMarker(playerKey, now, fallbackRange);
            this.unlistedObserved.remove(playerKey);
            this.unlistedMissingSince.remove(playerKey);
         }
      }

      this.online.clear();
      this.online.addAll(currentOnline);
      this.markers.removeIf(marker -> now - marker.createdAt > this.markerDuration.get() * 1000.0);
      while (this.markers.size() > this.maxMarkers.getInt()) this.markers.remove(0);
      this.lastSeen.entrySet().removeIf(entry -> now - entry.getValue().time > this.maxPositionAge.get() * 2000.0);
   }

   private void addMarker(String playerKey, long now, double maxDistance) {
      LastSeen seen = this.lastSeen.get(playerKey);
      if (seen == null || now - seen.time > this.maxPositionAge.get() * 1000.0) return;

      double dx = seen.x - mc.field_1724.method_23317();
      double dy = seen.y - mc.field_1724.method_23318();
      double dz = seen.z - mc.field_1724.method_23321();
      if (dx * dx + dy * dy + dz * dz > maxDistance * maxDistance) return;

      this.markers.removeIf(marker -> marker.key.equals(playerKey));
      this.markers.add(new Marker(playerKey, seen.name, seen.x, seen.y, seen.z, now));
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;
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
      this.unlistedObserved.clear();
      this.tabRemovedSince.clear();
      this.unlistedMissingSince.clear();
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

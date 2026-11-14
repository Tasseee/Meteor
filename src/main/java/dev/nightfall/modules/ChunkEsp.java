package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkFavorites;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_332;

public final class ChunkEsp extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Darstellung", "Ring", "Ring", "Pulse", "Beacon"));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite (Chunks)", 16.0, 2.0, 48.0, 1.0));
   private final NumberSetting markerSize = this.add(new NumberSetting("Marker-Groesse", 8.0, 4.0, 16.0, 1.0));
   private final NumberSetting opacity = this.add(new NumberSetting("Deckkraft", 0.88, 0.2, 1.0, 0.05));
   private final NumberSetting pulseSpeed = this.add(new NumberSetting("Puls-Geschwindigkeit", 1.0, 0.2, 3.0, 0.1));
   private final ColorSetting susColor = this.add(new ColorSetting("SUS-Farbe", -6588939));
   private final ColorSetting goneColor = this.add(new ColorSetting("SUS-WEG-Farbe", -12351783));
   private final ColorSetting favoriteColor = this.add(new ColorSetting("Favoriten-Farbe", -14246));
   private final BoolSetting showScore = this.add(new BoolSetting("Score anzeigen", true));
   private final BoolSetting showGone = this.add(new BoolSetting("SUS WEG anzeigen", true));
   private final BoolSetting showFavorites = this.add(new BoolSetting("Favoriten hervorheben", true));
   private final BoolSetting fadeDistance = this.add(new BoolSetting("Distanz-Fade", true));
   private final NumberSetting maxRegions = this.add(new NumberSetting("Max. Marker", 48.0, 4.0, 128.0, 4.0));

   private List<Region> cachedRegions = List.of();
   private long lastRegionBuildNanos;
   private int cachedPlayerChunkX = Integer.MIN_VALUE;
   private int cachedPlayerChunkZ = Integer.MIN_VALUE;
   private int cachedMarkCount = -1;

   public ChunkEsp() {
      super("ChunkESP", "Markiert auffaellige Chunks mit dezenten Radar-Markern statt 3D-Rahmen.", Category.VISUALS);
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 ^ cz & 4294967295L;
   }

   private List<Region> collectRegions(Collection<ChunkResult> source, int pcx, int pcz, int maxRange) {
      Map<Long, ChunkResult> candidates = new HashMap<>();
      for (ChunkResult result : source) {
         if (result.status == ChunkResult.Status.GONE && !this.showGone.get()) continue;
         int dx = result.cx - pcx;
         int dz = result.cz - pcz;
         if (dx * dx + dz * dz <= maxRange * maxRange) {
            candidates.put(key(result.cx, result.cz), result);
         }
      }

      List<ChunkResult> ordered = new ArrayList<>(candidates.values());
      ordered.sort(Comparator.comparingInt((ChunkResult r) -> r.cz).thenComparingInt(r -> r.cx));
      Set<Long> visited = new HashSet<>();
      List<Region> regions = new ArrayList<>();

      for (ChunkResult seed : ordered) {
         if (visited.contains(key(seed.cx, seed.cz))) continue;
         List<ChunkResult> group = new ArrayList<>();
         List<ChunkResult> queue = new ArrayList<>();
         queue.add(seed);
         visited.add(key(seed.cx, seed.cz));

         // Flood-fill adjacent chunks with the same status into one readable marker.
         for (int i = 0; i < queue.size() && queue.size() < 64; i++) {
            ChunkResult current = queue.get(i);
            group.add(current);
            for (int ox = -1; ox <= 1; ox++) {
               for (int oz = -1; oz <= 1; oz++) {
                  if (Math.abs(ox) + Math.abs(oz) != 1) continue;
                  long nextKey = key(current.cx + ox, current.cz + oz);
                  ChunkResult next = candidates.get(nextKey);
                  if (next != null && next.status == seed.status && visited.add(nextKey)) {
                     queue.add(next);
                  }
               }
            }
         }

         double centerX = 0.0;
         double centerZ = 0.0;
         double score = 0.0;
         boolean favorite = false;
         for (ChunkResult part : group) {
            centerX += part.cx + 0.5;
            centerZ += part.cz + 0.5;
            score = Math.max(score, part.score);
            favorite |= ChunkFavorites.isFavorite(part.cx, part.cz);
         }
         regions.add(new Region(centerX / group.size(), centerZ / group.size(), group.size(),
            score, favorite, seed.status));
      }

      regions.sort(Comparator
         .comparing((Region r) -> !(r.favorite && this.showFavorites.get()))
         .thenComparingDouble(r -> distanceSquared(r, pcx, pcz))
         .thenComparing((a, b) -> Double.compare(b.score, a.score)));
      return regions;
   }

   private static double distanceSquared(Region region, int pcx, int pcz) {
      double dx = region.chunkX - pcx - 0.5;
      double dz = region.chunkZ - pcz - 0.5;
      return dx * dx + dz * dz;
   }

   private static int alpha(int color, double factor) {
      int a = (int)Math.max(0.0, Math.min(255.0, 255.0 * factor));
      return Theme.withAlpha(color, a);
   }

   private static void ring(class_332 ctx, float cx, float cy, float radius, int color, int thickness) {
      final int segments = 20;
      float previousX = cx + radius;
      float previousY = cy;
      for (int i = 1; i <= segments; i++) {
         double angle = Math.PI * 2.0 * i / segments;
         float nextX = cx + (float)Math.cos(angle) * radius;
         float nextY = cy + (float)Math.sin(angle) * radius;
         Draw.line(ctx, previousX, previousY, nextX, nextY, color, thickness, true);
         previousX = nextX;
         previousY = nextY;
      }
   }

   private void drawMarker(class_332 ctx, Region region, long now, double distance, double maxDistance) {
      double fade = this.fadeDistance.get()
         ? Math.max(0.22, Math.min(1.0, 1.0 - Math.max(0.0, distance / maxDistance - 0.45) / 0.55))
         : 1.0;

      int baseColor = region.favorite && this.showFavorites.get()
         ? this.favoriteColor.rgb()
         : region.status == ChunkResult.Status.GONE ? this.goneColor.rgb() : this.susColor.rgb();

      double worldX = region.chunkX * 16.0;
      double worldZ = region.chunkZ * 16.0;
      double worldY = Math.floor(mc.field_1724.method_23318()) + 0.12;
      double[] point = new double[2];
      boolean onScreen = Projection.project(worldX, worldY, worldZ, point);
      if (!onScreen && !Projection.projectToEdge(worldX, worldY, worldZ, point)) return;

      float x = (float)point[0];
      float y = (float)point[1];
      double phase = now / 1_000_000_000.0 * this.pulseSpeed.get();
      float pulse = (float)(0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
      float radius = (float)this.markerSize.get() + (float)Math.min(5.0, Math.sqrt(region.count) * 1.4);
      int strong = alpha(baseColor, this.opacity.get() * fade);
      int soft = alpha(baseColor, this.opacity.get() * fade * 0.28);

      if (this.style.is("Pulse")) {
         float waveRadius = radius + 5.0F + pulse * 8.0F;
         ring(ctx, x, y, waveRadius, alpha(baseColor, this.opacity.get() * fade * (0.18 + pulse * 0.22)), 1);
      }

      ring(ctx, x, y, radius, strong, region.favorite ? 2 : 1);
      // A small center pip replaces the old square chunk outline entirely.
      ctx.method_25294(Math.round(x - 1.5F), Math.round(y - 1.5F),
         Math.round(x + 1.5F), Math.round(y + 1.5F), strong);
      Draw.line(ctx, x - radius - 3.0F, y, x - radius + 1.0F, y, soft, 1);
      Draw.line(ctx, x + radius - 1.0F, y, x + radius + 3.0F, y, soft, 1);
      Draw.line(ctx, x, y - radius - 3.0F, x, y - radius + 1.0F, soft, 1);
      Draw.line(ctx, x, y + radius - 1.0F, x, y + radius + 3.0F, soft, 1);

      String status = region.status == ChunkResult.Status.GONE ? "GONE" : "SUS";
      String label = (region.favorite && this.showFavorites.get() ? "★ " : "")
         + status + (region.count > 1 ? "  " + region.count + "x" : "")
         + (this.showScore.get() ? "  " + String.format(java.util.Locale.ROOT, "%.1f", region.score) : "");
      int textWidth = mc.field_1772.method_1727(label);
      int panelWidth = textWidth + 12;
      int panelHeight = 14;
      int labelX = Math.max(3, Math.min(Projection.width() - panelWidth - 3, Math.round(x + radius + 7.0F)));
      int labelY = Math.max(3, Math.min(Projection.height() - panelHeight - 3, Math.round(y - panelHeight * 0.5F)));

      if (this.style.is("Beacon") && onScreen) {
         Draw.line(ctx, x, y - radius - 2.0F, x, labelY + panelHeight, alpha(baseColor, this.opacity.get() * fade * 0.55), 1);
      }
      ctx.method_25294(labelX, labelY, labelX + panelWidth, labelY + panelHeight,
         alpha(Theme.HUD_PANEL, Math.min(0.92, this.opacity.get() * fade * 0.92)));
      ctx.method_25294(labelX, labelY, labelX + 2, labelY + panelHeight, strong);
      ctx.method_51433(mc.field_1772, label, labelX + 6, labelY + 3, Theme.TEXT, true);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;
      BaseFinder finder = ModuleManager.get(BaseFinder.class);
      if (finder == null || !finder.isEnabled()) return;

      int playerChunkX = mc.field_1724.method_31477() >> 4;
      int playerChunkZ = mc.field_1724.method_31479() >> 4;
      long now = System.nanoTime();
      int markCount = finder.marks().size();

      if (now - this.lastRegionBuildNanos >= 100_000_000L
         || playerChunkX != this.cachedPlayerChunkX
         || playerChunkZ != this.cachedPlayerChunkZ
         || markCount != this.cachedMarkCount) {
         this.cachedRegions = this.collectRegions(finder.marks(), playerChunkX, playerChunkZ, this.range.getInt());
         this.lastRegionBuildNanos = now;
         this.cachedPlayerChunkX = playerChunkX;
         this.cachedPlayerChunkZ = playerChunkZ;
         this.cachedMarkCount = markCount;
      }

      double maxDistance = Math.max(16.0, this.range.get() * 16.0);
      int drawn = 0;
      for (Region region : this.cachedRegions) {
         if (drawn >= this.maxRegions.getInt() || EspBase.drawn >= 2048) break;
         double dx = region.chunkX * 16.0 - mc.field_1724.method_23317();
         double dz = region.chunkZ * 16.0 - mc.field_1724.method_23321();
         double distance = Math.sqrt(dx * dx + dz * dz);
         this.drawMarker(ctx, region, now, distance, maxDistance);
         drawn++;
         EspBase.drawn++;
      }
   }

   private record Region(double chunkX, double chunkZ, int count, double score, boolean favorite, ChunkResult.Status status) {
   }
}

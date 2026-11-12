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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_332;

public final class ChunkEsp extends Module {
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Frames", "Surface", "Frames", "Pulse"));
   private final NumberSetting dist = this.add(new NumberSetting("Max Distanz (Chunks)", 12.0, 2.0, 48.0, 1.0));
   private final NumberSetting height = this.add(new NumberSetting("Rahmenhoehe", 24.0, 4.0, 128.0, 2.0));
   private final NumberSetting speed = this.add(new NumberSetting("Pulse-Speed", 1.0, 0.2, 4.0, 0.1));
   private final NumberSetting thick = this.add(new NumberSetting("Linienstaerke", 1.0, 1.0, 4.0, 1.0));
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", 0.82, 0.2, 1.0, 0.05));
   private final ColorSetting susColor = this.add(new ColorSetting("SUS-Farbe", -6588939));
   private final ColorSetting goneColor = this.add(new ColorSetting("SUS-WEG-Farbe", -12351783));
   private final ColorSetting favoriteColor = this.add(new ColorSetting("Favoriten-Farbe", -14246));
   private final BoolSetting fadeDistance = this.add(new BoolSetting("Distanz-Fade", true));
   private final NumberSetting fadeStart = this.add(new NumberSetting("Fade ab (Anteil)", 0.55, 0.1, 0.9, 0.05));
   private final BoolSetting showScore = this.add(new BoolSetting("Score anzeigen", true));
   private final BoolSetting showGone = this.add(new BoolSetting("SUS WEG anzeigen", true));
   private final BoolSetting showFavorites = this.add(new BoolSetting("Favoriten hervorheben", true));
   private final NumberSetting maxObj = this.add(new NumberSetting("Max Regionen", 32.0, 4.0, 256.0, 4.0));
   private List<ChunkEsp.Region> cachedRegions = List.of();
   private long lastRegionBuildNanos;
   private int cachedPlayerChunkX = Integer.MIN_VALUE;
   private int cachedPlayerChunkZ = Integer.MIN_VALUE;
   private int cachedMarkCount = -1;

   public ChunkEsp() {
      super("ChunkESP", "Zeigt zusammengefasste SUS-Regionen und Favoriten.", Category.VISUALS);
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 ^ cz & 4294967295L;
   }

   private List<ChunkEsp.Region> mergeAdjacent(Collection<ChunkResult> source, int pcx, int pcz, int range) {
      Map<Long, ChunkResult> candidates = new HashMap<>();

      for (ChunkResult r : source) {
         if (r.status != ChunkResult.Status.GONE || this.showGone.get()) {
            int dx = r.cx - pcx;
            int dz = r.cz - pcz;
            if (dx * dx + dz * dz <= range * range) {
               candidates.put(key(r.cx, r.cz), r);
            }
         }
      }

      List<ChunkResult> ordered = new ArrayList<>(candidates.values());
      ordered.sort(Comparator.<ChunkResult>comparingInt(rx -> rx.cz).thenComparingInt(rx -> rx.cx));
      Set<Long> visited = new HashSet<>();
      List<ChunkEsp.Region> regions = new ArrayList<>();

      label90:
      for (ChunkResult seed : ordered) {
         if (!visited.contains(key(seed.cx, seed.cz))) {
            int width = 1;

            while (true) {
               ChunkResult n = candidates.get(key(seed.cx + width, seed.cz));
               if (n == null || n.status != seed.status || visited.contains(key(n.cx, n.cz))) {
                  int depth = 1;

                  while (true) {
                     boolean full = true;

                     for (int x = 0; x < width; x++) {
                        ChunkResult nx = candidates.get(key(seed.cx + x, seed.cz + depth));
                        if (nx == null || nx.status != seed.status || visited.contains(key(nx.cx, nx.cz))) {
                           full = false;
                           break;
                        }
                     }

                     if (!full) {
                        double score = 0.0;
                        boolean favorite = false;

                        for (int z = 0; z < depth; z++) {
                           for (int xx = 0; xx < width; xx++) {
                              ChunkResult part = candidates.get(key(seed.cx + xx, seed.cz + z));
                              visited.add(key(part.cx, part.cz));
                              score = Math.max(score, part.score);
                              favorite |= ChunkFavorites.isFavorite(part.cx, part.cz);
                           }
                        }

                        regions.add(new ChunkEsp.Region(seed.cx, seed.cz, width, depth, width * depth, score, favorite, seed.status));
                        continue label90;
                     }

                     depth++;
                  }
               }

               width++;
            }
         }
      }

      return regions;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && Projection.valid()) {
         BaseFinder finder = ModuleManager.get(BaseFinder.class);
         if (finder.isEnabled()) {
            int pcx = mc.field_1724.method_31477() >> 4;
            int pcz = mc.field_1724.method_31479() >> 4;
            long now = System.nanoTime();
            int markCount = finder.marks().size();
            if (now - this.lastRegionBuildNanos >= 50_000_000L
               || pcx != this.cachedPlayerChunkX || pcz != this.cachedPlayerChunkZ
               || markCount != this.cachedMarkCount) {
               this.cachedRegions = this.mergeAdjacent(finder.marks(), pcx, pcz, this.dist.getInt());
               this.lastRegionBuildNanos = now;
               this.cachedPlayerChunkX = pcx;
               this.cachedPlayerChunkZ = pcz;
               this.cachedMarkCount = markCount;
            }
            List<ChunkEsp.Region> regions = this.cachedRegions;
            double phase = System.currentTimeMillis() % 100000L / 1000.0 * this.speed.get();
            double pulse = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0);
            double py = Math.floor(mc.field_1724.method_23318());
            int drawn = 0;

            for (ChunkEsp.Region r : regions) {
               if (drawn >= this.maxObj.getInt() || EspBase.drawn >= 2048) {
                  break;
               }

               float scoreT = (float)Math.max(0.0, Math.min(1.0, r.score / (finder.thresholdValue() * 3.0)));
               int baseColor = r.status == ChunkResult.Status.GONE ? this.goneColor.rgb() : Theme.lerp(this.susColor.rgb(), -44434, scoreT);
               if (r.favorite && this.showFavorites.get()) {
                  baseColor = this.favoriteColor.rgb();
               }

               double x0 = r.cx * 16.0;
               double z0 = r.cz * 16.0;
               double x1 = x0 + r.width * 16.0;
               double z1 = z0 + r.depth * 16.0;
               double h = this.height.get();
               double y0 = py - h / 2.0;
               double rdx = (x0 + x1) * 0.5 - Projection.eyeX();
               double rdz = (z0 + z1) * 0.5 - Projection.eyeZ();
               double regionDistance = Math.sqrt(rdx * rdx + rdz * rdz);
               double fade = 1.0;
               if (this.fadeDistance.get()) {
                  double normalized = regionDistance / Math.max(16.0, this.dist.get() * 16.0);
                  double progress = (normalized - this.fadeStart.get()) / Math.max(0.01, 1.0 - this.fadeStart.get());
                  fade = Math.max(0.0, Math.min(1.0, 1.0 - progress));
               }

               if (!(fade <= 0.005)) {
                  int alpha = (int)(this.opacity.get() * 255.0 * fade);
                  int color = 0xFF000000 | baseColor & 16777215;
                  String label = this.mode.get();
                  double boxMinY;
                  double boxMaxY;
                  switch (label) {
                     case "Surface":
                        boxMinY = py - 0.06;
                        boxMaxY = py - 0.02;
                        break;
                     case "Pulse":
                        double hh = h * (0.65 + 0.35 * pulse);
                        boxMinY = py - hh / 2.0;
                        boxMaxY = py + hh / 2.0;
                        alpha = (int)(alpha * (0.55 + 0.45 * pulse));
                        break;
                     default:
                        boxMinY = y0;
                        boxMaxY = y0 + h;
                  }

                  if (Draw.box(ctx, x0, boxMinY, z0, x1, boxMaxY, z1,
                     Theme.withAlpha(color, alpha), this.thick.getInt(), true, true)) {
                     label = (r.favorite && this.showFavorites.get() ? "★ " : "")
                        + (r.status == ChunkResult.Status.GONE ? "SUS WEG" : "SUS")
                        + (r.count > 1 ? " · " + r.count + " Chunks" : "")
                        + (this.showScore.get() ? " · " + String.format(Locale.ROOT, "%.1f", r.score) : "");
                     int tw = mc.field_1772.method_1727(label);
                     int lx = Math.round(Draw.topX - tw / 2.0F);
                     int ly = Math.round(Draw.topY - 14.0F);
                     ctx.method_51433(mc.field_1772, label, lx, ly, -657670, true);
                     drawn++;
                     EspBase.drawn++;
                  }
               }
            }
         }
      }
   }

   private record Region(int cx, int cz, int width, int depth, int count, double score, boolean favorite, ChunkResult.Status status) {
   }
}

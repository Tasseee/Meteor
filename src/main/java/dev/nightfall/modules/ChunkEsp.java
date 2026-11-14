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
import net.minecraft.class_332;

public final class ChunkEsp extends Module {
   private final ModeSetting mode = this.add(new ModeSetting("Modus", "Surface", "Surface", "Frames", "Pulse"));
   private final NumberSetting dist = this.add(new NumberSetting("Max Distanz (Chunks)", 12.0, 2.0, 48.0, 1.0));
   private final NumberSetting height = this.add(new NumberSetting("Rahmenhoehe", 24.0, 4.0, 128.0, 2.0));
   private final NumberSetting speed = this.add(new NumberSetting("Pulse-Speed", 1.0, 0.2, 4.0, 0.1));
   private final NumberSetting thick = this.add(new NumberSetting("Linienstaerke", 1.0, 1.0, 4.0, 1.0));
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", 0.78, 0.2, 1.0, 0.05));
   private final ColorSetting susColor = this.add(new ColorSetting("SUS-Farbe", -6588939));
   private final ColorSetting goneColor = this.add(new ColorSetting("SUS-WEG-Farbe", -12351783));
   private final ColorSetting favoriteColor = this.add(new ColorSetting("Favoriten-Farbe", -14246));
   private final BoolSetting fadeDistance = this.add(new BoolSetting("Distanz-Fade", true));
   private final NumberSetting fadeStart = this.add(new NumberSetting("Fade ab (Anteil)", 0.55, 0.1, 0.9, 0.05));
   private final BoolSetting showScore = this.add(new BoolSetting("Score anzeigen", true));
   private final BoolSetting showGone = this.add(new BoolSetting("SUS WEG anzeigen", true));
   private final BoolSetting showFavorites = this.add(new BoolSetting("Favoriten hervorheben", true));
   private final NumberSetting maxObj = this.add(new NumberSetting("Max Regionen", 32.0, 4.0, 256.0, 4.0));
   private List<Region> cachedRegions = List.of();
   private long lastRegionBuildNanos;
   private int cachedPlayerChunkX = Integer.MIN_VALUE;
   private int cachedPlayerChunkZ = Integer.MIN_VALUE;
   private int cachedMarkCount = -1;

   public ChunkEsp() {
      super("ChunkESP", "Zeigt SUS-Regionen und Favoriten mit klaren, leichten Markierungen.", Category.VISUALS);
   }

   private static long key(int cx, int cz) {
      return (long)cx << 32 ^ cz & 4294967295L;
   }

   private List<Region> mergeAdjacent(Collection<ChunkResult> source, int pcx, int pcz, int range) {
      Map<Long, ChunkResult> candidates = new HashMap<>();

      for (ChunkResult result : source) {
         if (result.status != ChunkResult.Status.GONE || this.showGone.get()) {
            int dx = result.cx - pcx;
            int dz = result.cz - pcz;
            if (dx * dx + dz * dz <= range * range) {
               candidates.put(key(result.cx, result.cz), result);
            }
         }
      }

      List<ChunkResult> ordered = new ArrayList<>(candidates.values());
      ordered.sort(Comparator.<ChunkResult>comparingInt(result -> result.cz).thenComparingInt(result -> result.cx));
      Set<Long> visited = new HashSet<>();
      List<Region> regions = new ArrayList<>();

      outer:
      for (ChunkResult seed : ordered) {
         if (visited.contains(key(seed.cx, seed.cz))) continue;

         int width = 1;
         while (true) {
            ChunkResult next = candidates.get(key(seed.cx + width, seed.cz));
            if (next == null || next.status != seed.status || visited.contains(key(next.cx, next.cz))) break;
            width++;
         }

         int depth = 1;
         while (true) {
            boolean completeRow = true;
            for (int x = 0; x < width; x++) {
               ChunkResult next = candidates.get(key(seed.cx + x, seed.cz + depth));
               if (next == null || next.status != seed.status || visited.contains(key(next.cx, next.cz))) {
                  completeRow = false;
                  break;
               }
            }
            if (!completeRow) break;
            depth++;
         }

         double score = 0.0;
         boolean favorite = false;
         for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
               ChunkResult part = candidates.get(key(seed.cx + x, seed.cz + z));
               if (part == null) continue;
               visited.add(key(part.cx, part.cz));
               score = Math.max(score, part.score);
               favorite |= ChunkFavorites.isFavorite(part.cx, part.cz);
            }
         }

         regions.add(new Region(seed.cx, seed.cz, width, depth, width * depth, score, favorite, seed.status));
      }

      regions.sort(Comparator
         .comparing((Region region) -> !(region.favorite && this.showFavorites.get()))
         .thenComparingDouble(region -> distanceSquared(region, pcx, pcz))
         .thenComparing((a, b) -> Double.compare(b.score, a.score)));
      return regions;
   }

   private static double distanceSquared(Region region, int pcx, int pcz) {
      double dx = region.cx + region.width * 0.5 - pcx;
      double dz = region.cz + region.depth * 0.5 - pcz;
      return dx * dx + dz * dz;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || !Projection.valid()) return;
      BaseFinder finder = ModuleManager.get(BaseFinder.class);
      if (!finder.isEnabled()) return;

      int playerChunkX = mc.field_1724.method_31477() >> 4;
      int playerChunkZ = mc.field_1724.method_31479() >> 4;
      long now = System.nanoTime();
      int markCount = finder.marks().size();

      if (now - this.lastRegionBuildNanos >= 100_000_000L
         || playerChunkX != this.cachedPlayerChunkX
         || playerChunkZ != this.cachedPlayerChunkZ
         || markCount != this.cachedMarkCount) {
         this.cachedRegions = this.mergeAdjacent(finder.marks(), playerChunkX, playerChunkZ, this.dist.getInt());
         this.lastRegionBuildNanos = now;
         this.cachedPlayerChunkX = playerChunkX;
         this.cachedPlayerChunkZ = playerChunkZ;
         this.cachedMarkCount = markCount;
      }

      double baseY = Math.floor(mc.field_1724.method_23318()) + 0.035;
      double pulse = 0.5 + 0.5 * Math.sin(now / 1_000_000_000.0 * this.speed.get() * Math.PI * 2.0);
      int drawn = 0;

      for (Region region : this.cachedRegions) {
         if (drawn >= this.maxObj.getInt() || EspBase.drawn >= 2048) break;

         int color = region.status == ChunkResult.Status.GONE ? this.goneColor.rgb() : this.susColor.rgb();
         if (region.favorite && this.showFavorites.get()) color = this.favoriteColor.rgb();

         double dx = (region.cx + region.width * 0.5) * 16.0 - mc.field_1724.method_23317();
         double dz = (region.cz + region.depth * 0.5) * 16.0 - mc.field_1724.method_23321();
         double distance = Math.sqrt(dx * dx + dz * dz);
         double maxDistance = Math.max(16.0, this.dist.get() * 16.0);
         double fade = this.fadeDistance.get()
            ? Math.max(0.16, Math.min(1.0, 1.0 - Math.max(0.0, distance / maxDistance - this.fadeStart.get()) / Math.max(0.01, 1.0 - this.fadeStart.get())))
            : 1.0;

         double x0 = region.cx * 16.0;
         double z0 = region.cz * 16.0;
         double x1 = x0 + region.width * 16.0;
         double z1 = z0 + region.depth * 16.0;
         double boxHeight = 0.035;
         double alphaFactor = 1.0;

         switch (this.mode.get()) {
            case "Frames" -> boxHeight = this.height.get();
            case "Pulse" -> {
               boxHeight = this.height.get() * (0.35 + pulse * 0.65);
               alphaFactor = 0.72 + pulse * 0.28;
            }
            default -> boxHeight = 0.035;
         }

         int alpha = (int)(this.opacity.get() * fade * alphaFactor * 255.0);
         int outline = Theme.withAlpha(color, Math.max(12, Math.min(255, alpha)));
         boolean visible = Draw.box(ctx, x0, baseY, z0, x1, baseY + boxHeight, z1, outline,
            Math.max(1, this.thick.getInt()), true, true);
         if (!visible) continue;

         String status = region.status == ChunkResult.Status.GONE ? "GONE" : "SUS";
         String label = (region.favorite && this.showFavorites.get() ? "★ " : "")
            + status
            + (region.count > 1 ? "  ×" + region.count : "")
            + (this.showScore.get() ? String.format(Locale.ROOT, "  %.1f", region.score) : "");

         int textWidth = mc.field_1772.method_1727(label);
         int panelWidth = textWidth + 14;
         int panelHeight = 14;
         int labelX = Math.max(2, Math.min(Projection.width() - panelWidth - 2,
            Math.round(Draw.topX - panelWidth * 0.5F)));
         int labelY = Math.max(2, Math.min(Projection.height() - panelHeight - 2,
            Math.round(Draw.topY - 18.0F)));

         ctx.method_25294(labelX, labelY, labelX + panelWidth, labelY + panelHeight,
            Theme.withAlpha(Theme.HUD_PANEL, Math.max(24, Math.min(230, (int)(220 * fade)))));
         ctx.method_25294(labelX, labelY, labelX + 2, labelY + panelHeight, outline);
         ctx.method_51433(mc.field_1772, label, labelX + 6, labelY + 3, Theme.TEXT, true);

         drawn++;
         EspBase.drawn++;
      }
   }

   private record Region(int cx, int cz, int width, int depth, int count, double score, boolean favorite, ChunkResult.Status status) {
   }
}

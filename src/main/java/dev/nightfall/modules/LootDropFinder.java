package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_332;
import net.minecraft.class_3417;
import net.minecraft.class_638;

public final class LootDropFinder extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 64.0, 8.0, 256.0, 8.0));
   private final NumberSetting markerSeconds = this.add(new NumberSetting("Chunk-Markierung (Sek.)", 60.0, 5.0, 300.0, 5.0));
   private final BoolSetting emeraldBlock = this.add(new BoolSetting("Emerald-Block", true));
   private final BoolSetting diamondBlock = this.add(new BoolSetting("Diamant-Block", true));
   private final BoolSetting ironBlock = this.add(new BoolSetting("Eisen-Block", true));
   private final BoolSetting heavyCore = this.add(new BoolSetting("Heavy Core", true));
   private final BoolSetting netheriteGoldBlocks = this.add(new BoolSetting("Netherite-/Goldblock", true));
   private final BoolSetting gildedBlackstone = this.add(new BoolSetting("Gilded Blackstone", true));
   private final BoolSetting soundAlert = this.add(new BoolSetting("Sound bei Fund", true));
   private final BoolSetting chatAlert = this.add(new BoolSetting("Nachricht bei Fund", true));
   private static final int CHUNK_REFRESH_TICKS = 100;
   private static final int CHUNKS_PER_TICK = 2;
   private static final int MARKER_COLOR = -432488968;
   private final Map<Long, List<LootDropFinder.Target>> blockTargetsByChunk = new HashMap<>();
   private final Map<Long, Long> lastChunkScan = new HashMap<>();
   private final Map<Long, Long> markedChunks = new HashMap<>();
   private final Map<Long, int[]> markerHeights = new HashMap<>();
   private int scannedChunks;
   private int totalDetections;
   private String lastFoundLabel = "Noch keine Funde";
   private long lastFoundAt;
   private final ArrayDeque<Long> chunkQueue = new ArrayDeque<>();
   private final Set<Long> queuedChunks = new HashSet<>();
   private class_638 lastWorld;
   private long tick;
   private long lastRefillTick;
   private boolean initialPassStarted;
   private boolean initialPassComplete;

   public LootDropFinder() {
      super("LootDropFinder", "Meldet gefundene Loot-Hinweise mit Sound und markiert nur den betroffenen Chunk.", Category.VISUALS);
   }

   private static long chunkKey(int cx, int cz) {
      return cx & 4294967295L | (long)cz << 32;
   }

   private static int chunkX(long key) {
      return (int)key;
   }

   private static int chunkZ(long key) {
      return (int)(key >> 32);
   }

   @Override
   protected void onEnable() {
      this.clearState();
   }

   @Override
   protected void onDisable() {
      this.clearState();
   }

   @Override
   public void onWorldLeave() {
      this.clearState();
      this.lastWorld = null;
   }

   private void clearState() {
      this.blockTargetsByChunk.clear();
      this.lastChunkScan.clear();
      this.markedChunks.clear();
      this.markerHeights.clear();
      this.scannedChunks = 0;
      this.totalDetections = 0;
      this.lastFoundLabel = "Noch keine Funde";
      this.lastFoundAt = 0L;
      this.chunkQueue.clear();
      this.queuedChunks.clear();
      this.tick = 0L;
      this.lastRefillTick = 0L;
      this.initialPassStarted = false;
      this.initialPassComplete = false;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (this.lastWorld != mc.field_1687) {
            this.clearState();
            this.lastWorld = mc.field_1687;
         }

         this.tick++;
         this.markedChunks.entrySet().removeIf(e -> this.tick - e.getValue() > this.markerSeconds.get() * 20.0);
         List<LootDropFinder.Target> found = new ArrayList<>();
         if (this.chunkQueue.isEmpty() && this.tick - this.lastRefillTick >= 20L) {
            this.refillChunkQueue();
         }

         for (int i = 0; i < 2 && !this.chunkQueue.isEmpty(); i++) {
            long key = this.chunkQueue.pollFirst();
            this.queuedChunks.remove(key);
            this.scanChunk(key, found);
         }

         if (this.initialPassStarted && !this.initialPassComplete && this.chunkQueue.isEmpty()) {
            this.initialPassComplete = true;
         }

         if (!found.isEmpty()) {
            this.alertByChunk(found);
         }
      }
   }

   private void refillChunkQueue() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         this.lastRefillTick = this.tick;
         int blockRange = this.range.getInt();
         int chunkRange = blockRange + 15 >> 4;
         int pcx = mc.field_1724.method_31477() >> 4;
         int pcz = mc.field_1724.method_31479() >> 4;
         double rangeSq = (double)blockRange * blockRange;
         Iterator<Long> cached = this.blockTargetsByChunk.keySet().iterator();

         while (cached.hasNext()) {
            long key = cached.next();
            double dx = chunkX(key) * 16.0 + 8.0 - mc.field_1724.method_23317();
            double dz = chunkZ(key) * 16.0 + 8.0 - mc.field_1724.method_23321();
            if (dx * dx + dz * dz > rangeSq + 512.0 || mc.field_1687.method_2935().method_21730(chunkX(key), chunkZ(key)) == null) {
               cached.remove();
               this.lastChunkScan.remove(key);
               this.queuedChunks.remove(key);
               this.chunkQueue.remove(key);
            }
         }

         List<Long> candidates = new ArrayList<>();

         for (int dz = -chunkRange; dz <= chunkRange; dz++) {
            for (int dx = -chunkRange; dx <= chunkRange; dx++) {
               int cx = pcx + dx;
               int cz = pcz + dz;
               double wx = cx * 16.0 + 8.0 - mc.field_1724.method_23317();
               double wz = cz * 16.0 + 8.0 - mc.field_1724.method_23321();
               if (!(wx * wx + wz * wz > rangeSq + 512.0) && mc.field_1687.method_2935().method_21730(cx, cz) != null) {
                  long key = chunkKey(cx, cz);
                  if (!this.queuedChunks.contains(key) && this.tick - this.lastChunkScan.getOrDefault(key, -4611686018427387904L) >= 100L) {
                     candidates.add(key);
                  }
               }
            }
         }

         candidates.sort(Comparator.comparingDouble(keyx -> {
            double dxx = chunkX(keyx) * 16.0 + 8.0 - mc.field_1724.method_23317();
            double dzx = chunkZ(keyx) * 16.0 + 8.0 - mc.field_1724.method_23321();
            return dxx * dxx + dzx * dzx;
         }));
         if (!this.initialPassStarted && !candidates.isEmpty()) {
            this.initialPassStarted = true;
         }

         for (long key : candidates) {
            this.chunkQueue.addLast(key);
            this.queuedChunks.add(key);
         }
      }
   }

   private void scanChunk(long key, List<LootDropFinder.Target> found) {
      if (mc.field_1687 != null) {
         int cx = chunkX(key);
         int cz = chunkZ(key);
         class_2818 chunk = mc.field_1687.method_2935().method_21730(cx, cz);
         if (chunk == null) {
            this.blockTargetsByChunk.remove(key);
            this.lastChunkScan.remove(key);
         } else {
            List<LootDropFinder.Target> old = this.blockTargetsByChunk.getOrDefault(key, List.of());
            Set<String> oldIds = new HashSet<>();

            for (LootDropFinder.Target target : old) {
               oldIds.add(target.id);
            }

            List<LootDropFinder.Target> next = new ArrayList<>();
            class_2826[] sections = chunk.method_12006();

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
               class_2826 section = sections[sectionIndex];
               if (section != null && !section.method_38292()) {
                  int baseY = chunk.method_31607() + sectionIndex * 16;

                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           class_2680 state = section.method_12254(x, y, z);
                           String name = null;
                           if (this.emeraldBlock.get() && state.method_27852(class_2246.field_10234)) {
                              name = "Emerald-Block";
                           } else if (this.diamondBlock.get() && state.method_27852(class_2246.field_10201)) {
                              name = "Diamant-Block";
                           } else if (this.ironBlock.get() && state.method_27852(class_2246.field_10085)) {
                              name = "Eisen-Block";
                           } else if (this.heavyCore.get() && state.method_27852(class_2246.field_49830)) {
                              name = "Heavy Core";
                           } else if (!this.netheriteGoldBlocks.get()
                              || !state.method_27852(class_2246.field_22108) && !state.method_27852(class_2246.field_10205)) {
                              if (this.gildedBlackstone.get() && state.method_27852(class_2246.field_23880)) {
                                 name = "Gilded Blackstone";
                              }
                           } else {
                              name = "Netherite-/Goldblock";
                           }

                           if (name != null) {
                              class_2338 pos = new class_2338((cx << 4) + x, baseY + y, (cz << 4) + z);
                              String id = "block:" + pos.method_10063() + ":" + name;
                              LootDropFinder.Target target = new LootDropFinder.Target(id, name, pos);
                              next.add(target);
                              if (this.initialPassComplete && !oldIds.contains(id)) {
                                 found.add(target);
                              }
                           }
                        }
                     }
                  }
               }
            }

            this.blockTargetsByChunk.put(key, next);
            this.lastChunkScan.put(key, this.tick);
            this.scannedChunks++;
         }
      }
   }

   private void alertByChunk(List<LootDropFinder.Target> found) {
      Map<Long, List<LootDropFinder.Target>> grouped = new LinkedHashMap<>();

      for (LootDropFinder.Target target : found) {
         class_243 p = target.center();
         long key = chunkKey((int)Math.floor(p.field_1352) >> 4, (int)Math.floor(p.field_1350) >> 4);
         grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(target);
      }

      for (Entry<Long, List<LootDropFinder.Target>> entry : grouped.entrySet()) {
         long key = entry.getKey();
         if (this.blockTargetsByChunk.getOrDefault(key, List.of()).size() >= 10 && !this.markedChunks.containsKey(key)) {
            this.markedChunks.put(key, this.tick);
            List<LootDropFinder.Target> items = entry.getValue();
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (LootDropFinder.Target item : items) {
               int targetY = item.pos != null ? item.pos.method_10264() : (int)Math.floor(item.center().field_1351);
               minY = Math.min(minY, targetY);
               maxY = Math.max(maxY, targetY);
            }

            this.markerHeights
               .put(
                  key,
                  new int[]{Math.max(mc.field_1687.method_31607(), minY - 4), Math.min(mc.field_1687.method_31607() + mc.field_1687.method_31605(), maxY + 5)}
               );
            this.totalDetections = this.totalDetections + items.size();
            this.lastFoundLabel = items.size() + " Hinweis(e) · Chunk " + chunkX(key) + " / " + chunkZ(key);
            this.lastFoundAt = System.currentTimeMillis();
            if (this.chatAlert.get() && mc.field_1724 != null) {
               mc.field_1724
                  .method_7353(
                     class_2561.method_43470("[Nightfall] LOOTDROP FOUND! Chunk " + chunkX(key) + " / " + chunkZ(key) + " · " + items.size() + " Treffer"),
                     false
                  );
            }

            if (this.soundAlert.get() && mc.field_1724 != null) {
               mc.field_1724.method_5783(class_3417.field_14627, 0.8F, 1.15F);
            }
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 != null && mc.field_1724 != null && Projection.valid()) {
         this.renderRadarPanel(ctx);
         List<Long> keys = new ArrayList<>(this.markedChunks.keySet());
         keys.sort(Comparator.comparingDouble(keyx -> {
            double xx = chunkX(keyx) * 16.0 + 8.0 - Projection.eyeX();
            double zx = chunkZ(keyx) * 16.0 + 8.0 - Projection.eyeZ();
            return xx * xx + zx * zx;
         }));
         int drawn = 0;
         int minY = mc.field_1687.method_31607();
         int maxY = mc.field_1687.method_31607() + mc.field_1687.method_31605();

         for (long key : keys) {
            if (drawn >= 64) {
               break;
            }

            int x = chunkX(key) << 4;
            int z = chunkZ(key) << 4;
            int[] height = this.markerHeights.getOrDefault(key, new int[]{minY, maxY});
            class_238 chunkBox = new class_238(x, height[0], z, x + 16, height[1], z + 16);
            Draw.box(ctx, chunkBox, 809025016, 5, true, true);
            if (Draw.box(ctx, chunkBox, -432488968, 2, true, true)) {
               drawn++;
            }
         }
      }
   }

   private void renderRadarPanel(class_332 ctx) {
      int sw = mc.method_22683().method_4486();
      int x = 10;
      int y = 10;
      int w = Math.min(214, sw - 20);
      int h = 76;
      int accent = -13058568;
      ctx.method_25294(x, y, x + w, y + h, -653585888);
      ctx.method_25294(x, y, x + w, y + 2, accent);
      ctx.method_25294(x, y + 2, x + 3, y + h, -14326872);
      ctx.method_51433(mc.field_1772, "NIGHTFALL", x + 10, y + 7, -7218689, true);
      ctx.method_51433(mc.field_1772, "LOOT RADAR", x + 10, y + 19, -1, true);
      String state = this.initialPassComplete ? "LIVE SCAN" : (this.initialPassStarted ? "INITIAL SCAN" : "WAITING FOR CHUNKS");
      int stateColor = this.initialPassComplete ? -10099035 : -15253;
      ctx.method_25294(x + w - 72, y + 9, x + w - 10, y + 21, -1441257414);
      ctx.method_27534(mc.field_1772, class_2561.method_43470(state), x + w - 41, y + 11, stateColor);
      ctx.method_25294(x + 10, y + 34, x + w - 10, y + 35, 1430213480);
      String left = "RANGE " + this.range.getInt() + "m";
      String right = "QUEUE " + this.chunkQueue.size();
      ctx.method_51433(mc.field_1772, left, x + 10, y + 42, -2758411, false);
      ctx.method_51433(mc.field_1772, right, x + w - 10 - mc.field_1772.method_1727(right), y + 42, -2758411, false);
      String stats = "SCANNED " + this.scannedChunks + "   MARKED " + this.markedChunks.size();
      ctx.method_51433(mc.field_1772, stats, x + 10, y + 55, -7231044, false);
      if (this.lastFoundAt > 0L && System.currentTimeMillis() - this.lastFoundAt < 6000L) {
         String recent = "FOUND  " + this.lastFoundLabel;
         int rw = Math.min(sw - 20, mc.field_1772.method_1727(recent) + 20);
         int rx = 10;
         int ry = y + h + 6;
         ctx.method_25294(rx, ry, rx + rw, ry + 19, -435149515);
         ctx.method_25294(rx, ry, rx + 2, ry + 19, -10099035);
         ctx.method_51433(mc.field_1772, recent, rx + 9, ry + 6, -4589872, true);
      }
   }

   private static final class Target {
      final String id;
      final String label;
      final class_2338 pos;

      Target(String id, String label, class_2338 pos) {
         this.id = id;
         this.label = label;
         this.pos = pos.method_10062();
      }

      class_243 center() {
         return class_243.method_24953(this.pos);
      }
   }
}

package dev.nightfall.modules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2599;
import net.minecraft.class_2601;
import net.minecraft.class_2609;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2636;
import net.minecraft.class_2680;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3719;
import net.minecraft.class_638;
import net.minecraft.class_8887;

public class BaseFinder extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Range (Chunks)", 32.0, 2.0, 48.0, 1.0));
   private final NumberSetting budgetMs = this.add(new NumberSetting("Budget ms/Tick", 0.8, 0.2, 5.0, 0.1));
   private final NumberSetting rescan = this.add(new NumberSetting("Rescan (s)", 8.0, 2.0, 120.0, 1.0));
   private final NumberSetting threshold = this.add(new NumberSetting("Min Score", 8.0, 3.0, 100.0, 1.0));
   private final NumberSetting minSignals = this.add(new NumberSetting("Min. Signaltypen", 2.0, 1.0, 10.0, 1.0));
   private final NumberSetting wStorage = this.add(new NumberSetting("Gewicht Storage", 1.0, 0.1, 5.0, 0.1));
   private final NumberSetting wChestPenalty = this.add(new NumberSetting("Gewicht Kisten-Abzug", 0.25, 0.0, 5.0, 0.1));
   private final NumberSetting wHopper = this.add(new NumberSetting("Gewicht Hopper", 1.5, 0.1, 5.0, 0.1));
   private final NumberSetting wFurnace = this.add(new NumberSetting("Gewicht Furnace", 0.7, 0.1, 5.0, 0.1));
   private final NumberSetting wRedstone = this.add(new NumberSetting("Gewicht Redstone", 1.2, 0.1, 5.0, 0.1));
   private final NumberSetting wSpawner = this.add(new NumberSetting("Gewicht Spawner", 2.0, 0.1, 10.0, 0.1));
   private final BoolSetting detectSpawners = this.add(new BoolSetting("Mob-Spawner erkennen", true));
   private final BoolSetting detectMobClusters = this.add(new BoolSetting("Mob-Cluster erkennen", true));
   private final NumberSetting wRotatedDeepslate = this.add(new NumberSetting("Gewicht Rotated Deepslate", 1.0, 0.0, 6.0, 0.1));
   private final NumberSetting wLightSources = this.add(new NumberSetting("Gewicht Lichtquellen", 0.8, 0.0, 6.0, 0.1));
   private final NumberSetting wRedstonePattern = this.add(new NumberSetting("Gewicht Redstone-Muster", 1.0, 0.0, 6.0, 0.1));
   private final NumberSetting wFarm = this.add(new NumberSetting("Gewicht Farm-Struktur", 1.0, 0.0, 6.0, 0.1));
   private final NumberSetting wMobCluster = this.add(new NumberSetting("Gewicht Mob-Cluster", 1.0, 0.0, 6.0, 0.1));
   private final NumberSetting wCluster = this.add(new NumberSetting("Gewicht Lagerzone", 1.5, 0.0, 5.0, 0.1));
   private final NumberSetting clusterWeight = this.add(new NumberSetting("Gewicht Amethyst-Cluster", 0.25, 0.0, 2.0, 0.05));
   private final NumberSetting minClusters = this.add(new NumberSetting("Min. Amethyst-Cluster", 6.0, 1.0, 64.0, 1.0));
   private final NumberSetting wSculk = this.add(new NumberSetting("Gewicht Sculk", 1.2, 0.0, 8.0, 0.1));
   private final NumberSetting minTunnelLength = this.add(new NumberSetting("Min. Tunnel-Laenge", 6.0, 5.0, 12.0, 1.0));
   private final NumberSetting wTunnel = this.add(new NumberSetting("Gewicht Tunnel", 1.5, 0.0, 5.0, 0.1));
   private final BoolSetting chat = this.add(new BoolSetting("Chat-Meldung", true));
   private static final Gson GSON = new Gson();
   private static final Map<class_2248, String> BLOCK_NAME_CACHE = new HashMap<>();
   private static final Type MARK_LIST = (new TypeToken<List<BaseFinder.Mark>>() {}).getType();
   private static final Type FAKE_LIST = (new TypeToken<List<BaseFinder.FakePattern>>() {}).getType();
   private final Map<Long, ChunkResult> results = new HashMap<>();
   private final ArrayDeque<Long> queue = new ArrayDeque<>();
   private final List<ChunkResult> top = new ArrayList<>();
   private final List<ChunkResult> marks = new ArrayList<>();
   private final List<FakePattern> fakePatterns = new ArrayList<>();
   private boolean dirty = true;
   private boolean marksDirty;
   private class_638 lastWorld;
   private String ctxKey;
   private long tick;
   private long lastScanNanos;
   private int lastScanned;
   private int candidatesLastRefill;
   private int scannedSinceRefill;
   private boolean paused;
   private final Map<Long, Integer> mobClusterMaxByChunk = new HashMap<>();
   // Render modules call suppression checks for every visible ESP object every
   // frame. Keep the result as a hash set so rendering never scans all fake
   // patterns/waypoints repeatedly.
   private final Set<Long> suppressedChunkCache = new HashSet<>();
   private final Set<Long> confirmedFakeChunkCache = new HashSet<>();
   // Repeated identical SUS scores can be a strong clue for copied/decoy bases.
   // Keep this only as a secondary fingerprint; a matching chest layout is still
   // required before a chunk is classified as a fake base.
   private final Map<String, List<ScoreFingerprintObservation>> repeatedScoreFingerprints = new HashMap<>();
   private boolean suppressionCacheDirty = true;

   public BaseFinder() {
      super("BaseFinder", "Findet auffaellige Basis-Chunks in geladenen Chunks anhand von dichter Lagerung und mehreren unabhaengigen Technik-Signalen.", Category.BASEFINDER);
   }

   private static long key(int cx, int cz) {
      return cx & 4294967295L | (long)cz << 32;
   }

   public double thresholdValue() {
      return this.threshold.get();
   }

   public ChunkResult get(int cx, int cz) {
      return this.results.get(key(cx, cz));
   }

   public boolean isFakeChunk(int cx, int cz) {
      return this.isSuppressedChunk(cx, cz, true);
   }

   /**
    * Returns true when the chunk is already classified as a fake-base/decoy
    * location. StorageESP must use this broader classification than the
    * three-hit confirmation used for the public fake marker.
    */
   public boolean isSuppressedChunk(int cx, int cz) {
      return this.isSuppressedChunk(cx, cz, false);
   }

   private boolean isSuppressedChunk(int cx, int cz, boolean requireConfirmedPattern) {
      ensureSuppressionCache();
      long k = key(cx, cz);
      return requireConfirmedPattern
         ? this.confirmedFakeChunkCache.contains(k)
         : this.suppressedChunkCache.contains(k);
   }

   private void ensureSuppressionCache() {
      if (!this.suppressionCacheDirty) return;

      this.suppressedChunkCache.clear();
      this.confirmedFakeChunkCache.clear();

      String currentContext = Storage.server() + "_" + Storage.dim();
      if (!currentContext.equals(this.ctxKey)) {
         this.suppressionCacheDirty = false;
         return;
      }

      for (Waypoint waypoint : WaypointManager.all()) {
         if (waypoint != null
            && FakeBases.CATEGORY.equalsIgnoreCase(waypoint.category)
            && Storage.dim().equals(waypoint.dim)) {
            long k = key(Math.floorDiv(waypoint.x, 16), Math.floorDiv(waypoint.z, 16));
            this.suppressedChunkCache.add(k);
         }
      }

      for (FakePattern pattern : this.fakePatterns) {
         if (pattern == null || pattern.seenChunks == null) continue;
         boolean confirmed = pattern.seenChunks.size() >= 3;
         for (String location : pattern.seenChunks) {
            if (location == null) continue;
            String[] parts = location.split(",");
            if (parts.length != 2) continue;
            try {
               long k = key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
               this.suppressedChunkCache.add(k);
               if (confirmed) this.confirmedFakeChunkCache.add(k);
            } catch (NumberFormatException ignored) {
            }
         }
      }

      this.suppressionCacheDirty = false;
   }

   public Collection<ChunkResult> results() {
      return this.results.values();
   }

   public List<ChunkResult> suspects() {
      return this.top;
   }

   public List<ChunkResult> marks() {
      return this.marks;
   }

   public double lastScanMs() {
      return this.lastScanNanos / 1000000.0;
   }

   public int queueSize() {
      return this.queue.size();
   }

   public int lastScanned() {
      return this.lastScanned;
   }

   public int resultCount() {
      return this.results.size();
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPaused(boolean value) {
      this.paused = value;
   }

   public int candidatesLastRefill() {
      return this.candidatesLastRefill;
   }

   public int scannedSinceRefill() {
      return this.scannedSinceRefill;
   }

   public double progress() {
      if (this.candidatesLastRefill <= 0) {
         return this.queue.isEmpty() ? 1.0 : 0.0;
      } else {
         return Math.max(0.0, Math.min(1.0, (double)this.scannedSinceRefill / this.candidatesLastRefill));
      }
   }

   public void clear() {
      this.saveMarks();
      this.saveFakePatterns();
      this.clearData();
      this.fakePatterns.clear();
      this.lastWorld = null;
      this.ctxKey = null;
   }

   @Override
   public void onWorldLeave() {
      this.clear();
   }

   private void clearData() {
      this.results.clear();
      this.queue.clear();
      this.top.clear();
      this.marks.clear();
      this.dirty = true;
      this.marksDirty = false;
      this.candidatesLastRefill = 0;
      this.scannedSinceRefill = 0;
      this.lastScanned = 0;
      this.lastScanNanos = 0L;
      this.mobClusterMaxByChunk.clear();
      this.suppressedChunkCache.clear();
      this.confirmedFakeChunkCache.clear();
      this.suppressionCacheDirty = true;
   }

   public void clearMarks() {
      this.clearData();
      this.marksDirty = true;
      this.saveMarks();
   }

   @Override
   protected void onDisable() {
      // Keep the confirmed fake-base signatures in memory so StorageESP can
      // continue filtering them even when the scanner itself is toggled off.
      this.saveMarks();
      this.saveFakePatterns();
      this.clearData();
      this.lastWorld = null;
   }

   @Override
   public void onTick() {
      class_638 w = mc.field_1687;
      if (w != null && mc.field_1724 != null) {
         if (w != this.lastWorld) {
            this.saveMarks();
            this.saveFakePatterns();
            this.clearData();
            this.fakePatterns.clear();
            this.lastWorld = w;
            this.ctxKey = Storage.server() + "_" + Storage.dim();
            this.suppressionCacheDirty = true;
            this.loadFakePatterns();
            this.loadMarks();
         }

         this.tick++;
         if (this.detectMobClusters.get() && this.tick % 40L == 0L) {
            this.rebuildMobClusterCounts(w);
         } else if (!this.detectMobClusters.get()) {
            this.mobClusterMaxByChunk.clear();
         }
         if (this.paused) {
            this.lastScanned = 0;
            this.lastScanNanos = 0L;
         } else {
            int pcx = mc.field_1724.method_31477() >> 4;
            int pcz = mc.field_1724.method_31479() >> 4;
            if (this.queue.isEmpty() && this.tick % 20L == 0L) {
               this.refill(w, pcx, pcz);
            }

            long start = System.nanoTime();
            long deadline = start + (long)(this.budgetMs.get() * 1000000.0);

            int n;
            for (n = 0; !this.queue.isEmpty() && System.nanoTime() < deadline; this.scannedSinceRefill++) {
               this.scan(w, this.queue.poll());
               n++;
            }

            this.lastScanNanos = System.nanoTime() - start;
            this.lastScanned = n;
            if (this.dirty) {
               this.rebuildLists();
            }

            if (this.marksDirty && this.tick % 600L == 0L) {
               this.saveMarks();
            }
         }
      }
   }

   private void refill(class_638 w, int pcx, int pcz) {
      int r = this.range.getInt();
      long minAge = this.rescan.getInt() * 20L;
      List<long[]> cand = new ArrayList<>();

      for (int dz = -r; dz <= r; dz++) {
         for (int dx = -r; dx <= r; dx++) {
            int d2 = dx * dx + dz * dz;
            if (d2 <= r * r) {
               long k = key(pcx + dx, pcz + dz);
               ChunkResult res = this.results.get(k);
               if ((res == null || res.lastScanTick == 0L || this.tick - res.lastScanTick >= minAge)
                  && w.method_2935().method_21730(pcx + dx, pcz + dz) != null) {
                  cand.add(new long[]{d2, k});
               }
            }
         }
      }

      cand.sort(Comparator.comparingLong(a -> a[0]));
      this.queue.clear();
      this.candidatesLastRefill = cand.size();
      this.scannedSinceRefill = 0;

      for (long[] c : cand) {
         this.queue.add(c[1]);
      }

      int limit = (r + 4) * (r + 4);
      this.results.values().removeIf(e -> e.status == ChunkResult.Status.CLEAN && (e.cx - pcx) * (e.cx - pcx) + (e.cz - pcz) * (e.cz - pcz) > limit);
   }

   private void scan(class_638 w, long k) {
      int cx = (int)k;
      int cz = (int)(k >> 32);
      class_2818 ch = w.method_2935().method_21730(cx, cz);
      if (this.isFakeChunk(cx, cz)) {
         ChunkResult ignored = this.results.get(k);
         if (ignored != null) {
            ignored.status = ChunkResult.Status.CLEAN;
            ignored.reasons.clear();
         }
         this.dirty = true;
         this.marksDirty = true;
         return;
      }
      if (ch != null) {
         int storage = 0;
         int chest = 0;
         List<String> chestPositions = new ArrayList<>();
         int shulker = 0;
         int hopper = 0;
         int furnace = 0;
         int redstone = 0;
         int spawner = 0;
         int rotatedDeepslate = 0;
         int lightSources = 0;
         int redstoneBlocks = 0;
         int farmBlocks = 0;
         Set<Integer> farmColumns = new HashSet<>();
         int buddingAmethyst = 0;
         int amethystClusters = 0;
         int geodeShell = 0;
         int sculk = 0;
         Map<Integer, Integer> cells = new HashMap<>();

         for (class_2586 be : ch.method_12214().values()) {
            boolean dense = false;
            if (be instanceof class_2595) {
               chest++;
               dense = true;
               chestPositions.add(
                  (be.method_11016().method_10263() & 15) + ","
                     + be.method_11016().method_10264() + ","
                     + (be.method_11016().method_10260() & 15)
               );
            } else if (be instanceof class_3719) {
               storage++;
               dense = true;
            } else if (be instanceof class_2627) {
               shulker++;
               dense = true;
            } else if (be instanceof class_2614) {
               hopper++;
               dense = true;
            } else if (be instanceof class_2609) {
               furnace++;
            } else if (be instanceof class_2601 || be instanceof class_2599 || be instanceof class_8887) {
               redstone++;
            } else if (be instanceof class_2636 && this.detectSpawners.get()) {
               spawner++;
            }

            if (dense) {
               int px = be.method_11016().method_10263() & 15;
               int pz = be.method_11016().method_10260() & 15;
               int py = be.method_11016().method_10264();
               cells.merge(px >> 2 | pz >> 2 << 2 | (py >> 2) + 32 << 4, 1, Integer::sum);
            }
         }

         class_2826[] sections = ch.method_12006();
         byte[] air = new byte[sections.length * 4096];
         for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            class_2826 section = sections[sectionIndex];
            int sectionOffset = sectionIndex * 4096;
            if (section == null || section.method_38292()) {
               Arrays.fill(air, sectionOffset, sectionOffset + 4096, (byte)1);
               continue;
            }

            for (int y = 0; y < 16; y++) {
               for (int z = 0; z < 16; z++) {
                  for (int x = 0; x < 16; x++) {
                     class_2680 state = section.method_12254(x, y, z);
                     String blockName = BLOCK_NAME_CACHE.computeIfAbsent(state.method_26204(), block -> block.toString().toLowerCase(Locale.ROOT));
                     if (blockName.contains("deepslate")) {
                        String stateText = state.toString().toLowerCase(Locale.ROOT);
                        if (stateText.contains("facing=") || stateText.contains("axis=") || stateText.contains("rotation=")) {
                           rotatedDeepslate++;
                        }
                     }
                     if (state.method_26213() >= 8) {
                        lightSources++;
                     }
                     if ((blockName.contains("redstone") && !blockName.contains("redstone_ore"))
                        || blockName.contains("repeater")
                        || blockName.contains("comparator")
                        || blockName.contains("observer")
                        || blockName.contains("piston")
                        || blockName.contains("dispenser")
                        || blockName.contains("dropper")
                        || blockName.contains("lever")) {
                        redstoneBlocks++;
                     }
                     if (blockName.contains("farmland")
                        || blockName.contains("wheat")
                        || blockName.contains("carrots")
                        || blockName.contains("potatoes")
                        || blockName.contains("beetroots")
                        || blockName.contains("melon_stem")
                        || blockName.contains("pumpkin_stem")
                        || blockName.contains("sugar_cane")
                        || blockName.contains("bamboo")
                        || blockName.contains("cactus")
                        || blockName.contains("kelp")
                        || blockName.contains("nether_wart")
                        || blockName.contains("sweet_berry_bush")) {
                        farmBlocks++;
                     }

                     // Record one planted column per X/Z position. Tall crops such
                     // as bamboo and sugar cane must count as one farm plant, not
                     // as many separate plants just because they have several blocks.
                     if (blockName.contains("bamboo")
                        || blockName.contains("sugar_cane")
                        || blockName.contains("cactus")
                        || blockName.contains("kelp")
                        || blockName.contains("wheat")
                        || blockName.contains("carrots")
                        || blockName.contains("potatoes")
                        || blockName.contains("beetroots")
                        || blockName.contains("melon_stem")
                        || blockName.contains("pumpkin_stem")
                        || blockName.contains("nether_wart")
                        || blockName.contains("sweet_berry_bush")) {
                        farmColumns.add((z << 4) | x);
                     }
                     int localIndex = y * 256 + z * 16 + x;
                     if (state.method_26215()) {
                        air[sectionOffset + localIndex] = 1;
                     }

                     if (state.method_27852(class_2246.field_27160)) {
                        buddingAmethyst++;
                     }

                     if (state.method_27852(class_2246.field_27161)
                        || state.method_27852(class_2246.field_27162)
                        || state.method_27852(class_2246.field_27163)
                        || state.method_27852(class_2246.field_27164)) {
                        amethystClusters++;
                     }

                     if (state.method_27852(class_2246.field_27114)
                        || state.method_27852(class_2246.field_29032)
                        || state.method_27852(class_2246.field_27159)) {
                        geodeShell++;
                     }

                     if (state.method_27852(class_2246.field_28108)
                        || state.method_27852(class_2246.field_43231)
                        || state.method_27852(class_2246.field_37571)
                        || state.method_27852(class_2246.field_37570)) {
                        sculk++;
                     }
                  }
               }
            }
         }

         int[] tunnelInfo = this.countTunnelRuns(air, sections.length * 16);
         int tunnelRuns = tunnelInfo[0];
         int tunnelBlocks = tunnelInfo[1];

         int maxCell = 0;
         int maxMobCell = this.mobClusterMaxByChunk.getOrDefault(k, 0);

         for (int v : cells.values()) {
            maxCell = Math.max(maxCell, v);
         }

         double cluster = maxCell >= 8 ? (maxCell - 7) * this.wCluster.get() : 0.0;
         double rotatedDeepslateScore = rotatedDeepslate >= 3 ? Math.min(6.0, (rotatedDeepslate - 2) * this.wRotatedDeepslate.get()) : 0.0;
         double lightScore = lightSources >= 4 ? Math.min(5.0, (lightSources - 3) * this.wLightSources.get() * 0.25) : 0.0;
         double redstonePatternScore = redstoneBlocks >= 5 ? Math.min(6.0, (redstoneBlocks - 4) * this.wRedstonePattern.get() * 0.5) : 0.0;
         double farmScore = farmBlocks >= 12 ? Math.min(5.0, (farmBlocks - 11) * this.wFarm.get() * 0.15) : 0.0;
         int longestFarmLine = this.longestFarmLine(farmColumns);
         double farmLineScore = longestFarmLine >= 6
            ? Math.min(5.0, (longestFarmLine - 5) * this.wFarm.get() * 0.5) : 0.0;
         double mobClusterScore = this.detectMobClusters.get() && maxMobCell >= 5
            ? Math.min(5.0, (maxMobCell - 4) * this.wMobCluster.get()) : 0.0;
         boolean geodeSignature = buddingAmethyst > 0 && amethystClusters > 0 && geodeShell >= 8;
         double amethystScore = amethystClusters >= this.minClusters.getInt()
            ? Math.min(8.0, amethystClusters * this.clusterWeight.get()) * (geodeSignature ? 0.15 : 1.0)
            : 0.0;
         double sculkScore = sculk >= 3 ? (sculk - 2) * this.wSculk.get() : 0.0;
         double naturalSignalScore = amethystScore + sculkScore;
         double tunnelScore = tunnelRuns > 0
            ? Math.min(6.0, tunnelRuns * this.wTunnel.get() + Math.max(0, tunnelBlocks - tunnelRuns * this.minTunnelLength.getInt()) * this.wTunnel.get() * 0.25)
            : 0.0;

         // Environmental hints are noisy (caves, geodes, farms and normal
         // lighting). They add only a small capped bonus and never count as
         // independent proof of a base.
         double softHints = tunnelScore * 0.15 + rotatedDeepslateScore * 0.15
            + lightScore * 0.15 + redstonePatternScore * 0.10 + farmScore * 0.10
            + mobClusterScore * 0.15 + sculkScore * 0.10 + amethystScore * 0.10
            + farmLineScore * 0.35;
         double softHintBonus = Math.min(1.5, softHints);

         int containerCount = storage + shulker + chest;
         boolean storageSignal = containerCount >= 4;
         boolean automationSignal = hopper >= 2 || redstone >= 2 || furnace >= 3
            || hopper >= 1 && (redstone >= 1 || furnace >= 2);
         boolean spawnerSignal = spawner >= 1;
         int signalKinds = (storageSignal ? 1 : 0) + (automationSignal ? 1 : 0) + (spawnerSignal ? 1 : 0);

         // Require substantial storage plus a separate technical signal.
         // This prevents caves, farms, tunnels and lighting alone from
         // generating BaseFinder alerts.
         double chestScore = Math.max(0, chest - 2) * 0.65
            - Math.min(chest, 2) * this.wChestPenalty.get();
         double score = storage * this.wStorage.get()
            + chestScore
            + shulker * this.wStorage.get() * 1.5
            + hopper * this.wHopper.get()
            + furnace * this.wFurnace.get()
            + redstone * this.wRedstone.get()
            + spawner * this.wSpawner.get()
            + cluster
            + softHintBonus;
         ChunkResult old = this.results.get(k);
         ChunkResult res = old != null ? old : new ChunkResult(cx, cz);
         ChunkResult.Status prev = res.status;
         res.score = score;
         res.naturalSignalScore = naturalSignalScore;
         res.lastScanTick = this.tick;
         res.reasons.clear();
         if (storage > 0) {
            res.reasons.add(storage + " Barrel (" + fmt(storage * this.wStorage.get()) + ")");
         }

         if (chest > 0) {
            res.reasons.add(chest + " Chest (Storage-Beitrag " + fmt(chestScore) + ")");
         }

         if (shulker > 0) {
            res.reasons.add(shulker + " Shulker (" + fmt(shulker * this.wStorage.get() * 1.5) + ")");
         }

         if (hopper > 0) {
            res.reasons.add(hopper + " Hopper (" + fmt(hopper * this.wHopper.get()) + ")");
         }

         if (furnace > 0) {
            res.reasons.add(furnace + " Furnace (" + fmt(furnace * this.wFurnace.get()) + ")");
         }

         if (redstone > 0) {
            res.reasons.add(redstone + " Dispenser/Comparator/Crafter (" + fmt(redstone * this.wRedstone.get()) + ")");
         }

         if (spawner > 0) {
            res.reasons.add(spawner + " Mob-Spawner (" + fmt(spawner * this.wSpawner.get()) + ")");
         }

         if (cluster > 0.0) {
            res.reasons.add("Lagerzone (" + maxCell + " in 4x4x4, +" + fmt(cluster) + ")");
         }

         if (amethystClusters > 0) {
            res.reasons
               .add(
                  "Amethyst-Cluster/Buds ("
                     + amethystClusters
                     + ", +"
                     + fmt(amethystScore)
                     + (geodeSignature ? ", natürliche Geoden-Signatur abgewertet" : "")
                     + ")"
               );
         }

         if (buddingAmethyst > 0) {
            res.reasons.add("Budding Amethyst (" + buddingAmethyst + ")");
         }

         if (geodeSignature) {
            res.reasons.add("Geoden-Merkmale erkannt (Calcit/Smooth Basalt/Amethystblock)");
         }

         if (sculkScore > 0.0) {
            res.reasons.add("Sculk-Cluster (" + sculk + ", +" + fmt(sculkScore) + ")");
         }

         if (tunnelScore > 0.0) {
            res.reasons.add("Tunnelspuren (" + tunnelRuns + " Abschnitte, " + tunnelBlocks + " Bloecke, +" + fmt(tunnelScore) + ")");
         }

         if (rotatedDeepslateScore > 0.0) res.reasons.add("Rotated Deepslate (" + rotatedDeepslate + ", +" + fmt(rotatedDeepslateScore) + ")");
         if (lightScore > 0.0) res.reasons.add("Ungewoehnliche Lichtquellen (" + lightSources + ", +" + fmt(lightScore) + ")");
         if (redstonePatternScore > 0.0) res.reasons.add("Redstone-Muster (" + redstoneBlocks + ", +" + fmt(redstonePatternScore) + ")");
         if (farmScore > 0.0) res.reasons.add("Farm-Struktur (" + farmBlocks + ", +" + fmt(farmScore) + ")");
         if (farmLineScore > 0.0) {
            res.reasons.add("Gerade Pflanzen-/Farmreihe erkannt (" + longestFarmLine
               + " Pflanzen-Spalten, z. B. Bambus/Zuckerrohr, +" + fmt(farmLineScore) + ")");
         }
         if (mobClusterScore > 0.0) res.reasons.add("Mob-Cluster (" + maxMobCell + " in 4x4x4, +" + fmt(mobClusterScore) + ")");

         if (softHintBonus > 0.0) {
            res.reasons.add("Umfeld-Hinweise (gedeckelter Zusatzbonus +" + fmt(softHintBonus) + ")");
         }

         // Chest layout is the primary fake-base fingerprint. Auxiliary block
         // signals are required to learn a new fingerprint, but a damaged chunk
         // can still match an already confirmed chest pattern after those blocks
         // have been removed.
         chestPositions.sort(String::compareTo);
         int fakeSignalKinds = (storage > 0 || chest > 0 || shulker > 0 ? 1 : 0)
            + (hopper > 0 ? 1 : 0)
            + (furnace > 0 ? 1 : 0)
            + (redstone > 0 ? 1 : 0)
            + (spawner > 0 ? 1 : 0)
            + (tunnelRuns > 0 ? 1 : 0)
            + (sculk >= 3 && !geodeSignature ? 1 : 0)
            + (lightSources >= 4 ? 1 : 0)
            + (redstoneBlocks >= 5 ? 1 : 0)
            + (farmBlocks >= 12 ? 1 : 0)
            + (rotatedDeepslate >= 3 ? 1 : 0)
            + (amethystScore > 0.0 && !geodeSignature ? 1 : 0);
         boolean mayLearnPattern = chestPositions.size() >= 2
            && signalKinds >= this.minSignals.getInt()
            && fakeSignalKinds >= 2;
         boolean repeatedFakePattern = chestPositions.size() >= 2
            && this.isRepeatedFakePattern(
               chestPositions, mayLearnPattern, storage, chest, shulker, hopper, furnace, redstone, spawner, cx, cz
            );

         // If several different chunks produce the same SUS score and the same
         // storage/technical counts, treat that as a fake-base candidate. Do not
         // trust the score alone: the chest layout must also match a previous
         // observation. This catches copied decoys even when the normal pattern
         // database has not learned them yet.
         boolean repeatedScoreFake = !repeatedFakePattern
            && chestPositions.size() >= 2
            && score >= this.threshold.get()
            && storageSignal
            && signalKinds >= this.minSignals.getInt()
            && this.isRepeatedScoreFakeCandidate(
               score, storage, chest, shulker, hopper, furnace, redstone, spawner,
               chestPositions, cx, cz
            );

         if (repeatedScoreFake) {
            if (prev == ChunkResult.Status.SUS) {
               this.recordFalsePositive(
                  res,
                  "Wiederholtes SUS-Score-/Kistenmuster erkannt",
                  score,
                  res.reasons,
                  "Mehrere verschiedene Chunks hatten denselben SUS-Score und dieselben Storage-/Technikwerte; das Kistenlayout passt ebenfalls zum wiederholten Muster."
               );
            }
            res.status = ChunkResult.Status.CLEAN;
            res.reasons.clear();
            this.dirty = true;
            this.marksDirty = true;
         } else if (score >= this.threshold.get() && storageSignal && signalKinds >= this.minSignals.getInt()) {
            res.status = ChunkResult.Status.SUS;
            if (prev != ChunkResult.Status.SUS) {
               res.foundAtMillis = System.currentTimeMillis();
               this.dirty = true;
               this.marksDirty = true;
               if (this.chat.get() && mc.field_1724 != null) {
                  mc.field_1724
                     .method_7353(
                        class_2561.method_43470(
                           "[Nightfall] SUS Chunk "
                              + cx
                              + ","
                              + cz
                              + " (Block "
                              + res.blockX()
                              + ", "
                              + res.blockZ()
                              + ") Score "
                              + fmt(score)
                              + " - "
                              + String.join(", ", res.reasons)
                        ),
                        false
                     );
               }
            }
         } else if (prev != ChunkResult.Status.SUS && prev != ChunkResult.Status.GONE) {
            res.status = ChunkResult.Status.CLEAN;
            if (score >= this.threshold.get() && (!storageSignal || signalKinds < this.minSignals.getInt())) {
               res.reasons.add("Hinweise vorhanden, aber kein bestaetigtes Storage-/Technik-Muster");
            }
         } else {
            if (prev == ChunkResult.Status.SUS) {
               this.recordFalsePositive(
                  res,
                  this.explainFalsePositive(score, storageSignal, signalKinds, this.minSignals.getInt(), containerCount, chest, shulker, hopper, furnace, redstone, spawner),
                  score,
                  res.reasons,
                  this.buildFalsePositiveDetail(score, storageSignal, signalKinds, this.minSignals.getInt(), containerCount, chest, shulker, hopper, furnace, redstone, spawner)
               );
               this.dirty = true;
               this.marksDirty = true;
            }
            res.status = ChunkResult.Status.GONE;
         }

         if (old == null) {
            this.results.put(k, res);
         }
      }
   }

   private void recordFalsePositive(
      ChunkResult res,
      String reason,
      double score,
      List<String> reasons,
      String detail
   ) {
      res.lastFalsePositiveAnalysis = reason + " — " + detail;
      res.lastFalsePositiveScore = score;
      res.lastFalsePositiveReasons.clear();
      res.lastFalsePositiveReasons.addAll(reasons);
      if (this.chat.get() && mc.field_1724 != null) {
         mc.field_1724.method_7353(
            class_2561.method_43470(
               "[Nightfall] SUS-False-Positive "
                  + res.cx + "," + res.cz
                  + " | " + res.lastFalsePositiveAnalysis
                  + " | Score " + fmt(score)
                  + " | Ausloeser: " + String.join(", ", reasons)
            ),
            false
         );
      }
   }

   private String explainFalsePositive(
      double score,
      boolean storageSignal,
      int signalKinds,
      int minSignals,
      int containerCount,
      int chest,
      int shulker,
      int hopper,
      int furnace,
      int redstone,
      int spawner
   ) {
      if (!storageSignal) {
         return "Zu wenig echtes Storage-Signal";
      }
      if (signalKinds < minSignals) {
         return "Zu wenige unabhaengige Signalarten";
      }
      if (score < this.threshold.get()) {
         return "Gesamtscore unter dem SUS-Schwellenwert";
      }
      if (containerCount > 0 && chest + shulker + hopper + furnace + redstone + spawner == 0) {
         return "Nur ein schwaches Umfeldsignal";
      }
      return "SUS-Kriterium war bei der Erstbewertung erfuellt, wurde beim Folgescan aber nicht bestaetigt";
   }

   private String buildFalsePositiveDetail(
      double score,
      boolean storageSignal,
      int signalKinds,
      int minSignals,
      int containerCount,
      int chest,
      int shulker,
      int hopper,
      int furnace,
      int redstone,
      int spawner
   ) {
      return "Score=" + fmt(score)
         + ", Threshold=" + fmt(this.threshold.get())
         + ", Storage=" + containerCount
         + " (Chest=" + chest
         + ", Shulker=" + shulker
         + ", Hopper=" + hopper
         + ", Furnace=" + furnace
         + ", Redstone=" + redstone
         + ", Spawner=" + spawner + ")"
         + ", Signalarten=" + signalKinds + "/" + minSignals;
   }

   /**
    * Finds the longest straight horizontal row of crop columns in this chunk.
    * A column is counted once regardless of crop height.
    */
   private int longestFarmLine(Set<Integer> columns) {
      int longest = 0;
      for (int z = 0; z < 16; z++) {
         int run = 0;
         for (int x = 0; x < 16; x++) {
            if (columns.contains((z << 4) | x)) {
               longest = Math.max(longest, ++run);
            } else {
               run = 0;
            }
         }
      }
      for (int x = 0; x < 16; x++) {
         int run = 0;
         for (int z = 0; z < 16; z++) {
            if (columns.contains((z << 4) | x)) {
               longest = Math.max(longest, ++run);
            } else {
               run = 0;
            }
         }
      }
      return longest;
   }

   private void rebuildMobClusterCounts(class_638 world) {
      Map<Long, Map<Integer, Integer>> cellsByChunk = new HashMap<>();
      for (class_1297 entity : world.method_18112()) {
         if (entity instanceof class_1309 && !(entity instanceof class_1657)) {
            double x = entity.method_23317();
            double y = entity.method_23318();
            double z = entity.method_23321();
            int cx = ((int)Math.floor(x)) >> 4;
            int cz = ((int)Math.floor(z)) >> 4;
            int localX = ((int)Math.floor(x)) & 15;
            int localZ = ((int)Math.floor(z)) & 15;
            int cellY = ((int)Math.floor(y) >> 2) + 32;
            int cell = localX >> 2 | localZ >> 2 << 2 | cellY << 4;
            cellsByChunk.computeIfAbsent(key(cx, cz), ignored -> new HashMap<>()).merge(cell, 1, Integer::sum);
         }
      }

      this.mobClusterMaxByChunk.clear();
      for (Map.Entry<Long, Map<Integer, Integer>> entry : cellsByChunk.entrySet()) {
         int max = 0;
         for (int count : entry.getValue().values()) {
            max = Math.max(max, count);
         }
         this.mobClusterMaxByChunk.put(entry.getKey(), max);
      }
   }

   private boolean isRepeatedScoreFakeCandidate(
      double score,
      int storage,
      int chest,
      int shulker,
      int hopper,
      int furnace,
      int redstone,
      int spawner,
      List<String> chestPositions,
      int cx,
      int cz
   ) {
      // Scores are compared at one decimal place because tiny floating-point or
      // weighting changes should not split an otherwise identical fingerprint.
      String fingerprint = Math.round(score * 10.0)
         + ":" + storage + ":" + chest + ":" + shulker + ":" + hopper
         + ":" + furnace + ":" + redstone + ":" + spawner;

      List<ScoreFingerprintObservation> observations =
         this.repeatedScoreFingerprints.computeIfAbsent(fingerprint, ignored -> new ArrayList<>());

      long locationKey = key(cx, cz);
      for (ScoreFingerprintObservation observation : observations) {
         if (observation.locationKey == locationKey) return false;
      }

      ScoreFingerprintObservation current =
         new ScoreFingerprintObservation(locationKey, cx, cz, new ArrayList<>(chestPositions));

      // Require at least two earlier occurrences: the current chunk is the
      // third matching score/count observation. This prevents normal repeated
      // bases from being classified as fake after only one coincidence.
      if (observations.size() >= 2) {
         // Compare against earlier chunks with the exact same score/count fingerprint.
         for (ScoreFingerprintObservation previous : observations) {
            if (matchesChestPattern(previous.chestPositions, chestPositions)) {
            for (ScoreFingerprintObservation matched : observations) {
               ChunkResult previousResult = this.results.get(matched.locationKey);
               if (previousResult != null) {
                  previousResult.status = ChunkResult.Status.CLEAN;
                  previousResult.reasons.clear();
               }
            }

            observations.add(current);
            if (observations.size() > 16) {
               observations.subList(0, observations.size() - 16).clear();
            }

            this.dirty = true;
            this.marksDirty = true;
            return true;
         }
         }
      }

      observations.add(current);
      if (observations.size() > 16) {
         observations.subList(0, observations.size() - 16).clear();
      }
      if (this.repeatedScoreFingerprints.size() > 128) {
         String oldestKey = this.repeatedScoreFingerprints.keySet().iterator().next();
         if (!oldestKey.equals(fingerprint)) {
            this.repeatedScoreFingerprints.remove(oldestKey);
         }
      }
      return false;
   }

   private boolean isRepeatedFakePattern(
      List<String> chestPositions, boolean mayLearnPattern,
      int storage, int chest, int shulker, int hopper, int furnace, int redstone, int spawner,
      int cx, int cz
   ) {
      String location = cx + "," + cz;

      for (FakePattern pattern : this.fakePatterns) {
         if (pattern == null || pattern.version < 4 || pattern.chestPositions == null
            || !matchesChestPattern(pattern.chestPositions, chestPositions)) {
            continue;
         }

         // A damaged copy may lose storage blocks, but it must not contain
         // extra block-entity signals that were absent from the saved template.
         if (!notMoreThan(pattern.storage, storage)
            || !notMoreThan(pattern.shulker, shulker)
            || !notMoreThan(pattern.hopper, hopper)
            || !notMoreThan(pattern.furnace, furnace)
            || !notMoreThan(pattern.redstone, redstone)
            || !notMoreThan(pattern.spawner, spawner)) {
            continue;
         }

         if (pattern.seenChunks == null) pattern.seenChunks = new ArrayList<>();
         if (!pattern.seenChunks.contains(location)) {
            pattern.seenChunks.add(location);
            this.suppressionCacheDirty = true;
            this.saveFakePatterns();
         }

         if (pattern.seenChunks.size() >= 3) {
            for (String seen : pattern.seenChunks) {
               String[] parts = seen.split(",");
               if (parts.length != 2) continue;
               try {
                  ChunkResult previous = this.results.get(key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                  if (previous != null) {
                     previous.status = ChunkResult.Status.CLEAN;
                     previous.reasons.clear();
                  }
               } catch (NumberFormatException ignored) {
               }
            }

            this.dirty = true;
            this.marksDirty = true;
            this.saveMarks();
            return true;
         }
         return false;
      }

      if (!mayLearnPattern) return false;

      FakePattern learned = new FakePattern();
      learned.version = 4;
      learned.storage = storage;
      learned.chest = chest;
      learned.shulker = shulker;
      learned.hopper = hopper;
      learned.furnace = furnace;
      learned.redstone = redstone;
      learned.spawner = spawner;
      learned.chestPositions = new ArrayList<>(chestPositions);
      learned.seenChunks.add(location);
      this.fakePatterns.add(learned);

      // Keep the local pattern database bounded on servers with many unrelated
      // storage layouts.
      if (this.fakePatterns.size() > 256) this.fakePatterns.remove(0);
      this.saveFakePatterns();
      return false;
   }

   private static boolean notMoreThan(int known, int current) {
      return known == 0 ? current == 0 : current <= known;
   }

   private static boolean matchesChestPattern(List<String> knownEncoded, List<String> currentEncoded) {
      Set<ChestPoint> known = parseChestPoints(knownEncoded);
      Set<ChestPoint> current = parseChestPoints(currentEncoded);
      if (known.size() < 2 || current.size() < 2 || current.size() > known.size()) return false;

      // At least half of the original chests must remain (minimum two). This
      // permits partially destroyed decoys without matching a random single chest.
      int minimumRemaining = Math.max(2, (int)Math.ceil(known.size() * 0.5));
      if (current.size() < minimumRemaining) return false;

      for (int rotation = 0; rotation < 4; rotation++) {
         List<ChestPoint> rotated = new ArrayList<>(current.size());
         for (ChestPoint point : current) rotated.add(rotate(point, rotation));

         // Try every possible anchor pair. This makes the comparison independent
         // of the fake base's offset inside the chunk and tolerant of rotations.
         for (ChestPoint candidateAnchor : rotated) {
            for (ChestPoint knownAnchor : known) {
               int dx = knownAnchor.x - candidateAnchor.x;
               int dy = knownAnchor.y - candidateAnchor.y;
               int dz = knownAnchor.z - candidateAnchor.z;
               boolean subset = true;

               for (ChestPoint point : rotated) {
                  if (!known.contains(new ChestPoint(point.x + dx, point.y + dy, point.z + dz))) {
                     subset = false;
                     break;
                  }
               }
               if (subset) return true;
            }
         }
      }
      return false;
   }

   private static Set<ChestPoint> parseChestPoints(List<String> encoded) {
      Set<ChestPoint> points = new HashSet<>();
      if (encoded == null) return points;
      for (String value : encoded) {
         if (value == null) continue;
         String[] parts = value.split(",");
         if (parts.length != 3) continue;
         try {
            points.add(new ChestPoint(
               Integer.parseInt(parts[0]),
               Integer.parseInt(parts[1]),
               Integer.parseInt(parts[2])
            ));
         } catch (NumberFormatException ignored) {
         }
      }
      return points;
   }

   private static ChestPoint rotate(ChestPoint point, int rotation) {
      return switch (rotation) {
         case 1 -> new ChestPoint(point.z, point.y, -point.x);
         case 2 -> new ChestPoint(-point.x, point.y, -point.z);
         case 3 -> new ChestPoint(-point.z, point.y, point.x);
         default -> point;
      };
   }

   private Path fakePatternsFile() {
      return Storage.dir().resolve("fake_patterns_" + this.ctxKey + ".json");
   }

   private void saveFakePatterns() {
      if (this.ctxKey != null) {
         try {
            writeAtomically(this.fakePatternsFile(), GSON.toJson(this.fakePatterns));
         } catch (IOException exception) {
            Nightfall.LOGGER.error("Fake-Base-Fingerprints konnten nicht gespeichert werden", exception);
         }
      }
   }

   private void loadFakePatterns() {
      this.fakePatterns.clear();
      Path file = this.fakePatternsFile();
      if (!Files.exists(file)) return;

      try {
         List<FakePattern> loaded = (List<FakePattern>)GSON.fromJson(Files.readString(file), FAKE_LIST);
         if (loaded == null) return;

         int skipped = 0;
         for (FakePattern pattern : loaded) {
            if (pattern == null || pattern.seenChunks == null) {
               skipped++;
               continue;
            }
            if (pattern.chestPositions == null) pattern.chestPositions = new ArrayList<>();
            this.fakePatterns.add(pattern);
         }
         if (skipped > 0) {
            Nightfall.LOGGER.warn("{} ungueltige Fake-Pattern-Eintraege uebersprungen: {}", skipped, file);
         }
      } catch (Exception exception) {
         Nightfall.LOGGER.error("Fake-Base-Fingerprints defekt: {}", file, exception);
      }
   }

   private int[] countTunnelRuns(byte[] air, int height) {
      int minLength = this.minTunnelLength.getInt();
      int runs = 0;
      int blocks = 0;

      // Look for straight, two-block-high passages with a floor, ceiling and
      // two side walls. Only data from this already-loaded chunk is inspected.
      for (int y = 1; y + 2 < height; y++) {
         for (int z = 1; z < 15; z++) {
            int length = 0;
            for (int x = 0; x < 16; x++) {
               if (this.isTunnelCell(air, height, x, y, z, true)) {
                  length++;
               } else {
                  if (length >= minLength) {
                     runs++;
                     blocks += length;
                  }
                  length = 0;
               }
            }
            if (length >= minLength) {
               runs++;
               blocks += length;
            }
         }

         for (int x = 1; x < 15; x++) {
            int length = 0;
            for (int z = 0; z < 16; z++) {
               if (this.isTunnelCell(air, height, x, y, z, false)) {
                  length++;
               } else {
                  if (length >= minLength) {
                     runs++;
                     blocks += length;
                  }
                  length = 0;
               }
            }
            if (length >= minLength) {
               runs++;
               blocks += length;
            }
         }
      }

      return new int[]{runs, blocks};
   }

   private static boolean isTunnelCell(byte[] air, int height, int x, int y, int z, boolean alongX) {
      if (y < 1 || y + 2 >= height) {
         return false;
      }

      int index = (y * 16 + z) * 16 + x;
      int sideA = alongX ? index - 16 : index - 1;
      int sideB = alongX ? index + 16 : index + 1;

      return air[index] != 0
         && air[index + 256] != 0
         && air[index - 256] == 0
         && air[index + 512] == 0
         && air[sideA] == 0
         && air[sideB] == 0;
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private void rebuildLists() {
      this.top.clear();
      this.marks.clear();

      for (ChunkResult r : this.results.values()) {
         // Fake bases stay detected internally, but never enter the visible lists.
         if (this.isFakeChunk(r.cx, r.cz)) continue;
         if (r.status == ChunkResult.Status.SUS) {
            this.top.add(r);
            this.marks.add(r);
         } else if (r.status == ChunkResult.Status.GONE) {
            this.marks.add(r);
         }
      }

      this.top.sort((a, b) -> Double.compare(b.score, a.score));
      this.dirty = false;
   }

   private Path marksFile() {
      return Storage.dir().resolve("marks_" + this.ctxKey + ".json");
   }

   public void saveMarks() {
      if (this.ctxKey != null) {
         try {
            List<BaseFinder.Mark> out = new ArrayList<>();

            for (ChunkResult r : this.results.values()) {
               if (!this.isFakeChunk(r.cx, r.cz) && r.status != ChunkResult.Status.CLEAN) {
                  BaseFinder.Mark m = new BaseFinder.Mark();
                  m.cx = r.cx;
                  m.cz = r.cz;
                  m.score = r.score;
                  m.status = r.status.name();
                  m.found = r.foundAtMillis;
                  m.reasons = new ArrayList<>(r.reasons);
                  out.add(m);
               }
            }

            Path f = this.marksFile();
            if (out.isEmpty() && !Files.exists(f)) {
               return;
            }

            writeAtomically(f, GSON.toJson(out));
            this.marksDirty = false;
         } catch (IOException var5) {
            Nightfall.LOGGER.error("Markierungen konnten nicht gespeichert werden", var5);
         }
      }
   }

   private void loadMarks() {
      Path f = this.marksFile();
      if (Files.exists(f)) {
         try {
            List<BaseFinder.Mark> in = (List<BaseFinder.Mark>)GSON.fromJson(Files.readString(f), MARK_LIST);
            if (in == null) {
               return;
            }

            int skipped = 0;
            for (BaseFinder.Mark m : in) {
               if (m == null || m.status == null || !Double.isFinite(m.score)) {
                  skipped++;
                  continue;
               }

               ChunkResult.Status status;
               try {
                  status = ChunkResult.Status.valueOf(m.status);
               } catch (IllegalArgumentException invalidStatus) {
                  skipped++;
                  continue;
               }

               if (status == ChunkResult.Status.CLEAN) {
                  skipped++;
                  continue;
               }

               ChunkResult r = new ChunkResult(m.cx, m.cz);
               r.score = m.score;
               r.status = status;
               r.foundAtMillis = Math.max(0L, m.found);
               if (m.reasons != null) {
                  for (String reason : m.reasons) {
                     if (reason != null && !reason.isBlank()) {
                        r.reasons.add(reason);
                     }
                  }
               }

               r.lastScanTick = 0L;
               this.results.put(key(m.cx, m.cz), r);
            }

            if (skipped > 0) {
               Nightfall.LOGGER.warn("{} ungueltige Eintraege in Markierungsdatei uebersprungen: {}", skipped, f);
            }
            this.dirty = true;
         } catch (Exception var6) {
            Nightfall.LOGGER.error("Markierungsdatei defekt: {}", f, var6);
         }
      }
   }

   private static void writeAtomically(Path target, String content) throws IOException {
      Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
      Files.writeString(temporary, content);
      try {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException unsupported) {
         Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   private record ScoreFingerprintObservation(
      long locationKey,
      int cx,
      int cz,
      List<String> chestPositions
   ) {
   }

   private static final class FakePattern {
      int version;
      int storage;
      int chest;
      int shulker;
      int hopper;
      int furnace;
      int redstone;
      int spawner;
      List<String> chestPositions = new ArrayList<>();
      List<String> seenChunks = new ArrayList<>();
   }

   private record ChestPoint(int x, int y, int z) {
   }

   private static final class Mark {
      int cx;
      int cz;
      double score;
      String status;
      long found;
      List<String> reasons;
   }
}

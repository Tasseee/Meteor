package dev.nightfall.modules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Storage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_2246;
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
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_3719;
import net.minecraft.class_638;
import net.minecraft.class_8887;

public class BaseFinder extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Range (Chunks)", 32.0, 2.0, 48.0, 1.0));
   private final NumberSetting budgetMs = this.add(new NumberSetting("Budget ms/Tick", 2.5, 0.2, 5.0, 0.1));
   private final NumberSetting rescan = this.add(new NumberSetting("Rescan (s)", 8.0, 2.0, 120.0, 1.0));
   private final NumberSetting threshold = this.add(new NumberSetting("Min Score", 6.0, 3.0, 100.0, 1.0));
   private final NumberSetting minSignals = this.add(new NumberSetting("Min. Signaltypen", 1.0, 1.0, 10.0, 1.0));
   private final NumberSetting wStorage = this.add(new NumberSetting("Gewicht Storage", 1.0, 0.1, 5.0, 0.1));
   private final NumberSetting wHopper = this.add(new NumberSetting("Gewicht Hopper", 1.5, 0.1, 5.0, 0.1));
   private final NumberSetting wFurnace = this.add(new NumberSetting("Gewicht Furnace", 0.7, 0.1, 5.0, 0.1));
   private final NumberSetting wRedstone = this.add(new NumberSetting("Gewicht Redstone", 1.2, 0.1, 5.0, 0.1));
   private final NumberSetting wSpawner = this.add(new NumberSetting("Gewicht Spawner", 2.0, 0.1, 10.0, 0.1));
   private final NumberSetting wCluster = this.add(new NumberSetting("Gewicht Lagerzone", 1.5, 0.0, 5.0, 0.1));
   private final NumberSetting clusterWeight = this.add(new NumberSetting("Gewicht Amethyst-Cluster", 0.25, 0.0, 2.0, 0.05));
   private final NumberSetting minClusters = this.add(new NumberSetting("Min. Amethyst-Cluster", 6.0, 1.0, 64.0, 1.0));
   private final NumberSetting wSculk = this.add(new NumberSetting("Gewicht Sculk", 1.2, 0.0, 8.0, 0.1));
   private final BoolSetting chat = this.add(new BoolSetting("Chat-Meldung", true));
   private static final Gson GSON = new Gson();
   private static final Type MARK_LIST = (new TypeToken<List<BaseFinder.Mark>>() {}).getType();
   private static final Type FAKE_LIST = (new TypeToken<List<BaseFinder.FakePattern>>() {}).getType();
   private final Map<Long, ChunkResult> results = new HashMap<>();
   private final ArrayDeque<Long> queue = new ArrayDeque<>();
   private final List<ChunkResult> top = new ArrayList<>();
   private final List<ChunkResult> marks = new ArrayList<>();
   private final List<BaseFinder.FakePattern> fakePatterns = new ArrayList<>();
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

   public BaseFinder() {
      super("BaseFinder", "Findet verdaechtige Chunks (Storage, Hopper, Redstone) in geladenen Chunks.", Category.BASEFINDER);
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
      String location = cx + "," + cz;

      for (BaseFinder.FakePattern pattern : this.fakePatterns) {
         if (pattern.seenChunks != null && pattern.seenChunks.size() >= 3 && pattern.seenChunks.contains(location)) {
            return true;
         }
      }

      return false;
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
   }

   public void clearMarks() {
      this.clearData();
      this.marksDirty = true;
      this.saveMarks();
   }

   @Override
   protected void onDisable() {
      this.clear();
   }

   @Override
   public void onTick() {
      class_638 w = mc.field_1687;
      if (w != null && mc.field_1724 != null) {
         if (w != this.lastWorld) {
            this.saveMarks();
            this.clearData();
            this.lastWorld = w;
            this.ctxKey = Storage.server() + "_" + Storage.dim();
            this.fakePatterns.clear();
            this.loadMarks();
            this.loadFakePatterns();
         }

         this.tick++;
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
      if (ch != null) {
         int storage = 0;
         int shulker = 0;
         int hopper = 0;
         int furnace = 0;
         int redstone = 0;
         int spawner = 0;
         int buddingAmethyst = 0;
         int amethystClusters = 0;
         int geodeShell = 0;
         int sculk = 0;
         Map<Integer, Integer> cells = new HashMap<>();

         for (class_2586 be : ch.method_12214().values()) {
            boolean dense = false;
            if (be instanceof class_2595 || be instanceof class_3719) {
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
            } else if (be instanceof class_2636) {
               spawner++;
            }

            if (dense) {
               int px = be.method_11016().method_10263() & 15;
               int pz = be.method_11016().method_10260() & 15;
               int py = be.method_11016().method_10264();
               cells.merge(px >> 2 | pz >> 2 << 2 | (py >> 2) + 32 << 4, 1, Integer::sum);
            }
         }

         for (class_2826 section : ch.method_12006()) {
            if (section != null && !section.method_38292()) {
               for (int y = 0; y < 16; y++) {
                  for (int z = 0; z < 16; z++) {
                     for (int x = 0; x < 16; x++) {
                        class_2680 state = section.method_12254(x, y, z);
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
         }

         int maxCell = 0;

         for (int v : cells.values()) {
            maxCell = Math.max(maxCell, v);
         }

         double cluster = maxCell >= 6 ? (maxCell - 5) * this.wCluster.get() : 0.0;
         boolean geodeSignature = buddingAmethyst > 0 && amethystClusters > 0 && geodeShell >= 8;
         double amethystScore = amethystClusters >= this.minClusters.getInt()
            ? Math.min(8.0, amethystClusters * this.clusterWeight.get()) * (geodeSignature ? 0.15 : 1.0)
            : 0.0;
         double sculkScore = sculk >= 3 ? (sculk - 2) * this.wSculk.get() : 0.0;
         double naturalSignalScore = amethystScore + sculkScore;
         double score = storage * this.wStorage.get()
            + shulker * this.wStorage.get() * 1.5
            + hopper * this.wHopper.get()
            + furnace * this.wFurnace.get()
            + redstone * this.wRedstone.get()
            + spawner * this.wSpawner.get()
            + cluster
            + naturalSignalScore;
         int signalKinds = (storage + shulker > 0 ? 1 : 0)
            + (hopper > 0 ? 1 : 0)
            + (furnace > 0 ? 1 : 0)
            + (redstone > 0 ? 1 : 0)
            + (spawner > 0 ? 1 : 0)
            + (cluster > 0.0 ? 1 : 0)
            + (amethystScore > 0.0 ? 1 : 0)
            + (sculkScore > 0.0 ? 1 : 0);
         ChunkResult old = this.results.get(k);
         ChunkResult res = old != null ? old : new ChunkResult(cx, cz);
         ChunkResult.Status prev = res.status;
         res.score = score;
         res.naturalSignalScore = naturalSignalScore;
         res.lastScanTick = this.tick;
         res.reasons.clear();
         if (storage > 0) {
            res.reasons.add(storage + " Chest/Barrel (" + fmt(storage * this.wStorage.get()) + ")");
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

         if (score >= this.threshold.get() && signalKinds >= this.minSignals.getInt()) {
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
            if (score >= this.threshold.get() && signalKinds < this.minSignals.getInt()) {
               res.reasons.add("Score hoch, aber nur " + signalKinds + " Signaltyp(en) — nicht als SUS markiert");
            }
         } else {
            res.status = ChunkResult.Status.GONE;
            if (prev == ChunkResult.Status.SUS) {
               this.dirty = true;
               this.marksDirty = true;
            }
         }

         if (old == null) {
            this.results.put(k, res);
         }
      }
   }

   private boolean isRepeatedFakePattern(int storage, int shulker, int hopper, int furnace, int redstone, int spawner, int cx, int cz) {
      for (BaseFinder.FakePattern p : this.fakePatterns) {
         if (similar(p.storage, storage)
            && similar(p.shulker, shulker)
            && similar(p.hopper, hopper)
            && similar(p.furnace, furnace)
            && similar(p.redstone, redstone)
            && similar(p.spawner, spawner)) {
            String location = cx + "," + cz;
            if (!p.seenChunks.contains(location)) {
               p.seenChunks.add(location);
               this.saveFakePatterns();
            }

            if (p.seenChunks.size() >= 3) {
               for (String seen : p.seenChunks) {
                  String[] parts = seen.split(",");
                  if (parts.length == 2) {
                     try {
                        ChunkResult previous = this.results.get(key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                        if (previous != null) {
                           previous.status = ChunkResult.Status.CLEAN;
                           previous.reasons.clear();
                        }
                     } catch (NumberFormatException var16) {
                     }
                  }
               }

               this.dirty = true;
               this.marksDirty = true;
               this.saveMarks();
               return true;
            }

            return false;
         }
      }

      BaseFinder.FakePattern px = new BaseFinder.FakePattern();
      px.storage = storage;
      px.shulker = shulker;
      px.hopper = hopper;
      px.furnace = furnace;
      px.redstone = redstone;
      px.spawner = spawner;
      px.seenChunks.add(cx + "," + cz);
      this.fakePatterns.add(px);
      this.saveFakePatterns();
      return false;
   }

   private static boolean similar(int a, int b) {
      return Math.abs(a - b) <= Math.max(2, Math.round(Math.max(a, b) * 0.25F));
   }

   private Path fakePatternsFile() {
      return Storage.dir().resolve("fake_patterns_" + this.ctxKey + ".json");
   }

   private void saveFakePatterns() {
      if (this.ctxKey != null) {
         try {
            Files.writeString(this.fakePatternsFile(), GSON.toJson(this.fakePatterns));
         } catch (IOException var2) {
            Nightfall.LOGGER.error("Fake-Base-Fingerprints konnten nicht gespeichert werden", var2);
         }
      }
   }

   private void loadFakePatterns() {
      this.fakePatterns.clear();
      Path f = this.fakePatternsFile();
      if (Files.exists(f)) {
         try {
            List<BaseFinder.FakePattern> loaded = (List<BaseFinder.FakePattern>)GSON.fromJson(Files.readString(f), FAKE_LIST);
            if (loaded != null) {
               for (BaseFinder.FakePattern pattern : loaded) {
                  if (pattern != null) {
                     if (pattern.seenChunks == null) {
                        pattern.seenChunks = new ArrayList<>();
                     }

                     this.fakePatterns.add(pattern);
                  }
               }
            }
         } catch (Exception var5) {
            Nightfall.LOGGER.error("Fake-Base-Fingerprints defekt: {}", f, var5);
         }
      }
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private void rebuildLists() {
      this.top.clear();
      this.marks.clear();

      for (ChunkResult r : this.results.values()) {
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
               if (r.status != ChunkResult.Status.CLEAN) {
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

            Files.writeString(f, GSON.toJson(out));
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

            for (BaseFinder.Mark m : in) {
               ChunkResult r = new ChunkResult(m.cx, m.cz);
               r.score = m.score;
               r.status = ChunkResult.Status.valueOf(m.status);
               r.foundAtMillis = m.found;
               if (m.reasons != null) {
                  r.reasons.addAll(m.reasons);
               }

               r.lastScanTick = 0L;
               this.results.put(key(m.cx, m.cz), r);
            }

            this.dirty = true;
         } catch (Exception var6) {
            Nightfall.LOGGER.error("Markierungsdatei defekt: {}", f, var6);
         }
      }
   }

   private static final class FakePattern {
      int storage;
      int shulker;
      int hopper;
      int furnace;
      int redstone;
      int spawner;
      List<String> seenChunks = new ArrayList<>();
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

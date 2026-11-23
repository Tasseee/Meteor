package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Storage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_332;
import net.minecraft.class_638;

/**
 * Shared session cache for client chunks that are already present locally.
 *
 * All chunk-manager reads are lookup-only. This module never asks the server
 * for a chunk, force-loads a chunk, changes the player, or replaces the render
 * camera. Other modules can use getAvailableChunk/getBlockState as a common API.
 */
public final class ChunkMemory extends Module {
   private static final Map<Long, CachedChunk> CACHE = new LinkedHashMap<>(256, 0.75F, true);
   private static final Map<Long, Long> NEXT_LOOKUP_TICK = new LinkedHashMap<>(256, 0.75F, true);
   private static final int MAX_LOOKUP_SCHEDULES = 8192;

   private static class_638 cacheWorld;
   private static String cacheContext;
   private static long sessionTick;
   private static long maxCacheAgeTicks = 24_000L;

   private final NumberSetting range = this.add(
      new NumberSetting("Scan-Radius (Bloecke)", 50.0, 30.0, 200.0, 1.0)
   );
   private final NumberSetting scanSpeed = this.add(
      new NumberSetting("Scan-Budget (Chunks/Tick)", 8.0, 1.0, 64.0, 1.0)
   );
   private final NumberSetting maxChunks = this.add(
      new NumberSetting("Max. gespeicherte Chunks", 1024.0, 64.0, 4096.0, 64.0)
   );
   private final NumberSetting maxAgeMinutes = this.add(
      new NumberSetting("Cache-Lebensdauer (Minuten)", 20.0, 1.0, 120.0, 1.0)
   );
   private final NumberSetting lookupRetrySeconds = this.add(
      new NumberSetting("Wiederholungsintervall (Sekunden)", 2.0, 1.0, 15.0, 1.0)
   );
   private final BoolSetting useBackgroundFreecam = this.add(
      new BoolSetting("BackgroundFreecam als Zentrum", true)
   );
   private final BoolSetting useFreecamPosition = this.add(
      new BoolSetting("Manuelle Freecam als Ersatz", true)
   );
   private final BoolSetting pauseScan = this.add(
      new BoolSetting("Chunk-Scan pausieren", false)
   );

   private int centerChunkX = Integer.MIN_VALUE;
   private int centerChunkZ = Integer.MIN_VALUE;
   private int pathRange = -1;
   private List<Offset> scanPath = List.of();
   private int scanCursor;
   private int lastLookups;
   private int lastLoaded;
   private int lastNewEntries;
   private int lastCachedFallbacks;
   private int lastRetrySkips;
   private int lastPruned;
   private String centerSource = "Spieler";
   private boolean startedBackgroundFreecam;

   public ChunkMemory() {
      super(
         "ChunkMemory",
         "Session-Cache fuer bereits empfangene Chunks mit gemeinsamer API und BackgroundFreecam-Scan.",
         Category.WORLD
      );
   }

   @Override
   protected void onEnable() {
      this.startedBackgroundFreecam = false;
      this.syncBackgroundFreecam();
   }

   @Override
   protected void onDisable() {
      if (this.startedBackgroundFreecam) {
         try {
            BackgroundFreecam camera = ModuleManager.get(BackgroundFreecam.class);
            if (camera.isEnabled()) camera.setEnabled(false);
         } catch (IllegalStateException ignored) {
         }
      }
      this.startedBackgroundFreecam = false;
   }

   /**
    * The helper is owned only when ChunkMemory itself switched it on. A
    * BackgroundFreecam enabled manually by the user is never disabled here.
    */
   private void syncBackgroundFreecam() {
      BackgroundFreecam camera;
      try {
         camera = ModuleManager.get(BackgroundFreecam.class);
      } catch (IllegalStateException ignored) {
         return;
      }

      boolean shouldRun = this.useBackgroundFreecam.get()
         && mc.field_1724 != null && mc.field_1687 != null;
      if (shouldRun && !camera.isEnabled()) {
         camera.setEnabled(true);
         this.startedBackgroundFreecam = true;
      } else if (!shouldRun && this.startedBackgroundFreecam) {
         if (camera.isEnabled()) camera.setEnabled(false);
         this.startedBackgroundFreecam = false;
      }
   }

   private class_243 resolveScanPosition() {
      if (mc.field_1724 == null || mc.field_1687 == null) return null;

      if (this.useBackgroundFreecam.get() && BackgroundFreecam.isActive()) {
         class_243 backgroundPosition = BackgroundFreecam.cameraPosition();
         if (backgroundPosition != null) {
            this.centerSource = BackgroundFreecam.isPaused()
               ? "BackgroundFreecam (pausiert)" : "BackgroundFreecam";
            return backgroundPosition;
         }
      }

      if (this.useFreecamPosition.get() && FreecamController.isActive()) {
         this.centerSource = "Manuelle Freecam";
         return FreecamController.cameraPosition();
      }

      this.centerSource = "Spieler";
      return mc.field_1724.method_33571();
   }

   /** Shared scan origin for BlockESP, OreESP, BaseFinder and other modules. */
   public static class_243 scanPosition() {
      try {
         ChunkMemory module = ModuleManager.get(ChunkMemory.class);
         if (module.isEnabled()) {
            class_243 position = module.resolveScanPosition();
            if (position != null) return position;
         }
      } catch (IllegalStateException ignored) {
      }

      if (BackgroundFreecam.isActive()) {
         class_243 position = BackgroundFreecam.cameraPosition();
         if (position != null) return position;
      }
      if (FreecamController.isActive()) {
         return FreecamController.cameraPosition();
      }
      return mc.field_1724 == null ? null : mc.field_1724.method_33571();
   }

   public static String scanSource() {
      try {
         ChunkMemory module = ModuleManager.get(ChunkMemory.class);
         if (module.isEnabled()) {
            module.resolveScanPosition();
            return module.centerSource;
         }
      } catch (IllegalStateException ignored) {
      }
      if (BackgroundFreecam.isActive()) {
         return BackgroundFreecam.isPaused() ? "BackgroundFreecam (pausiert)" : "BackgroundFreecam";
      }
      return FreecamController.isActive() ? "Manuelle Freecam" : "Spieler";
   }

   private static long key(int cx, int cz) {
      return (cx & 0xFFFFFFFFL) | ((long)cz << 32);
   }

   private static void rememberLiveChunk(int cx, int cz, class_2818 chunk) {
      if (chunk == null || cacheWorld == null || mc.field_1687 != cacheWorld) return;

      long chunkKey = key(cx, cz);
      CachedChunk previous = CACHE.get(chunkKey);
      if (previous == null) {
         CACHE.put(chunkKey, new CachedChunk(cx, cz, chunk, sessionTick, sessionTick));
      } else if (previous.chunk() != chunk) {
         // A new client chunk instance replaced the old snapshot.
         CACHE.put(chunkKey, new CachedChunk(cx, cz, chunk, sessionTick, sessionTick));
      } else {
         CACHE.put(chunkKey, new CachedChunk(cx, cz, chunk, previous.capturedAtTick(), sessionTick));
      }
   }

   /** Returns a cached chunk only while it belongs to the current client world. */
   public static class_2818 getCachedChunk(int cx, int cz) {
      if (cacheWorld == null || mc.field_1687 != cacheWorld) return null;

      long chunkKey = key(cx, cz);
      CachedChunk cached = CACHE.get(chunkKey);
      if (cached == null) return null;

      if (sessionTick - cached.lastAccessTick() > maxCacheAgeTicks) {
         CACHE.remove(chunkKey);
         return null;
      }

      CACHE.put(chunkKey, new CachedChunk(
         cached.cx(), cached.cz(), cached.chunk(), cached.capturedAtTick(), sessionTick
      ));
      return cached.chunk();
   }

   /** Snapshot of current cache entries. Chunk instances remain client-owned. */
   public static Collection<CachedChunk> cachedChunks() {
      if (cacheWorld == null || mc.field_1687 != cacheWorld) return List.of();
      return Collections.unmodifiableList(new ArrayList<>(CACHE.values()));
   }

   public static int cachedChunkCount() {
      return cacheWorld != null && mc.field_1687 == cacheWorld ? CACHE.size() : 0;
   }

   public static long cachedChunkAgeTicks(int cx, int cz) {
      CachedChunk cached = CACHE.get(key(cx, cz));
      return cached == null ? -1L : Math.max(0L, sessionTick - cached.capturedAtTick());
   }

   /**
    * Returns the live client chunk first and falls back to the session cache.
    * No chunk request or forced load is performed by this method.
    */
   public static class_2818 getAvailableChunk(int cx, int cz) {
      class_638 world = mc.field_1687;
      if (world == null) return null;

      class_2818 loaded = world.method_2935().method_21730(cx, cz);
      if (loaded != null) {
         rememberLiveChunk(cx, cz, loaded);
         return loaded;
      }

      return getCachedChunk(cx, cz);
   }

   /** Reads one block through the shared live-or-cached chunk API. */
   public static class_2680 getBlockState(int x, int y, int z) {
      return getBlockState(getAvailableChunk(x >> 4, z >> 4), x, y, z);
   }

   /**
    * Direct section read. Callers scanning many blocks in the same chunk
    * should resolve the chunk once and reuse this overload.
    */
   public static class_2680 getBlockState(class_2818 chunk, int x, int y, int z) {
      class_638 world = mc.field_1687;
      if (chunk == null || world == null || y < world.method_31607() || y >= world.method_31600()) {
         return null;
      }

      class_2826[] sections = chunk.method_12006();
      int sectionIndex = (y >> 4) - (world.method_31607() >> 4);
      if (sectionIndex < 0 || sectionIndex >= sections.length) return null;

      class_2826 section = sections[sectionIndex];
      return section == null ? null : section.method_12254(x & 15, y & 15, z & 15);
   }

   public static void clearSessionCache() {
      CACHE.clear();
      NEXT_LOOKUP_TICK.clear();
      cacheWorld = null;
      cacheContext = null;
      sessionTick = 0L;
   }

   @Override
   public void onTick() {
      class_638 world = mc.field_1687;
      if (world == null || mc.field_1724 == null) return;

      this.syncBackgroundFreecam();

      String context = Storage.server() + "_" + Storage.dim();
      if (world != cacheWorld || !context.equals(cacheContext)) {
         clearSessionCache();
         cacheWorld = world;
         cacheContext = context;
         this.resetScanPath();
      }

      maxCacheAgeTicks = (long)this.maxAgeMinutes.getInt() * 1200L;
      class_243 center = this.resolveScanPosition();
      if (center == null) return;

      this.centerChunkX = ((int)Math.floor(center.field_1352)) >> 4;
      this.centerChunkZ = ((int)Math.floor(center.field_1350)) >> 4;
      int configuredRange = this.range.getInt();
      if (configuredRange != this.pathRange) {
         this.pathRange = configuredRange;
         this.rebuildScanPath(configuredRange);
      }

      this.lastLookups = 0;
      this.lastLoaded = 0;
      this.lastNewEntries = 0;
      this.lastCachedFallbacks = 0;
      this.lastRetrySkips = 0;

      if (!this.pauseScan.get()) {
         this.scanAvailableChunks(world, center, configuredRange);
      }

      sessionTick++;
      this.trimCache(this.maxChunks.getInt());
   }

   private void scanAvailableChunks(class_638 world, class_243 center, int radiusBlocks) {
      int radiusSquared = radiusBlocks * radiusBlocks;
      int processed = 0;
      int budget = this.scanSpeed.getInt();
      long retryTicks = (long)this.lookupRetrySeconds.getInt() * 20L;
      var chunkManager = world.method_2935();

      while (processed < budget && !this.scanPath.isEmpty()) {
         if (this.scanCursor >= this.scanPath.size()) this.scanCursor = 0;

         Offset offset = this.scanPath.get(this.scanCursor++);
         processed++;

         int targetX = this.centerChunkX + offset.dx();
         int targetZ = this.centerChunkZ + offset.dz();
         if (!intersectsRadius(targetX, targetZ, center.field_1352, center.field_1350, radiusSquared)) {
            continue;
         }

         long chunkKey = key(targetX, targetZ);
         Long nextLookup = NEXT_LOOKUP_TICK.get(chunkKey);
         if (nextLookup != null && sessionTick < nextLookup) {
            this.lastRetrySkips++;
            continue;
         }
         if (nextLookup != null) NEXT_LOOKUP_TICK.remove(chunkKey);

         this.lastLookups++;
         class_2818 loaded = chunkManager.method_21730(targetX, targetZ);
         if (loaded != null) {
            this.lastLoaded++;
            CachedChunk previous = CACHE.get(chunkKey);
            if (previous == null || previous.chunk() != loaded) {
               if (previous == null) this.lastNewEntries++;
               rememberLiveChunk(targetX, targetZ, loaded);
            } else {
               rememberLiveChunk(targetX, targetZ, loaded);
            }
            NEXT_LOOKUP_TICK.put(chunkKey, sessionTick + retryTicks);
         } else {
            CachedChunk previous = CACHE.get(chunkKey);
            if (previous != null && sessionTick - previous.lastAccessTick() <= maxCacheAgeTicks) {
               this.lastCachedFallbacks++;
               CACHE.put(chunkKey, new CachedChunk(
                  previous.cx(), previous.cz(), previous.chunk(),
                  previous.capturedAtTick(), sessionTick
               ));
            }
            // A missing chunk is not requested. Retry it later because it may
            // become available through normal client/server gameplay meanwhile.
            NEXT_LOOKUP_TICK.put(chunkKey, sessionTick + retryTicks);
         }

         trimLookupSchedule();
      }
   }

   private void rebuildScanPath(int radiusBlocks) {
      List<Offset> offsets = new ArrayList<>();
      int candidateRadius = radiusBlocks + 16;
      int radiusChunks = (candidateRadius + 15) / 16 + 1;
      int radiusSquared = candidateRadius * candidateRadius;

      for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
         for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            int chunkMinX = dx * 16;
            int chunkMinZ = dz * 16;
            int nearestX = chunkMinX > 0 ? chunkMinX : chunkMinX + 16 < 0 ? -(chunkMinX + 16) : 0;
            int nearestZ = chunkMinZ > 0 ? chunkMinZ : chunkMinZ + 16 < 0 ? -(chunkMinZ + 16) : 0;
            int distanceToFootprint = nearestX * nearestX + nearestZ * nearestZ;
            if (distanceToFootprint <= radiusSquared) {
               int chunkCenterX = chunkMinX + 8;
               int chunkCenterZ = chunkMinZ + 8;
               int centerDistance = chunkCenterX * chunkCenterX + chunkCenterZ * chunkCenterZ;
               offsets.add(new Offset(dx, dz, centerDistance));
            }
         }
      }

      offsets.sort(Comparator.comparingInt(Offset::distanceSquared)
         .thenComparingInt(Offset::dz)
         .thenComparingInt(Offset::dx));
      this.scanPath = offsets;
      this.scanCursor = 0;
   }

   private static boolean intersectsRadius(
      int chunkX, int chunkZ, double centerX, double centerZ, int radiusSquared
   ) {
      double minX = (double)chunkX * 16.0;
      double minZ = (double)chunkZ * 16.0;
      double nearestX = centerX < minX ? minX - centerX
         : centerX > minX + 16.0 ? centerX - (minX + 16.0) : 0.0;
      double nearestZ = centerZ < minZ ? minZ - centerZ
         : centerZ > minZ + 16.0 ? centerZ - (minZ + 16.0) : 0.0;
      return nearestX * nearestX + nearestZ * nearestZ <= radiusSquared;
   }

   private void resetScanPath() {
      this.centerChunkX = Integer.MIN_VALUE;
      this.centerChunkZ = Integer.MIN_VALUE;
      this.pathRange = -1;
      this.scanPath = List.of();
      this.scanCursor = 0;
   }

   private static void trimLookupSchedule() {
      while (NEXT_LOOKUP_TICK.size() > MAX_LOOKUP_SCHEDULES) {
         Long eldest = NEXT_LOOKUP_TICK.keySet().iterator().next();
         NEXT_LOOKUP_TICK.remove(eldest);
      }
   }

   private int trimCache(int limit) {
      int removed = 0;
      Iterator<Map.Entry<Long, CachedChunk>> iterator = CACHE.entrySet().iterator();
      while (iterator.hasNext()) {
         CachedChunk entry = iterator.next().getValue();
         if (sessionTick - entry.lastAccessTick() > maxCacheAgeTicks) {
            iterator.remove();
            NEXT_LOOKUP_TICK.remove(key(entry.cx(), entry.cz()));
            removed++;
         }
      }

      while (CACHE.size() > limit) {
         Long eldest = CACHE.keySet().iterator().next();
         CACHE.remove(eldest);
         NEXT_LOOKUP_TICK.remove(eldest);
         removed++;
      }
      this.lastPruned = removed;
      return removed;
   }

   @Override
   public void renderHud(class_332 context) {
      if (mc.field_1724 == null || mc.field_1687 == null) return;

      class_243 center = this.resolveScanPosition();
      if (center == null) return;

      String title = "CHUNKMEMORY";
      String mode = BackgroundFreecam.isActive()
         ? BackgroundFreecam.movementModeName() + (BackgroundFreecam.isPaused() ? " / PAUSE" : "")
         : this.centerSource;
      String line = "Cache " + cachedChunkCount() + "/" + this.maxChunks.getInt()
         + "  |  " + this.range.getInt() + " B";
      String scan = this.pauseScan.get() ? "SCAN PAUSIERT"
         : "Live " + this.lastLoaded + "/" + this.lastLookups
            + "  |  Cache-Fallback " + this.lastCachedFallbacks;
      String extra = "Neu " + this.lastNewEntries + "  |  Retry uebersprungen " + this.lastRetrySkips
         + "  |  " + mode;

      int width = Math.max(mc.field_1772.method_1727(title),
         Math.max(mc.field_1772.method_1727(line),
            Math.max(mc.field_1772.method_1727(scan), mc.field_1772.method_1727(extra)))) + 18;
      int x = 8;
      int y = 42;
      context.method_25294(x, y, x + width, y + 68, 0x90000000);
      context.method_25294(x, y, x + width, y + 2, this.pauseScan.get() ? 0xFFFFB347 : 0xFF39FFB6);
      context.method_51433(mc.field_1772, title, x + 8, y + 5, 0xFF39FFB6, true);
      context.method_51433(mc.field_1772, line, x + 8, y + 18, 0xFFFFFFFF, true);
      context.method_51433(mc.field_1772, scan, x + 8, y + 31, 0xFFB8C8D8, true);
      context.method_51433(mc.field_1772, extra, x + 8, y + 44, 0xFFB8C8D8, true);
   }

   @Override
   public void onWorldLeave() {
      clearSessionCache();
      this.resetScanPath();
      this.lastLookups = 0;
      this.lastLoaded = 0;
      this.lastNewEntries = 0;
      this.lastCachedFallbacks = 0;
      this.lastRetrySkips = 0;
      this.lastPruned = 0;
   }

   public record CachedChunk(
      int cx, int cz, class_2818 chunk, long capturedAtTick, long lastAccessTick
   ) {
   }

   private record Offset(int dx, int dz, int distanceSquared) {
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Storage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_638;

/**
 * Session-only memory for chunks that are already present on the client.
 *
 * It deliberately performs lookup-only reads through the client chunk manager:
 * it never requests a chunk from the server and never changes the real player position.
 */
public final class ChunkMemory extends Module {
   private static final Map<Long, CachedChunk> CACHE = new LinkedHashMap<>(256, 0.75F, true);
   private static class_638 cacheWorld;
   private static String cacheContext;

   private final NumberSetting range = this.add(
      new NumberSetting("Scan-Reichweite (Chunks)", 16.0, 2.0, 48.0, 1.0)
   );
   private final NumberSetting scanSpeed = this.add(
      new NumberSetting("Scan-Geschwindigkeit (Chunks/Tick)", 8.0, 1.0, 64.0, 1.0)
   );
   private final NumberSetting maxChunks = this.add(
      new NumberSetting("Max. gespeicherte Chunks", 512.0, 64.0, 4096.0, 64.0)
   );
   private final BoolSetting useFreecamPosition = this.add(
      new BoolSetting("Freecam als Scan-Zentrum", true)
   );

   private int centerChunkX = Integer.MIN_VALUE;
   private int centerChunkZ = Integer.MIN_VALUE;
   private int pathRange = -1;
   private List<Offset> scanPath = List.of();
   private int scanCursor;
   private long scanTick;

   public ChunkMemory() {
      super(
         "ChunkMemory",
         "Merkt sich waehrend der Sitzung bereits empfangene Chunks und stellt sie anderen Welt-Modulen bereit.",
         Category.WORLD
      );
   }

   private static long key(int cx, int cz) {
      return cx & 0xFFFFFFFFL | (long)cz << 32;
   }

   /** Returns a cached chunk without asking Minecraft to load or request it. */
   public static class_2818 getCachedChunk(int cx, int cz) {
      if (cacheWorld == null || mc.field_1687 != cacheWorld) {
         return null;
      }
      CachedChunk cached = CACHE.get(key(cx, cz));
      return cached == null ? null : cached.chunk();
   }

   /** Snapshot of the current session index; the chunk objects remain client-owned. */
   public static Collection<CachedChunk> cachedChunks() {
      if (cacheWorld == null || mc.field_1687 != cacheWorld) {
         return List.of();
      }
      return Collections.unmodifiableList(new ArrayList<>(CACHE.values()));
   }

   public static int cachedChunkCount() {
      return cacheWorld != null && mc.field_1687 == cacheWorld ? CACHE.size() : 0;
   }

   /**
    * Reads a block from a currently loaded chunk or from the last cached chunk
    * section. A null result means the client has never had that chunk available.
    */
   public static class_2680 getBlockState(int x, int y, int z) {
      class_638 world = mc.field_1687;
      if (world == null || y < world.method_31607() || y >= world.method_31600()) {
         return null;
      }

      int cx = x >> 4;
      int cz = z >> 4;
      class_2818 loaded = world.method_2935().method_21730(cx, cz);
      if (loaded != null) {
         return world.method_8320(new class_2338(x, y, z));
      }

      class_2818 cached = getCachedChunk(cx, cz);
      if (cached == null) {
         return null;
      }

      class_2826[] sections = cached.method_12006();
      int sectionIndex = (y >> 4) - (world.method_31607() >> 4);
      if (sectionIndex < 0 || sectionIndex >= sections.length) {
         return null;
      }

      class_2826 section = sections[sectionIndex];
      if (section == null) {
         return null;
      }
      return section.method_12254(x & 15, y & 15, z & 15);
   }

   public static void clearSessionCache() {
      CACHE.clear();
      cacheWorld = null;
      cacheContext = null;
   }

   @Override
   public void onTick() {
      class_638 world = mc.field_1687;
      if (world == null || mc.field_1724 == null) {
         return;
      }

      String context = Storage.server() + "_" + Storage.dim();
      if (world != cacheWorld || !context.equals(cacheContext)) {
         clearSessionCache();
         cacheWorld = world;
         cacheContext = context;
         this.resetScanPath();
      }

      class_243 center = mc.field_1724.method_33571();
      if (this.useFreecamPosition.get() && FreecamController.isActive()) {
         class_243 freecam = FreecamController.cameraPosition();
         if (freecam != null) {
            center = freecam;
         }
      }

      int cx = ((int)Math.floor(center.field_1352)) >> 4;
      int cz = ((int)Math.floor(center.field_1350)) >> 4;
      int configuredRange = this.range.getInt();
      if (cx != this.centerChunkX || cz != this.centerChunkZ || configuredRange != this.pathRange) {
         this.centerChunkX = cx;
         this.centerChunkZ = cz;
         this.pathRange = configuredRange;
         this.rebuildScanPath(configuredRange);
      }

      int processed = 0;
      int budget = this.scanSpeed.getInt();
      while (processed < budget && !this.scanPath.isEmpty()) {
         if (this.scanCursor >= this.scanPath.size()) {
            this.scanCursor = 0;
         }

         Offset offset = this.scanPath.get(this.scanCursor++);
         int targetX = this.centerChunkX + offset.dx();
         int targetZ = this.centerChunkZ + offset.dz();

         // This is intentionally a lookup-only operation. A null result is not loaded.
         class_2818 loaded = world.method_2935().method_21730(targetX, targetZ);
         if (loaded != null) {
            CACHE.put(key(targetX, targetZ), new CachedChunk(targetX, targetZ, loaded, this.scanTick));
         }
         processed++;
      }

      this.scanTick++;
      this.trimCache(this.maxChunks.getInt());
   }

   private void rebuildScanPath(int radius) {
      List<Offset> offsets = new ArrayList<>();
      int radiusSquared = radius * radius;
      for (int dz = -radius; dz <= radius; dz++) {
         for (int dx = -radius; dx <= radius; dx++) {
            int distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= radiusSquared) {
               offsets.add(new Offset(dx, dz, distanceSquared));
            }
         }
      }
      offsets.sort(Comparator.comparingInt(Offset::distanceSquared)
         .thenComparingInt(Offset::dz)
         .thenComparingInt(Offset::dx));
      this.scanPath = offsets;
      this.scanCursor = 0;
   }

   private void resetScanPath() {
      this.centerChunkX = Integer.MIN_VALUE;
      this.centerChunkZ = Integer.MIN_VALUE;
      this.pathRange = -1;
      this.scanPath = List.of();
      this.scanCursor = 0;
   }

   private void trimCache(int limit) {
      while (CACHE.size() > limit) {
         Long eldest = CACHE.keySet().iterator().next();
         CACHE.remove(eldest);
      }
   }

   @Override
   public void onWorldLeave() {
      clearSessionCache();
      this.resetScanPath();
      this.scanTick = 0L;
   }

   public record CachedChunk(int cx, int cz, class_2818 chunk, long capturedAtTick) {
   }

   private record Offset(int dx, int dz, int distanceSquared) {
   }
}

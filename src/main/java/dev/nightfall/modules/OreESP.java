package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Esp2DRenderer;
import dev.nightfall.util.Projection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_332;
import net.minecraft.class_7923;
import net.minecraft.class_2818;

public final class OreESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 96.0, 4.0));
   private final NumberSetting maxBlocks = this.add(new NumberSetting("Max Erzblöcke pro Scan", 500.0, 20.0, 1000.0, 20.0));
   private final ModeSetting display = this.add(new ModeSetting("Darstellung", "3D", "3D", "2D", "Aus"));
   private final List<OreMarker> found = new ArrayList<>();
   private static final int[][] NEIGHBORS = {
      {1, 0, 0}, {-1, 0, 0},
      {0, 1, 0}, {0, -1, 0},
      {0, 0, 1}, {0, 0, -1}
   };
   private int timer;

   public OreESP() {
      super("OreESP", "Umschließt zusammenhängende Erzadern im geladenen Gebiet mit je einer Box; Netherquarz und Nethergold werden ignoriert.", Category.VISUALS);
   }

   private static final Map<class_2248, OreInfo> ORE_INFO = new HashMap<>();

   private static String blockId(class_2248 block) {
      class_2378 registry = (class_2378)class_7923.field_41175;
      return registry.method_10221(block).toString();
   }

   private static OreInfo oreInfo(class_2248 block) {
      return ORE_INFO.computeIfAbsent(block, key -> {
         String id = blockId(key);
         if (id.equals("minecraft:ancient_debris")) {
            return new OreInfo(true, 0xFFB56CFF);
         }
         if (id.equals("minecraft:nether_quartz_ore") || id.equals("minecraft:nether_gold_ore")
            || !id.endsWith("_ore")) {
            return new OreInfo(false, 0xFFFFFFFF);
         }
         int color = id.endsWith("diamond_ore") ? 0xFF40DFFF
            : id.endsWith("emerald_ore") ? 0xFF45E08A
            : id.endsWith("gold_ore") ? 0xFFFFD34D
            : id.endsWith("iron_ore") ? 0xFFE1D7C8
            : id.endsWith("copper_ore") ? 0xFFFF9255
            : id.endsWith("lapis_ore") ? 0xFF568CFF
            : id.endsWith("redstone_ore") ? 0xFFFF5757
            : id.endsWith("coal_ore") ? 0xFF9AA3AD : 0xFFFFFFFF;
         return new OreInfo(true, color);
      });
   }

   private static long chunkKey(int x, int z) {
      return (long)x << 32 ^ z & 0xFFFFFFFFL;
   }

   private static class_2818 scanChunk(int x, int z, Map<Long, class_2818> chunks, Set<Long> missingChunks) {
      long key = chunkKey(x, z);
      class_2818 chunk = chunks.get(key);
      if (chunk != null) return chunk;
      if (missingChunks.contains(key)) return null;

      chunk = ChunkMemory.getAvailableChunk(x, z);
      if (chunk == null) {
         missingChunks.add(key);
      } else {
         chunks.put(key, chunk);
      }
      return chunk;
   }

   private OreMarker collectVein(
      class_2338 start,
      class_2248 ore,
      Set<class_2338> visited,
      Map<Long, class_2818> chunks,
      Set<Long> missingChunks
   ) {
      ArrayDeque<class_2338> pending = new ArrayDeque<>();
      pending.add(start);
      visited.add(start);

      int minX = start.method_10263();
      int minY = start.method_10264();
      int minZ = start.method_10260();
      int maxX = minX;
      int maxY = minY;
      int maxZ = minZ;
      int blockCount = 0;
      int bottomY = mc.field_1687.method_31607();
      int topY = mc.field_1687.method_31600();

      while (!pending.isEmpty()) {
         class_2338 current = pending.removeFirst();
         int x = current.method_10263();
         int y = current.method_10264();
         int z = current.method_10260();
         blockCount++;

         minX = Math.min(minX, x);
         minY = Math.min(minY, y);
         minZ = Math.min(minZ, z);
         maxX = Math.max(maxX, x);
         maxY = Math.max(maxY, y);
         maxZ = Math.max(maxZ, z);

         for (int[] direction : NEIGHBORS) {
            class_2338 neighbor = current.method_10069(direction[0], direction[1], direction[2]);
            if (visited.contains(neighbor)) continue;

            int nx = neighbor.method_10263();
            int ny = neighbor.method_10264();
            int nz = neighbor.method_10260();
            if (ny < bottomY || ny >= topY) {
               visited.add(neighbor);
               continue;
            }

            // Only inspect client-loaded or session-cached chunk data.
            class_2818 neighborChunk = scanChunk(nx >> 4, nz >> 4, chunks, missingChunks);
            class_2680 neighborState = ChunkMemory.getBlockState(neighborChunk, nx, ny, nz);
            if (neighborState == null) {
               visited.add(neighbor);
               continue;
            }

            class_2248 neighborBlock = neighborState.method_26204();
            if (neighborBlock == ore) {
               visited.add(neighbor);
               pending.addLast(neighbor);
            } else if (!oreInfo(neighborBlock).isOre()) {
               // Remember empty/non-ore boundaries, but leave a different ore
               // type unvisited so it can seed its own vein later in this scan.
               visited.add(neighbor);
            }
         }
      }

      class_238 bounds = new class_238(
         minX, minY, minZ,
         maxX + 1.0, maxY + 1.0, maxZ + 1.0
      );
      return new OreMarker(bounds, this.oreColor(ore), blockCount);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || this.timer-- > 0) return;
      this.timer = 12;
      this.found.clear();
      class_243 scanOrigin = FreecamController.isActive()
         ? FreecamController.cameraPosition()
         : mc.field_1724.method_33571();
      class_2338 p = new class_2338(
         (int)Math.floor(scanOrigin.field_1352),
         (int)Math.floor(scanOrigin.field_1351),
         (int)Math.floor(scanOrigin.field_1350)
      );
      int r = this.range.getInt();
      int cap = this.maxBlocks.getInt();
      int minY = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
      int maxY = Math.min(mc.field_1687.method_31600() - 1, p.method_10264() + r);
      int minX = p.method_10263() - r, maxX = p.method_10263() + r;
      int minZ = p.method_10260() - r, maxZ = p.method_10260() + r;
      int scannedOreBlocks = 0;
      Set<class_2338> visited = new HashSet<>();
      Map<Long, class_2818> chunks = new HashMap<>();
      Set<Long> missingChunks = new HashSet<>();
      int radiusSquared = r * r;

      for (int cx = minX >> 4; cx <= maxX >> 4 && scannedOreBlocks < cap; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4 && scannedOreBlocks < cap; cz++) {
            class_2818 chunk = scanChunk(cx, cz, chunks, missingChunks);
            if (chunk == null) continue;
            int sx = Math.max(minX, cx << 4), ex = Math.min(maxX, (cx << 4) + 15);
            int sz = Math.max(minZ, cz << 4), ez = Math.min(maxZ, (cz << 4) + 15);
            for (int x = sx; x <= ex && scannedOreBlocks < cap; x++) {
               int dx = x - p.method_10263();
               for (int z = sz; z <= ez && scannedOreBlocks < cap; z++) {
                  int dz = z - p.method_10260();
                  int horizontalDistanceSquared = dx * dx + dz * dz;
                  if (horizontalDistanceSquared > radiusSquared) continue;
                  for (int y = minY; y <= maxY && scannedOreBlocks < cap; y++) {
                     int dy = y - p.method_10264();
                     if (horizontalDistanceSquared + dy * dy > radiusSquared) continue;
                     class_2338 bp = new class_2338(x, y, z);
                     if (visited.contains(bp)) continue;

                     class_2680 state = ChunkMemory.getBlockState(chunk, x, y, z);
                     if (state == null) continue;
                     class_2248 block = state.method_26204();
                     OreInfo info = oreInfo(block);
                     if (info.isOre()) {
                        OreMarker vein = this.collectVein(bp, block, visited, chunks, missingChunks);
                        this.found.add(new OreMarker(vein.box(), info.color(), vein.blockCount()));
                        // The last connected vein is always completed, even if
                        // its size takes this count beyond the configured limit.
                        scannedOreBlocks += vein.blockCount();
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (Projection.valid() && !this.display.is("Aus")) {
         for (OreMarker marker : this.found) {
            if (this.display.is("2D")) {
               Esp2DRenderer.draw(ctx, marker.box(), marker.color(), 2, 35, true);
            } else {
               Draw.box(ctx, marker.box(), marker.color(), 2, true);
            }
         }
      }
   }

   @Override
   protected void onDisable() { this.found.clear(); }

   @Override
   public void onWorldLeave() { this.found.clear(); this.timer = 0; }

   private record OreInfo(boolean isOre, int color) {
   }

   private record OreMarker(class_238 box, int color, int blockCount) {
   }
}

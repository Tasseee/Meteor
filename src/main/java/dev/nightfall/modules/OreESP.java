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

   private String blockId(class_2248 block) {
      class_2378 registry = (class_2378)class_7923.field_41175;
      return registry.method_10221(block).toString();
   }

   private boolean isOre(class_2248 block) {
      String id = this.blockId(block);
      if (id.equals("minecraft:ancient_debris")) return true;
      if (id.equals("minecraft:nether_quartz_ore") || id.equals("minecraft:nether_gold_ore")) return false;
      return id.endsWith("_ore");
   }

   private int oreColor(class_2248 block) {
      String id = this.blockId(block);
      if (id.equals("minecraft:ancient_debris")) return 0xFFB56CFF;
      if (id.endsWith("diamond_ore")) return 0xFF40DFFF;
      if (id.endsWith("emerald_ore")) return 0xFF45E08A;
      if (id.endsWith("gold_ore")) return 0xFFFFD34D;
      if (id.endsWith("iron_ore")) return 0xFFE1D7C8;
      if (id.endsWith("copper_ore")) return 0xFFFF9255;
      if (id.endsWith("lapis_ore")) return 0xFF568CFF;
      if (id.endsWith("redstone_ore")) return 0xFFFF5757;
      if (id.endsWith("coal_ore")) return 0xFF9AA3AD;
      return 0xFFFFFFFF;
   }

   private OreMarker collectVein(class_2338 start, class_2248 ore, Set<class_2338> visited) {
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
            if (ny < bottomY || ny >= topY) continue;

            // Only inspect client-loaded or session-cached chunk data.
            class_2680 neighborState = ChunkMemory.getBlockState(nx, ny, nz);
            if (neighborState != null && neighborState.method_26204() == ore) {
               visited.add(neighbor);
               pending.addLast(neighbor);
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
      int maxY = Math.min(mc.field_1687.method_31600(), p.method_10264() + r);
      int minX = p.method_10263() - r, maxX = p.method_10263() + r;
      int minZ = p.method_10260() - r, maxZ = p.method_10260() + r;
      int scannedOreBlocks = 0;
      Set<class_2338> visited = new HashSet<>();

      for (int cx = minX >> 4; cx <= maxX >> 4 && scannedOreBlocks < cap; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4 && scannedOreBlocks < cap; cz++) {
            if (mc.field_1687.method_2935().method_21730(cx, cz) == null
               && ChunkMemory.getCachedChunk(cx, cz) == null) continue;
            int sx = Math.max(minX, cx << 4), ex = Math.min(maxX, (cx << 4) + 15);
            int sz = Math.max(minZ, cz << 4), ez = Math.min(maxZ, (cz << 4) + 15);
            for (int x = sx; x <= ex && scannedOreBlocks < cap; x++) {
               for (int z = sz; z <= ez && scannedOreBlocks < cap; z++) {
                  for (int y = minY; y <= maxY && scannedOreBlocks < cap; y++) {
                     class_2338 bp = new class_2338(x, y, z);
                     if (visited.contains(bp)) continue;

                     class_2680 state = ChunkMemory.getBlockState(x, y, z);
                     if (state == null) continue;
                     class_2248 block = state.method_26204();
                     if (this.isOre(block)) {
                        OreMarker vein = this.collectVein(bp, block, visited);
                        this.found.add(vein);
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

   private record OreMarker(class_238 box, int color, int blockCount) {
   }
}

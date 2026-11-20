package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_2586;
import net.minecraft.class_2636;
import net.minecraft.class_7923;

public class SpawnerEsp extends BlockEntityEsp {
   private final BoolSetting hideStructureSpawners = this.add(new BoolSetting("Struktur-Spawner ausblenden", false));
   private final BoolSetting hideDungeonSpawners = this.add(new BoolSetting("Dungeon-Spawner ausblenden", false));
   private final BoolSetting ignoreChunkCorners = this.add(new BoolSetting("Spawner in Chunk-Ecken ignorieren", true));
   private final NumberSetting cornerMargin = this.add(new NumberSetting("Ecken-Abstand", 2.0, 1.0, 4.0, 1.0));
   private final Map<class_2338, Boolean> filterCache = new HashMap<>();
   private int filterCacheTimer;

   public SpawnerEsp() {
      super("SpawnerESP", "Markiert Spawner und kann typische Struktur- bzw. Dungeon-Spawner ausblenden.", 128, 256, 15485081);
   }

   @Override
   protected boolean accept(class_2586 be) {
      if (!(be instanceof class_2636)) return false;
      if (this.ignoreChunkCorners.get() && isChunkCorner(be.method_11016())) return false;
      if (!this.hideStructureSpawners.get() && !this.hideDungeonSpawners.get()) return true;

      class_2338 pos = be.method_11016().method_10062();
      Boolean cached = this.filterCache.get(pos);
      if (cached != null) return cached;

      boolean hide = this.hideDungeonSpawners.get() && isDungeonRoom(pos)
         || this.hideStructureSpawners.get() && isStructureRoom(pos);
      this.filterCache.put(pos, !hide);
      return !hide;
   }

   private boolean isChunkCorner(class_2338 pos) {
      int margin = this.cornerMargin.getInt();
      int x = pos.method_10263() & 15;
      int z = pos.method_10260() & 15;
      boolean nearWest = x < margin;
      boolean nearEast = x >= 16 - margin;
      boolean nearNorth = z < margin;
      boolean nearSouth = z >= 16 - margin;
      return nearWest && nearNorth || nearWest && nearSouth || nearEast && nearNorth || nearEast && nearSouth;
   }

   private boolean isDungeonRoom(class_2338 center) {
      int cobble = 0;
      int mossy = 0;
      boolean chest = false;
      for (int x = -6; x <= 6; x++) {
         for (int y = -4; y <= 4; y++) {
            for (int z = -6; z <= 6; z++) {
               class_2338 p = center.method_10069(x, y, z);
               String id = blockId(p);
               if (id.equals("minecraft:cobblestone")) cobble++;
               else if (id.equals("minecraft:mossy_cobblestone")) mossy++;
               else if (id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest")) chest = true;
            }
         }
      }
      return chest && cobble + mossy >= 18 && mossy >= 3;
   }

   private boolean isStructureRoom(class_2338 center) {
      int stronghold = 0;
      int fortress = 0;
      int mineshaft = 0;
      int bastion = 0;
      for (int x = -8; x <= 8; x++) {
         for (int y = -5; y <= 5; y++) {
            for (int z = -8; z <= 8; z++) {
               String id = blockId(center.method_10069(x, y, z));
               if (id.equals("minecraft:stone_bricks") || id.equals("minecraft:cracked_stone_bricks")
                  || id.equals("minecraft:mossy_stone_bricks") || id.equals("minecraft:infested_stone_bricks")) stronghold++;
               if (id.equals("minecraft:nether_bricks") || id.equals("minecraft:red_nether_bricks")
                  || id.equals("minecraft:nether_brick_fence")) fortress++;
               if (id.equals("minecraft:rail") || id.equals("minecraft:powered_rail")
                  || id.equals("minecraft:detector_rail") || id.equals("minecraft:activator_rail")
                  || id.equals("minecraft:cobweb")) mineshaft++;
               if (id.equals("minecraft:polished_blackstone_bricks") || id.equals("minecraft:blackstone")
                  || id.equals("minecraft:gilded_blackstone")) bastion++;
            }
         }
      }
      return stronghold >= 20 || fortress >= 12 || mineshaft >= 3 || bastion >= 20;
   }

   private String blockId(class_2338 pos) {
      class_2248 block = mc.field_1687.method_8320(pos).method_26204();
      return ((class_2378<class_2248>) class_7923.field_41175).method_10221(block).toString();
   }

   @Override
   public void onTick() {
      if (this.filterCacheTimer-- <= 0) {
         this.filterCacheTimer = 20;
         this.filterCache.clear();
      }
      super.onTick();
   }

   @Override
   protected void onDisable() {
      super.onDisable();
      this.filterCache.clear();
      this.filterCacheTimer = 0;
   }

   @Override
   public void onWorldLeave() {
      super.onWorldLeave();
      this.filterCache.clear();
      this.filterCacheTimer = 0;
   }

   @Override
   protected String nameOf(class_2586 be) {
      return "Spawner";
   }
}

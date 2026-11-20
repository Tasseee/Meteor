package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_2586;
import net.minecraft.class_2636;
import net.minecraft.class_2818;
import net.minecraft.class_3341;
import net.minecraft.class_3449;
import net.minecraft.class_4184;
import net.minecraft.class_243;
import net.minecraft.class_2248;
import net.minecraft.class_7923;

public class SpawnerEsp extends BlockEntityEsp {
   private final BoolSetting hideStructureSpawners = this.add(new BoolSetting("Struktur-Spawner ausblenden", true));
   private final BoolSetting hideDungeonSpawners = this.add(new BoolSetting("Dungeon-Spawner ausblenden", true));
   private final List<class_3341> structureBounds = new ArrayList<>();
   private final Set<class_2338> dungeonSpawnerPositions = new HashSet<>();
   private int cacheTimer;

   public SpawnerEsp() {
      super("SpawnerESP", "Markiert Spawner; blendet generierte Struktur- und Dungeon-Spawner aus.", 128, 256, 15485081);
   }

   @Override
   protected boolean accept(class_2586 be) {
      if (!(be instanceof class_2636)) return false;

      class_2338 pos = be.method_11016();
      if (this.hideStructureSpawners.get()) {
         for (class_3341 bounds : this.structureBounds) {
            if (bounds.method_47593(pos.method_10263(), pos.method_10264(), pos.method_10260())) {
               return false;
            }
         }
      }

      return !this.hideDungeonSpawners.get() || !this.dungeonSpawnerPositions.contains(pos.method_10062());
   }

   @Override
   protected String nameOf(class_2586 be) {
      return "Spawner";
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null && this.cacheTimer-- <= 0) {
         this.cacheTimer = 20;
         this.rebuildGeneratedSpawnerCache();
      }
      super.onTick();
   }

   private void rebuildGeneratedSpawnerCache() {
      this.structureBounds.clear();
      this.dungeonSpawnerPositions.clear();

      if (!this.hideStructureSpawners.get() && !this.hideDungeonSpawners.get()) return;

      class_4184 camera = mc.field_1773.method_19418();
      class_243 origin = camera != null && camera.method_19332() ? camera.method_71156() : mc.field_1724.method_33571();
      int centerChunkX = (int)Math.floor(origin.field_1352) >> 4;
      int centerChunkZ = (int)Math.floor(origin.field_1350) >> 4;
      int scanRadius = (this.range.getInt() >> 4) + 1;
      int structureRadius = scanRadius + 8;

      for (int dz = -structureRadius; dz <= structureRadius; dz++) {
         for (int dx = -structureRadius; dx <= structureRadius; dx++) {
            class_2818 chunk = mc.field_1687.method_2935().method_21730(centerChunkX + dx, centerChunkZ + dz);
            if (chunk == null) continue;

            if (this.hideStructureSpawners.get()) {
               for (class_3449 start : chunk.method_12016().values()) {
                  if (start != null && start.method_14969() != null) {
                     this.structureBounds.add(start.method_14969());
                  }
               }
            }

            if (this.hideDungeonSpawners.get() && Math.abs(dx) <= scanRadius && Math.abs(dz) <= scanRadius) {
               for (class_2586 blockEntity : chunk.method_12214().values()) {
                  if (blockEntity instanceof class_2636) {
                     class_2338 pos = blockEntity.method_11016();
                     if (this.looksLikeGeneratedDungeon(pos)) {
                        this.dungeonSpawnerPositions.add(pos.method_10062());
                     }
                  }
               }
            }
         }
      }
   }

   /**
    * Dungeons are placed as features rather than registered StructureStarts.
    * Their classic cobblestone/mossy-cobblestone room and nearby chest provide
    * a best-effort signature. The separate setting lets users disable this
    * heuristic if a player-built room happens to match it.
    */
   private boolean looksLikeGeneratedDungeon(class_2338 spawnerPos) {
      int roughStone = 0;
      boolean nearbyChest = false;
      class_2378<?> blockRegistry = (class_2378<?>)class_7923.field_41175;

      for (int y = -2; y <= 2; y++) {
         for (int z = -4; z <= 4; z++) {
            for (int x = -4; x <= 4; x++) {
               class_2248 block = mc.field_1687.method_8320(
                  new class_2338(spawnerPos.method_10263() + x, spawnerPos.method_10264() + y, spawnerPos.method_10260() + z)
               ).method_26204();
               String id = blockRegistry.method_10221(block).toString();
               if (id.equals("minecraft:cobblestone") || id.equals("minecraft:mossy_cobblestone")) {
                  roughStone++;
               } else if (id.equals("minecraft:chest")) {
                  nearbyChest = true;
               }
            }
         }
      }

      return roughStone >= 24 && (nearbyChest || roughStone >= 48);
   }

   @Override
   protected void onDisable() {
      super.onDisable();
      this.structureBounds.clear();
      this.dungeonSpawnerPositions.clear();
      this.cacheTimer = 0;
   }

   @Override
   public void onWorldLeave() {
      super.onWorldLeave();
      this.structureBounds.clear();
      this.dungeonSpawnerPositions.clear();
      this.cacheTimer = 0;
   }
}

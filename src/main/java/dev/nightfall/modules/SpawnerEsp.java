package dev.nightfall.modules;

import dev.nightfall.module.setting.ModeSetting;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2636;

/**
 * Highlights spawners in already-loaded chunks. Dungeon detection is a
 * conservative client-side heuristic, not a definitive custom-spawner test.
 */
public class SpawnerEsp extends BlockEntityEsp {
   private final ModeSetting dungeonMode =
      this.add(new ModeSetting("Dungeon-Modus", "Dungeon ausblenden", "Alle zeigen", "Dungeon markieren", "Dungeon ausblenden"));
   private final Map<class_2338, DungeonCache> dungeonCache = new HashMap<>();
   private int scanEpoch;

   public SpawnerEsp() {
      super("SpawnerESP", "Markiert Spawner mit vorsichtigem Dungeon-Filter.", 256, 256, 0xB66CFF, 3);
   }

   @Override
   protected boolean accept(class_2586 be) {
      return be instanceof class_2636;
   }

   @Override
   protected void onScanStart() {
      this.scanEpoch++;
   }

   @Override
   protected void onEspReset() {
      this.dungeonCache.clear();
      this.scanEpoch = 0;
   }

   @Override
   protected boolean acceptForScan(class_2586 be) {
      if (!this.accept(be)) {
         return false;
      }
      if (this.dungeonMode.is("Alle zeigen")) {
         return true;
      }

      boolean dungeon = this.looksLikeDungeon(be.method_11016());
      return !this.dungeonMode.is("Dungeon ausblenden") || !dungeon;
   }

   private boolean looksLikeDungeon(class_2338 pos) {
      class_2338 key = pos.method_10062();
      DungeonCache cached = this.dungeonCache.get(key);
      if (cached != null && this.scanEpoch - cached.epoch < 5) {
         return cached.dungeon;
      }

      if (mc.field_1687 == null) {
         return false;
      }

      int cobble = 0;
      int chests = 0;
      for (int y = -2; y <= 2; y++) {
         for (int z = -4; z <= 4; z++) {
            for (int x = -4; x <= 4; x++) {
               String state = mc.field_1687.method_8320(pos.method_10069(x, y, z))
                  .toString().toLowerCase(Locale.ROOT);
               if (state.contains("cobblestone")) cobble++;
               if (state.contains("chest")) chests++;
            }
         }
      }

      // Higher threshold than before: only flag strong dungeon-like layouts.
      boolean result = cobble >= 40 && chests >= 1;
      this.dungeonCache.put(key, new DungeonCache(result, this.scanEpoch));
      return result;
   }

   @Override
   protected String nameOf(class_2586 be) {
      return "✦ Spawner";
   }

   @Override
   protected String labelOf(class_2586 be, double distance) {
      String name = this.nameOf(be);
      if (!this.dungeonMode.is("Alle zeigen") && this.looksLikeDungeon(be.method_11016())
         && this.dungeonMode.is("Dungeon markieren")) {
         name += " · ?Dungeon";
      }
      if (this.distance.get()) {
         name += " · " + (int)distance + "m";
      }
      return this.names.get() ? name : (this.distance.get() ? (int)distance + "m" : "");
   }

   @Override
   protected int colorOf(class_2586 be) {
      if (this.dungeonMode.is("Dungeon markieren") && this.looksLikeDungeon(be.method_11016())) {
         return 0xFFAA33;
      }
      return this.color.rgb();
   }

   private static final class DungeonCache {
      private final boolean dungeon;
      private final int epoch;

      private DungeonCache(boolean dungeon, int epoch) {
         this.dungeon = dungeon;
         this.epoch = epoch;
      }
   }
}

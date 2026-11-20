package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import java.util.Locale;
import net.minecraft.class_2338;
import net.minecraft.class_2586;
import net.minecraft.class_2636;

/**
 * Highlights spawner block entities in already-loaded chunks.
 *
 * The dungeon filter is deliberately heuristic: it only examines block states
 * that the client already has. It does not request or reveal unloaded/hidden data.
 */
public class SpawnerEsp extends BlockEntityEsp {
   private final BoolSetting filterDungeons =
      this.add(new BoolSetting("Natürliche Dungeons filtern", true));

   public SpawnerEsp() {
      // Start at the maximum supported range (256 blocks).
      super("SpawnerESP", "Markiert Spawner und filtert typische Dungeon-Räume.", 256, 256, 0xB66CFF, 3);
   }

   @Override
   protected boolean accept(class_2586 be) {
      return be instanceof class_2636;
   }

   @Override
   protected boolean acceptForScan(class_2586 be) {
      if (!this.accept(be)) {
         return false;
      }

      return !this.filterDungeons.get() || !looksLikeDungeon(be.method_11016());
   }

   /**
    * A vanilla dungeon commonly has a compact cobblestone/mossy-cobblestone
    * room and at least one chest. Requiring both signals reduces false
    * positives, but cannot perfectly distinguish server-custom spawners.
    */
   private static boolean looksLikeDungeon(class_2338 center) {
      int cobble = 0;
      int chests = 0;

      for (int y = -2; y <= 2; y++) {
         for (int z = -4; z <= 4; z++) {
            for (int x = -4; x <= 4; x++) {
               String state = net.minecraft.class_310.method_1551().field_1687
                  .method_8320(center.method_10069(x, y, z))
                  .toString()
                  .toLowerCase(Locale.ROOT);

               if (state.contains("cobblestone")) {
                  cobble++;
               }
               if (state.contains("chest")) {
                  chests++;
               }
            }
         }
      }

      return cobble >= 28 && chests >= 1;
   }

   @Override
   protected String nameOf(class_2586 be) {
      return "✦ Spawner";
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_2636;
import net.minecraft.class_2818;
import net.minecraft.class_3719;

public final class BlockNotifier extends Module {
   private final BoolSetting chests = this.add(new BoolSetting("Chests/Barrels", true));
   private final BoolSetting shulkers = this.add(new BoolSetting("Shulker", true));
   private final BoolSetting spawners = this.add(new BoolSetting("Spawner", true));
   private final BoolSetting hoppers = this.add(new BoolSetting("Hopper", false));
   private final NumberSetting range = this.add(new NumberSetting("Range (Blocks)", 48.0, 8.0, 128.0, 8.0));
   private final Set<Long> seen = new HashSet<>();
   private boolean initialized;
   private int timer;

   public BlockNotifier() {
      super("BlockNotifier", "Meldet neue, clientseitig sichtbare Block-Entities in geladenen Chunks.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.seen.clear();
      this.initialized = false;
      this.timer = 0;
   }

   @Override
   protected void onDisable() {
      this.seen.clear();
      this.initialized = false;
   }

   @Override
   public void onWorldLeave() {
      this.seen.clear();
      this.initialized = false;
      this.timer = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null && this.timer-- <= 0) {
         this.timer = 20;
         int cx = mc.field_1724.method_31477() >> 4;
         int cz = mc.field_1724.method_31479() >> 4;
         int chunkRange = this.range.getInt() + 15 >> 4;
         double maxDistanceSq = (double)this.range.getInt() * this.range.getInt();
         Set<Long> current = new HashSet<>();

         for (int dz = -chunkRange; dz <= chunkRange; dz++) {
            for (int dx = -chunkRange; dx <= chunkRange; dx++) {
               class_2818 chunk = mc.field_1687.method_2935().method_21730(cx + dx, cz + dz);
               if (chunk != null) {
                  for (class_2586 be : chunk.method_12214().values()) {
                     if (this.accept(be)) {
                        class_2338 pos = be.method_11016();
                        if (!(pos.method_40081(mc.field_1724.method_23317(), mc.field_1724.method_23318(), mc.field_1724.method_23321()) > maxDistanceSq)) {
                           long key = pos.method_10063();
                           current.add(key);
                           if (this.initialized && !this.seen.contains(key)) {
                              String type = this.label(be);
                              mc.field_1724
                                 .method_7353(
                                    class_2561.method_43470(
                                       "[Nightfall] "
                                          + type
                                          + " entdeckt bei "
                                          + pos.method_10263()
                                          + " "
                                          + pos.method_10264()
                                          + " "
                                          + pos.method_10260()
                                          + " (clientseitig sichtbar; Inhalt nicht geprüft)"
                                    ),
                                    false
                                 );
                           }
                        }
                     }
                  }
               }
            }
         }

         this.seen.clear();
         this.seen.addAll(current);
         this.initialized = true;
      }
   }

   private boolean accept(class_2586 be) {
      if (!this.chests.get() || !(be instanceof class_2595) && !(be instanceof class_3719)) {
         if (this.shulkers.get() && be instanceof class_2627) {
            return true;
         } else {
            return this.spawners.get() && be instanceof class_2636 ? true : this.hoppers.get() && be instanceof class_2614;
         }
      } else {
         return true;
      }
   }

   private String label(class_2586 be) {
      if (be instanceof class_2595) {
         return "Chest";
      } else if (be instanceof class_3719) {
         return "Barrel";
      } else if (be instanceof class_2627) {
         return "Shulker";
      } else if (be instanceof class_2636) {
         return "Spawner";
      } else {
         return be instanceof class_2614 ? "Hopper" : "Block-Entity";
      }
   }
}

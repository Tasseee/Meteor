package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2561;

public final class SpawnerProtect extends Module {
   private final NumberSetting radius = this.add(new NumberSetting("Suchradius", 8.0, 4.0, 16.0, 1.0));
   private final BoolSetting disconnect = this.add(new BoolSetting("Bei Verlust trennen", false));
   private final Set<class_2338> tracked = new HashSet<>();
   private int ticks;

   public SpawnerProtect() {
      super("SpawnerProtect", "Warnt, wenn ein zuvor sichtbarer Spawner in der Nähe verschwindet.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      this.tracked.clear();
      this.ticks = 0;
   }

   @Override
   protected void onDisable() {
      this.tracked.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (++this.ticks % 10 == 0) {
            class_2338 center = mc.field_1724.method_24515();
            int r = this.radius.getInt();

            for (int x = -r; x <= r; x++) {
               for (int y = -r; y <= r; y++) {
                  for (int z = -r; z <= r; z++) {
                     if (x * x + y * y + z * z <= r * r) {
                        class_2338 p = center.method_10069(x, y, z);
                        if (mc.field_1687.method_22340(p) && mc.field_1687.method_8320(p).method_27852(class_2246.field_10260)) {
                           this.tracked.add(p.method_10062());
                        }
                     }
                  }
               }
            }

            for (class_2338 p : new HashSet<>(this.tracked)) {
               if (!(p.method_10262(center) > (r + 2.0) * (r + 2.0))
                  && mc.field_1687.method_22340(p)
                  && !mc.field_1687.method_8320(p).method_27852(class_2246.field_10260)) {
                  this.tracked.remove(p);
                  String msg = "Spawner bei " + p.method_10263() + " " + p.method_10264() + " " + p.method_10260() + " ist nicht mehr sichtbar.";
                  NotificationCenter.push("SpawnerProtect", msg, 8000L, -41620);
                  mc.field_1724.method_7353(class_2561.method_43470("[Nightfall] WARNUNG: " + msg), false);
                  if (this.disconnect.get() && mc.method_1562() != null) {
                     mc.method_1562().method_48296().method_10747(class_2561.method_43470("Nightfall SpawnerProtect: Spawner verschwunden"));
                     return;
                  }
               }
            }
         }
      }
   }
}

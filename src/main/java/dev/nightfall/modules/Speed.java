package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class Speed extends Module {
   private static final double BASE_SPRINT = 0.2806;
   private final NumberSetting multiplier = this.add(new NumberSetting("Multiplier", 1.4, 1.0, 3.0, 0.05));
   private final BoolSetting onlyGround = this.add(new BoolSetting("Only Ground", false));

   public Speed() {
      super("Speed", "Erhoeht die Laufgeschwindigkeit (nur bei Bewegung).", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && !p.method_5765() && !p.method_5799() && !p.method_5771() && !p.method_6101() && !p.method_31549().field_7479 && !p.method_6128()) {
         if (!this.onlyGround.get() || p.method_24828()) {
            class_243 dir = MoveUtil.direction();
            if (dir != class_243.field_1353) {
               double s = 0.2806 * this.multiplier.get();
               class_243 vel = p.method_18798();
               p.method_18800(dir.field_1352 * s, vel.field_1351, dir.field_1350 * s);
            }
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class WaterWalk extends Module {
   private final NumberSetting speed = this.add(new NumberSetting("Speed (b/s)", 4.3, 1.0, 12.0, 0.1));

   public WaterWalk() {
      super("WaterWalk", "Gehen im Wasser mit Landgeschwindigkeit.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && !p.method_31549().field_7479 && !p.method_5765() && p.method_5799()) {
         class_243 d = MoveUtil.direction();
         if (d != class_243.field_1353) {
            double s = this.speed.get() / 20.0;
            class_243 v = p.method_18798();
            p.method_18800(d.field_1352 * s, v.field_1351, d.field_1350 * s);
         }
      }
   }
}

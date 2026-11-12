package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class LongJump extends Module {
   private final NumberSetting boost = this.add(new NumberSetting("Boost", 0.55, 0.2, 1.5, 0.05));

   public LongJump() {
      super("LongJump", "Zusaetzlicher Vorwaertsschub beim Absprung.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && p.method_24828() && mc.field_1690.field_1903.method_1434() && !p.method_5799()) {
         class_243 d = MoveUtil.direction();
         if (d != class_243.field_1353) {
            class_243 v = p.method_18798();
            p.method_18800(d.field_1352 * this.boost.get(), Math.max(v.field_1351, 0.42), d.field_1350 * this.boost.get());
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class SafeWalk extends Module {
   private final NumberSetting maxDrop = this.add(new NumberSetting("Max. Fallhoehe", 1.0, 1.0, 4.0, 0.5));

   public SafeWalk() {
      super("SafeWalk", "Stoppt an Kanten (clientseitig).", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && p.method_24828() && !p.method_5715() && !p.method_31549().field_7479) {
         class_243 v = p.method_18798();
         if (v.field_1352 != 0.0 || v.field_1350 != 0.0) {
            class_238 shifted = p.method_5829().method_989(v.field_1352, -this.maxDrop.get(), v.field_1350);
            if (mc.field_1687.method_8587(p, shifted)) {
               p.method_18800(0.0, v.field_1351, 0.0);
            }
         }
      }
   }
}

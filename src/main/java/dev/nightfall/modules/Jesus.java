package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.class_243;
import net.minecraft.class_3486;
import net.minecraft.class_746;

public class Jesus extends Module {
   public Jesus() {
      super("Jesus", "Schwebt an der Wasseroberflaeche.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && !p.method_31549().field_7479 && !p.method_5765()) {
         if (p.method_5799()) {
            if (!mc.field_1690.field_1832.method_1434() && !mc.field_1690.field_1903.method_1434()) {
               boolean headUnder = mc.field_1687.method_8316(p.method_24515().method_10084()).method_15767(class_3486.field_15517);
               class_243 v = p.method_18798();
               p.method_18800(v.field_1352, headUnder ? 0.18 : 0.0, v.field_1350);
            }
         }
      }
   }
}

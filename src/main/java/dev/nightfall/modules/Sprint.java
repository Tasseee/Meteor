package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.class_746;

public class Sprint extends Module {
   public Sprint() {
      super("Sprint", "Sprintet automatisch beim Vorwaertslaufen.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null) {
         boolean ok = mc.field_1690.field_1894.method_1434() && !p.method_5715() && !p.field_5976 && p.method_7344().method_7586() > 6 && !p.method_6115();
         if (ok) {
            p.method_5728(true);
         }
      }
   }
}

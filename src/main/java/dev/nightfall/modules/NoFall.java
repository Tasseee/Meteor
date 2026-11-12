package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;

public final class NoFall extends Module {
   private final BoolSetting onlyAbove = this.add(new BoolSetting("Nur ab 3 Bloecken", true));

   public NoFall() {
      super("NoFall", "Setzt die lokale Falldistanz vor der Landung zurueck.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && !mc.field_1724.method_6128() && !mc.field_1724.method_5799() && !mc.field_1724.method_5771()) {
         if (!mc.field_1724.method_24828() && mc.field_1724.method_18798().field_1351 < 0.0 && (!this.onlyAbove.get() || mc.field_1724.field_6017 >= 3.0)) {
            mc.field_1724.field_6017 = 0.0;
         }
      }
   }
}

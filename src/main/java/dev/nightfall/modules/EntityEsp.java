package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1311;
import net.minecraft.class_1657;

public class EntityEsp extends EntityScanEsp {
   private final BoolSetting hostile = this.add(new BoolSetting("Feindlich", true));
   private final BoolSetting passive = this.add(new BoolSetting("Passiv", false));

   public EntityEsp() {
      super("EntityESP", "Markiert Mobs.", 128, 512, 16096779);
   }

   @Override
   protected boolean accept(class_1297 e) {
      if (e instanceof class_1309 && !(e instanceof class_1657)) {
         boolean h = e.method_5864().method_5891() == class_1311.field_6302;
         return h ? this.hostile.get() : this.passive.get();
      } else {
         return false;
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.EntityTypeSetting;
import net.minecraft.class_7923;
import net.minecraft.class_2378;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1311;
import net.minecraft.class_1657;
import net.minecraft.class_1299;

public class EntityEsp extends EntityScanEsp {
   private final BoolSetting hostile = this.add(new BoolSetting("Feindlich", true));
   private final BoolSetting passive = this.add(new BoolSetting("Passiv", false));
   private final EntityTypeSetting entities = this.add(new EntityTypeSetting("Entities"));

   public EntityEsp() {
      super("EntityESP", "Markiert Mobs.", 128, 512, 16096779);
   }

   @Override
   protected boolean accept(class_1297 e) {
      if (!this.entities.isEmpty()) {
         try {
            class_2378<class_1299<?>> registry = (class_2378<class_1299<?>>) class_7923.field_41177;
            String id = registry.method_10221(e.method_5864()).toString().toLowerCase(java.util.Locale.ROOT);
            return this.entities.has(id);
         } catch (Throwable ignored) {
            return false;
         }
      }

      if (e instanceof class_1309 && !(e instanceof class_1657)) {
         boolean h = e.method_5864().method_5891() == class_1311.field_6302;
         return h ? this.hostile.get() : this.passive.get();
      } else {
         return false;
      }
   }
}

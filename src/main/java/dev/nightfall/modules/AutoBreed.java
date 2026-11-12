package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import java.util.List;
import net.minecraft.class_1268;
import net.minecraft.class_1429;
import net.minecraft.class_1799;

public final class AutoBreed extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 4.0, 2.0, 6.0, 1.0));
   private final NumberSetting interval = this.add(new NumberSetting("Intervall (Ticks)", 20.0, 10.0, 80.0, 5.0));
   private int cooldown;

   public AutoBreed() {
      super("AutoBreed", "Füttert jeweils ein passendes, noch nicht verliebtes Tier in der Nähe.", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      this.cooldown = 0;
   }

   @Override
   protected void onDisable() {
      this.cooldown = 0;
   }

   @Override
   public void onWorldLeave() {
      this.cooldown = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null) {
         if (this.cooldown > 0) {
            this.cooldown--;
         } else {
            class_1799 food = mc.field_1724.method_6047();
            if (!food.method_7960()) {
               List<class_1429> animals = mc.field_1687
                  .method_8390(
                     class_1429.class,
                     mc.field_1724.method_5829().method_1014(this.range.get()),
                     animalx -> animalx.method_5805() && !animalx.method_6479() && animalx.method_6481(food)
                  );
               if (!animals.isEmpty()) {
                  class_1429 nearest = null;
                  double nearestDistance = Double.MAX_VALUE;

                  for (class_1429 animal : animals) {
                     double distance = mc.field_1724.method_5858(animal);
                     if (distance < nearestDistance) {
                        nearest = animal;
                        nearestDistance = distance;
                     }
                  }

                  if (nearest != null) {
                     mc.field_1761.method_2905(mc.field_1724, nearest, class_1268.field_5808);
                     this.cooldown = this.interval.getInt();
                  }
               }
            }
         }
      }
   }
}

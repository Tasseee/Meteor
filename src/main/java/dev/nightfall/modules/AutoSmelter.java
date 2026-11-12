package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1713;
import net.minecraft.class_1720;
import net.minecraft.class_1799;
import net.minecraft.class_7923;

public final class AutoSmelter extends Module {
   private final NumberSetting interval = this.add(new NumberSetting("Intervall (Ticks)", 10.0, 5.0, 40.0, 1.0));
   private int ticks;

   public AutoSmelter() {
      super("AutoSmelter", "Befüllt ein geöffnetes Ofen-Menü vorsichtig mit passenden Items und Brennstoff.", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
   }

   @Override
   protected void onDisable() {
      this.ticks = 0;
   }

   @Override
   public void onWorldLeave() {
      this.ticks = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1761 != null && mc.field_1724.field_7512 instanceof class_1720 handler) {
         if (++this.ticks >= this.interval.getInt()) {
            this.ticks = 0;
            if (!handler.method_7611(0).method_7681()) {
               for (int i = 3; i < handler.method_7602().size(); i++) {
                  class_1799 stack = handler.method_7611(i).method_7677();
                  if (isSmeltable(stack)) {
                     mc.field_1761.method_2906(handler.field_7763, i, 0, class_1713.field_7794, mc.field_1724);
                     return;
                  }
               }
            }

            if (!handler.method_7611(1).method_7681()) {
               for (int ix = 3; ix < handler.field_7761.size(); ix++) {
                  class_1799 stack = handler.method_7611(ix).method_7677();
                  if (isFuel(stack)) {
                     mc.field_1761.method_2906(handler.field_7763, ix, 0, class_1713.field_7794, mc.field_1724);
                     return;
                  }
               }
            }
         }
      }
   }

   private static boolean isSmeltable(class_1799 stack) {
      if (stack.method_7960()) {
         return false;
      } else {
         String id = class_7923.field_41178.method_10221(stack.method_7909()).method_12832();
         return id.startsWith("raw_")
            || id.endsWith("_ore")
            || id.equals("cobblestone")
            || id.equals("sand")
            || id.equals("clay_ball")
            || id.equals("netherrack")
            || id.equals("wet_sponge")
            || id.equals("kelp")
            || id.equals("chorus_fruit");
      }
   }

   private static boolean isFuel(class_1799 stack) {
      if (stack.method_7960()) {
         return false;
      } else {
         String id = class_7923.field_41178.method_10221(stack.method_7909()).method_12832();
         return id.equals("coal") || id.equals("charcoal") || id.equals("blaze_rod") || id.endsWith("_planks") || id.endsWith("_log") || id.endsWith("_wood");
      }
   }
}

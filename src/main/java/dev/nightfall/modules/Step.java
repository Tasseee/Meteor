package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1324;
import net.minecraft.class_5134;

public class Step extends Module {
   private static final double VANILLA = 0.6;
   private final NumberSetting height = this.add(new NumberSetting("Hoehe", 1.0, 0.6, 2.5, 0.1));
   private double previousHeight = 0.6;
   private boolean captured;

   public Step() {
      super("Step", "Erhoeht die Stufenhoehe (Attribut step_height).", Category.MOVEMENT);
   }

   private class_1324 attr() {
      return mc.field_1724 == null ? null : mc.field_1724.method_5996(class_5134.field_47761);
   }

   @Override
   public void onTick() {
      class_1324 a = this.attr();
      if (a != null) {
         if (!this.captured) {
            this.previousHeight = a.method_6201();
            this.captured = true;
         }

         if (a.method_6201() != this.height.get()) {
            a.method_6192(this.height.get());
         }
      }
   }

   @Override
   protected void onDisable() {
      this.restore();
   }

   @Override
   public void onWorldLeave() {
      this.restore();
   }

   private void restore() {
      class_1324 a = this.attr();
      if (this.captured && a != null) {
         a.method_6192(this.previousHeight);
      }

      this.captured = false;
      this.previousHeight = 0.6;
   }
}

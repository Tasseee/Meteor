package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.class_243;
import net.minecraft.class_746;

public class Fly extends Module {
   private final NumberSetting speed = this.add(new NumberSetting("Speed (b/s)", 11.5, 1.0, 40.0, 0.5));
   private final NumberSetting vertical = this.add(new NumberSetting("Vertical (b/s)", 9.0, 1.0, 30.0, 0.5));

   public Fly() {
      super("Fly", "Clientseitiger Flug: WASD, Space hoch, Shift runter.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && !p.method_5765() && !p.method_6128()) {
         class_243 dir = MoveUtil.direction();
         double h = this.speed.get() / 20.0;
         double v = this.vertical.get() / 20.0;
         double vy = mc.field_1690.field_1903.method_1434() ? v : (mc.field_1690.field_1832.method_1434() ? -v : 0.0);
         p.method_18800(dir.field_1352 * h, vy, dir.field_1350 * h);
      }
   }
}

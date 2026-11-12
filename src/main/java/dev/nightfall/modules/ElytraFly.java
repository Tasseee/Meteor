package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.class_243;
import net.minecraft.class_746;

public final class ElytraFly extends Module {
   private final NumberSetting horizontal = this.add(new NumberSetting("Horizontal (b/s)", 26.0, 5.0, 60.0, 1.0));
   private final NumberSetting vertical = this.add(new NumberSetting("Vertical (b/s)", 9.0, 1.0, 25.0, 1.0));

   public ElytraFly() {
      super("ElytraFly", "Steuert den Gleitflug: WASD, Space hoch, Shift runter.", Category.MOVEMENT);
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player != null && player.method_6128() && !player.method_5765()) {
         class_243 direction = MoveUtil.direction();
         class_243 velocity = player.method_18798();
         double horizontalSpeed = this.horizontal.get() / 20.0;
         double verticalSpeed = this.vertical.get() / 20.0;
         double x = direction.method_1027() > 0.0 ? direction.field_1352 * horizontalSpeed : velocity.field_1352;
         double z = direction.method_1027() > 0.0 ? direction.field_1350 * horizontalSpeed : velocity.field_1350;
         double y = mc.field_1690.field_1903.method_1434() ? verticalSpeed : (mc.field_1690.field_1832.method_1434() ? -verticalSpeed : velocity.field_1351);
         player.method_18800(x, y, z);
      }
   }
}

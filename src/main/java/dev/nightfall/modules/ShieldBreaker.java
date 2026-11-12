package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1743;

public final class ShieldBreaker extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 4.2, 2.0, 6.0, 0.2));
   private final NumberSetting delay = this.add(new NumberSetting("Angriffsintervall (Ticks)", 3.0, 1.0, 12.0, 1.0));
   private int cooldown;
   private int previousSlot = -1;
   private class_1657 pendingTarget;

   public ShieldBreaker() {
      super("ShieldBreaker", "Wechselt bei blockenden Gegnern kurz zur Axt und greift an.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) {
         this.restore();
      } else if (this.previousSlot >= 0) {
         class_1657 target = this.pendingTarget;
         if (target != null
            && target.method_5805()
            && target.method_6039()
            && mc.field_1724.method_5858(target) <= this.range.get() * this.range.get()
            && mc.field_1724.method_6057(target)) {
            mc.field_1761.method_2918(mc.field_1724, target);
            mc.field_1724.method_6104(class_1268.field_5808);
            this.cooldown = this.delay.getInt();
         }

         this.restore();
      } else if (this.cooldown > 0) {
         this.cooldown--;
      } else {
         class_1657 target = CombatUtil.nearestPlayer(this.range.get());
         if (target != null && target.method_6039() && mc.field_1724.method_6057(target)) {
            int axeSlot = -1;

            for (int i = 0; i < 9; i++) {
               if (mc.field_1724.method_31548().method_5438(i).method_7909() instanceof class_1743) {
                  axeSlot = i;
                  break;
               }
            }

            if (axeSlot >= 0) {
               this.previousSlot = mc.field_1724.method_31548().method_67532();
               this.pendingTarget = target;
               mc.field_1724.method_31548().method_61496(axeSlot);
            }
         }
      }
   }

   private void restore() {
      if (this.previousSlot >= 0 && mc.field_1724 != null) {
         mc.field_1724.method_31548().method_61496(this.previousSlot);
      }

      this.previousSlot = -1;
      this.pendingTarget = null;
   }

   @Override
   protected void onDisable() {
      this.restore();
      this.cooldown = 0;
   }

   @Override
   public void onWorldLeave() {
      this.restore();
      this.cooldown = 0;
   }
}

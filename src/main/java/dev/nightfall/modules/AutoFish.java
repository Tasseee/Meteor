package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1268;
import net.minecraft.class_1802;

public final class AutoFish extends Module {
   private final NumberSetting minimumInterval = this.add(new NumberSetting("Mindestabstand (Ticks)", 12.0, 8.0, 40.0, 1.0));
   private final BoolSetting mainHandOnly = this.add(new BoolSetting("Nur Haupthand", true));
   private int cooldown;

   public AutoFish() {
      super("AutoFish", "Zieht die Angel bei erkanntem Biss ein; wirft nicht automatisch erneut aus.", Category.MISC);
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
         } else if (mc.field_1724.field_7513 != null && mc.field_1724.field_7513.method_5805()) {
            if (!this.mainHandOnly.get() || mc.field_1724.method_6047().method_31574(class_1802.field_8378)) {
               if (mc.field_1724.field_7513.method_18798().field_1351 < -0.08) {
                  mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
                  this.cooldown = this.minimumInterval.getInt();
               }
            }
         }
      }
   }
}

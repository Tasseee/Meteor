package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1802;
import net.minecraft.class_746;

public final class AutoGap extends Module {
   private final NumberSetting health = this.add(new NumberSetting("Health-Schwelle", 10.0, 2.0, 19.0, 1.0));
   private boolean eating;
   private boolean used;
   private boolean previousUsePressed;
   private int previousSlot;
   private int ticks;
   private int cooldown;

   public AutoGap() {
      super("AutoGap", "Isst bei niedriger Health einen goldenen Apfel.", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.finish();
   }

   @Override
   public void onWorldLeave() {
      this.finish();
      this.cooldown = 0;
   }

   private void finish() {
      if (this.eating) {
         mc.field_1690.field_1904.method_23481(this.previousUsePressed);
         InvUtil.select(this.previousSlot);
      }

      this.eating = false;
      this.used = false;
      this.ticks = 0;
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null) {
         if (this.cooldown > 0) {
            this.cooldown--;
         }

         if (this.eating) {
            this.ticks++;
            if (p.method_6115()) {
               this.used = true;
            }

            if ((!this.used || p.method_6115()) && this.ticks <= 60) {
               mc.field_1690.field_1904.method_23481(true);
            } else {
               this.finish();
               this.cooldown = 32;
            }
         } else if (this.cooldown <= 0 && !p.method_6115() && mc.field_1755 == null) {
            if (!(p.method_6032() + p.method_6067() > this.health.get())) {
               int slot = InvUtil.ensureHotbar(class_1802.field_8367);
               if (slot < 0) {
                  slot = InvUtil.ensureHotbar(class_1802.field_8463);
               }

               if (slot >= 0) {
                  this.previousSlot = InvUtil.selected();
                  this.previousUsePressed = mc.field_1690.field_1904.method_1434();
                  InvUtil.select(slot);
                  mc.field_1690.field_1904.method_23481(true);
                  this.eating = true;
               }
            }
         }
      }
   }
}

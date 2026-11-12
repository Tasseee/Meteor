package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1802;
import net.minecraft.class_746;

public final class AutoTotem extends Module {
   private final NumberSetting health = this.add(new NumberSetting("Health-Schwelle (0 = immer)", 0.0, 0.0, 20.0, 1.0));
   private final NumberSetting delay = this.add(new NumberSetting("Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private static final int CONFIRM_TIMEOUT_TICKS = 12;
   private int wait;
   private int confirmTicks;
   private boolean swapPending;
   private boolean offhandHadTotem;

   public AutoTotem() {
      super("AutoTotem", "Legt einen Totem mit Server-Bestaetigung in die Offhand.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.reset();
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldLeave() {
      this.reset();
   }

   private void reset() {
      this.wait = this.confirmTicks = 0;
      this.swapPending = false;
      this.offhandHadTotem = false;
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player != null && mc.field_1761 != null) {
         if (player.field_7512 == player.field_7498) {
            boolean hasTotem = player.method_6079().method_31574(class_1802.field_8288);
            if (hasTotem) {
               this.offhandHadTotem = true;
               this.swapPending = false;
               this.confirmTicks = 0;
               this.wait = 0;
            } else {
               if (this.offhandHadTotem) {
                  this.wait = 0;
                  this.confirmTicks = 0;
                  this.swapPending = false;
               }

               this.offhandHadTotem = false;
               if (this.swapPending) {
                  if (--this.confirmTicks <= 0) {
                     this.swapPending = false;
                     this.wait = Math.max(4, this.delay.getInt());
                  }
               } else if (this.wait > 0) {
                  this.wait--;
               } else if (!(this.health.get() > 0.0) || !(player.method_6032() + player.method_6067() > this.health.get())) {
                  int slot = InvUtil.findInventory(class_1802.field_8288);
                  if (slot >= 0 && InvUtil.swapWithOffhand(slot)) {
                     this.swapPending = true;
                     this.confirmTicks = 12;
                  }
               }
            }
         }
      }
   }
}

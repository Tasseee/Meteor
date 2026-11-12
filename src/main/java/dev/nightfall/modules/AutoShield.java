package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_746;

public final class AutoShield extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Erkennungsreichweite", 5.0, 2.0, 8.0, 0.5));
   private final NumberSetting holdTicks = this.add(new NumberSetting("Blockdauer (Ticks)", 7.0, 2.0, 15.0, 1.0));
   private final BoolSetting requireVisible = this.add(new BoolSetting("Nur sichtbare Gegner", false));
   private int ticksRemaining;
   private boolean ownsUseKey;
   private boolean previousUsePressed;

   public AutoShield() {
      super("AutoShield", "Hebt das ausgeruestete Schild bei einem nahen gegnerischen Schlag.", Category.COMBAT);
   }

   public void onAttackAnimation(class_1657 attacker) {
      class_746 player = mc.field_1724;
      if (this.isEnabled()
         && player != null
         && mc.field_1687 != null
         && attacker != null
         && attacker != player
         && attacker.method_5805()
         && !attacker.method_7325()
         && !CombatUtil.isFriend(attacker)) {
         if (!(player.method_5858(attacker) > this.range.get() * this.range.get())) {
            if (!this.requireVisible.get() || player.method_6057(attacker)) {
               if (this.hasShieldEquipped(player)) {
                  this.ticksRemaining = Math.max(this.ticksRemaining, this.holdTicks.getInt());
               }
            }
         }
      }
   }

   private boolean hasShieldEquipped(class_746 player) {
      return player.method_6047().method_31574(class_1802.field_8255) || player.method_6079().method_31574(class_1802.field_8255);
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player != null && mc.field_1690 != null && mc.field_1755 == null && this.hasShieldEquipped(player) && this.ticksRemaining > 0) {
         if (!this.ownsUseKey) {
            this.previousUsePressed = mc.field_1690.field_1904.method_1434();
            this.ownsUseKey = true;
         }

         mc.field_1690.field_1904.method_23481(true);
         this.ticksRemaining--;
         if (this.ticksRemaining <= 0) {
            this.release();
         }
      } else {
         this.release();
         this.ticksRemaining = 0;
      }
   }

   private void release() {
      if (this.ownsUseKey && mc.field_1690 != null) {
         mc.field_1690.field_1904.method_23481(this.previousUsePressed);
      }

      this.ownsUseKey = false;
   }

   @Override
   protected void onDisable() {
      this.ticksRemaining = 0;
      this.release();
   }

   @Override
   public void onWorldLeave() {
      this.ticksRemaining = 0;
      this.release();
   }
}

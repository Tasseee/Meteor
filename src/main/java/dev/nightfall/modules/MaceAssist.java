package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_746;

public class MaceAssist extends Module {
   private final NumberSetting minFall = this.add(new NumberSetting("Min. Fallhoehe", 1.5, 1.5, 20.0, 0.5));
   private final NumberSetting range = this.add(new NumberSetting("Angriffs-Reichweite", 3.2, 1.0, 6.0, 0.1));
   private final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbares Ziel", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch zur Mace", true));
   private final BoolSetting switchBack = this.add(new BoolSetting("Zurueckwechseln", true));
   private final BoolSetting autoAttack = this.add(new BoolSetting("Auto-Angriff", true));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));
   public double estimate;
   private int prevSlot = -1;

   public MaceAssist() {
      super("MaceAssist", "Mace-Smash: Auto-Switch und Angriff bei ausreichender Fallhoehe.", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.restoreSlot();
      this.estimate = 0.0;
   }

   @Override
   public void onWorldLeave() {
      this.restoreSlot();
      this.estimate = 0.0;
   }

   private void restoreSlot() {
      if (this.switchBack.get() && this.prevSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.prevSlot);
      }

      this.prevSlot = -1;
   }

   public static double smashDamage(double fall) {
      double bonus = fall <= 3.0 ? 4.0 * fall : (fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + (fall - 8.0));
      return 6.0 + bonus;
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      this.estimate = 0.0;
      if (p != null && mc.field_1755 == null && mc.field_1761 != null) {
         double fall = p.field_6017;
         if (!p.method_24828() && !(fall < this.minFall.get()) && !p.method_5799() && !p.method_6128()) {
            if (!p.method_6047().method_31574(class_1802.field_49814)) {
               if (!this.autoSwitch.get()) {
                  return;
               }

               int s = InvUtil.findHotbar(class_1802.field_49814);
               if (s < 0) {
                  return;
               }

               if (this.prevSlot < 0) {
                  this.prevSlot = InvUtil.selected();
               }

               InvUtil.select(s);
            }

            this.estimate = smashDamage(fall);
            if (this.autoAttack.get() && mc.field_1692 instanceof class_1309 le && le.method_5805()) {
               if (le instanceof class_1657 pe) {
                  if (!this.players.get() || CombatUtil.isFriend(pe)) {
                     return;
                  }
               } else if (!this.mobs.get()) {
                  return;
               }

               if (!(p.method_5739(le) > this.range.get())) {
                  if (!this.visibleOnly.get() || p.method_6057(le)) {
                     if (!(p.method_7261(0.5F) < 0.95F)) {
                        mc.field_1761.method_2918(p, le);
                        p.method_6104(class_1268.field_5808);
                     }
                  }
               }
            }
         } else {
            if (p.method_24828() && this.prevSlot >= 0 && this.switchBack.get()) {
               InvUtil.select(this.prevSlot);
               this.prevSlot = -1;
            }
         }
      }
   }
}

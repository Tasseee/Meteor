package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1309;
import net.minecraft.class_1311;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_3489;
import net.minecraft.class_746;

public class TriggerBot extends Module {
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting hostile = this.add(new BoolSetting("Feindliche Mobs", true));
   private final BoolSetting passive = this.add(new BoolSetting("Passive Mobs", false));
   private final BoolSetting cooldown = this.add(new BoolSetting("Cooldown pruefen", true));
   private final BoolSetting crit = this.add(new BoolSetting("Crit abwarten", false));
   private final BoolSetting weaponOnly = this.add(new BoolSetting("Nur Waffe", true));
   private final NumberSetting delay = this.add(new NumberSetting("Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private int wait;

   public TriggerBot() {
      super("TriggerBot", "Greift automatisch an, wenn das Fadenkreuz ein Ziel trifft.", Category.COMBAT);
   }

   @Override
   public void onWorldLeave() {
      this.wait = 0;
   }

   @Override
   protected void onDisable() {
      this.wait = 0;
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1755 == null && mc.field_1761 != null) {
         if (this.wait > 0) {
            this.wait--;
         } else if (mc.field_1692 instanceof class_1309 le && le.method_5805() && le != p) {
            if (le instanceof class_1657 pe) {
               if (!this.players.get() || CombatUtil.isFriend(pe)) {
                  return;
               }
            } else if (le.method_5864().method_5891() == class_1311.field_6302) {
               if (!this.hostile.get()) {
                  return;
               }
            } else if (!this.passive.get()) {
               return;
            }

            if (this.weaponOnly.get()) {
               class_1799 s = p.method_6047();
               if (!s.method_31573(class_3489.field_42611)
                  && !s.method_31573(class_3489.field_42612)
                  && !s.method_31574(class_1802.field_49814)
                  && !s.method_31574(class_1802.field_8547)) {
                  return;
               }
            }

            if (!this.cooldown.get() || !(p.method_7261(0.5F) < 0.95F)) {
               if (this.crit.get()) {
                  boolean c = !p.method_24828()
                     && p.field_6017 > 0.0
                     && !p.method_6101()
                     && !p.method_5799()
                     && !p.method_6059(class_1294.field_5919)
                     && !p.method_5765()
                     && !p.method_5624();
                  if (!c) {
                     return;
                  }
               }

               mc.field_1761.method_2918(p, le);
               p.method_6104(class_1268.field_5808);
               this.wait = this.delay.getInt();
            }
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_4969;
import net.minecraft.class_746;

public class AutoAnchor extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 8.0, 3.0, 12.0, 1.0));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting delay = this.add(new NumberSetting("Delay (Ticks)", 2.0, 1.0, 20.0, 1.0));
   private final NumberSetting maxSelf = this.add(new NumberSetting("Max. Eigenschaden", 16.0, 1.0, 20.0, 1.0));
   private int timer;

   @Override
   public void onWorldLeave() {
      this.timer = 0;
   }

   @Override
   protected void onDisable() {
      this.timer = 0;
   }

   public AutoAnchor() {
      super("AutoAnchor", "Setzt, laedt und zuendet Respawn-Anker ueber dem Gegner.", Category.COMBAT);
   }

   private void use(class_746 p, class_2338 pos, class_2350 face, class_243 hit) {
      mc.field_1761.method_2896(p, class_1268.field_5808, new class_3965(hit, face, pos, false));
      p.method_6104(class_1268.field_5808);
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && mc.field_1761 != null && mc.field_1755 == null) {
         if (!mc.field_1687.method_27983().method_29177().method_12832().equals("the_nether")) {
            if (this.timer > 0) {
               this.timer--;
            } else {
               class_1657 t = CombatUtil.nearestPlayer(this.targetRange.get());
               if (t != null) {
                  class_2338 cand = t.method_24515().method_10086(2);
                  class_243 center = new class_243(cand.method_10263() + 0.5, cand.method_10264() + 1.0, cand.method_10260() + 0.5);
                  if (!(p.method_33571().method_1022(center) > this.range.get())) {
                     double self = CombatUtil.explosionDamage(center, p, 5.0);
                     class_2680 st = mc.field_1687.method_8320(cand);
                     if (!st.method_27852(class_2246.field_23152)) {
                        if (st.method_45474()) {
                           int a = InvUtil.ensureHotbar(class_1802.field_23141);
                           if (a >= 0) {
                              for (class_2350 d : class_2350.values()) {
                                 class_2338 nb = cand.method_10093(d);
                                 if (!mc.field_1687.method_8320(nb).method_26220(mc.field_1687, nb).method_1110()) {
                                    class_2350 face = d.method_10153();
                                    class_243 hit = class_243.method_24953(nb)
                                       .method_1031(face.method_10148() * 0.5, face.method_10164() * 0.5, face.method_10165() * 0.5);
                                    InvUtil.select(a);
                                    this.use(p, nb, face, hit);
                                    this.timer = this.delay.getInt();
                                    return;
                                 }
                              }
                           }
                        }
                     } else if (!(self > this.maxSelf.get()) && !(self >= CombatUtil.effectiveHealth(p) - 1.0)) {
                        int charges = (Integer)st.method_11654(class_4969.field_23153);
                        if (charges < 1) {
                           int g = InvUtil.ensureHotbar(class_1802.field_8801);
                           if (g < 0) {
                              return;
                           }

                           InvUtil.select(g);
                        } else {
                           int other = -1;

                           for (int i = 0; i < 9; i++) {
                              if (!InvUtil.hotbarStack(i).method_31574(class_1802.field_8801)) {
                                 other = i;
                                 break;
                              }
                           }

                           if (other < 0) {
                              return;
                           }

                           InvUtil.select(other);
                        }

                        this.use(p, cand, class_2350.field_11036, center);
                        this.timer = this.delay.getInt();
                     }
                  }
               }
            }
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_746;

public class TrapCrit extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 3.0, 1.0, 6.0, 0.1));
   private final BoolSetting onlyTrapped = this.add(new BoolSetting("Nur eingeschlossene", true));
   private final BoolSetting cooldown = this.add(new BoolSetting("Cooldown pruefen", true));
   private final BoolSetting crit = this.add(new BoolSetting("Crit abwarten", true));
   private final BoolSetting needKey = this.add(new BoolSetting("Attack-Taste noetig", true));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));
   public class_1309 target;
   public boolean trapped;
   public boolean critReady;
   public float cooldownProgress;

   public TrapCrit() {
      super("TrapCrit", "Treffer-Assistent fuer eingeschlossene Ziele (Crit/Cooldown).", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.resetState();
   }

   @Override
   public void onWorldLeave() {
      this.resetState();
   }

   private void resetState() {
      this.target = null;
      this.trapped = false;
      this.critReady = false;
      this.cooldownProgress = 0.0F;
   }

   @Override
   public void onTick() {
      this.resetState();
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && mc.field_1755 == null && mc.field_1761 != null) {
         if (mc.field_1692 instanceof class_1309 le && le.method_5805() && le != p) {
            boolean isPlayer = le instanceof class_1657;
            if (isPlayer ? this.players.get() : this.mobs.get()) {
               if (!(p.method_5739(le) > this.range.get()) && p.method_6057(le)) {
                  this.trapped = this.enclosed(le);
                  if (!this.onlyTrapped.get() || this.trapped) {
                     this.target = le;
                     this.cooldownProgress = p.method_7261(0.5F);
                     this.critReady = !p.method_24828()
                        && p.field_6017 > 0.0
                        && !p.method_6101()
                        && !p.method_5799()
                        && !p.method_6059(class_1294.field_5919)
                        && !p.method_5765()
                        && !p.method_5624();
                     if (!this.needKey.get() || mc.field_1690.field_1886.method_1434()) {
                        if (!this.cooldown.get() || !(this.cooldownProgress < 0.95F)) {
                           if (!this.crit.get() || this.critReady) {
                              mc.field_1761.method_2918(p, le);
                              p.method_6104(class_1268.field_5808);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean solid(class_2338 pos) {
      return !mc.field_1687.method_8320(pos).method_26220(mc.field_1687, pos).method_1110();
   }

   private boolean enclosed(class_1309 e) {
      class_2338 feet = e.method_24515();
      class_2338 head = feet.method_10084();

      for (class_2350 d : class_2350.values()) {
         if (d.method_10166().method_10179() && (!this.solid(feet.method_10093(d)) || !this.solid(head.method_10093(d)))) {
            return false;
         }
      }

      return this.solid(head.method_10084());
   }
}

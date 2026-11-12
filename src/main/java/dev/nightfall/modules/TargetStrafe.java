package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.class_1657;
import net.minecraft.class_243;

public final class TargetStrafe extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 5.0, 2.0, 8.0, 0.5));
   private final NumberSetting speed = this.add(new NumberSetting("Strafe-Speed", 0.22, 0.08, 0.45, 0.01));
   private final BoolSetting clockwise = this.add(new BoolSetting("Im Uhrzeigersinn", true));
   private final BoolSetting requireJump = this.add(new BoolSetting("Nur mit Springen", false));

   public TargetStrafe() {
      super("TargetStrafe", "Kreist um den naechsten Gegner, solange Bewegungstasten gedrueckt sind.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && !mc.field_1724.method_5765() && mc.field_1755 == null) {
         if (mc.field_1690.field_1894.method_1434()
            || mc.field_1690.field_1881.method_1434()
            || mc.field_1690.field_1913.method_1434()
            || mc.field_1690.field_1849.method_1434()) {
            if (!this.requireJump.get() || mc.field_1690.field_1903.method_1434()) {
               class_1657 target = CombatUtil.nearestPlayer(this.range.get());
               if (target != null) {
                  class_243 away = mc.field_1724.method_73189().method_1020(target.method_73189());
                  double len = Math.sqrt(away.field_1352 * away.field_1352 + away.field_1350 * away.field_1350);
                  if (!(len < 0.001)) {
                     double sign = this.clockwise.get() ? 1.0 : -1.0;
                     double tx = -away.field_1350 / len * sign;
                     double tz = away.field_1352 / len * sign;
                     double radial = Math.max(-0.08, Math.min(0.08, (len - 2.7) * 0.035));
                     mc.field_1724
                        .method_18800(
                           tx * this.speed.get() + away.field_1352 / len * radial,
                           mc.field_1724.method_18798().field_1351,
                           tz * this.speed.get() + away.field_1350 / len * radial
                        );
                  }
               }
            }
         }
      }
   }
}

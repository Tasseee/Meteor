package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.Locale;
import net.minecraft.class_1309;
import net.minecraft.class_332;

public class CombatStatusHud extends HudModule {
   public CombatStatusHud() {
      super("CombatStatus", "Ziel, Distanz, Cooldown und Crit-Status.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw / 2 + 12, sh / 2 + 12};
   }

   @Override
   protected void draw(class_332 ctx) {
      TrapCrit trapCrit = ModuleManager.get(TrapCrit.class);
      KillAura killAura = ModuleManager.get(KillAura.class);
      class_1309 target = trapCrit.target != null ? trapCrit.target : killAura.target;
      double mace = ModuleManager.get(MaceAssist.class).estimate;
      String idle = trapCrit.isEnabled() ? "Kein TrapCrit-Ziel" : (killAura.isEnabled() ? "Kein KillAura-Ziel" : "Kein Ziel");
      String name = target == null ? idle : target.method_5477().getString();
      String detail = target != null && mc.field_1724 != null
         ? String.format(
            Locale.ROOT,
            "%.1f m  %s  %s",
            mc.field_1724.method_5739(target),
            trapCrit.target == target ? (trapCrit.trapped ? "TRAP" : "frei") : "AURA",
            trapCrit.target == target && trapCrit.critReady ? "CRIT" : "-"
         )
         : "";
      this.w = Math.max(126, Math.min(180, Math.max(mc.field_1772.method_1727(name) + 12, mc.field_1772.method_1727(detail) + 12)));
      this.h = target == null ? (mace > 0.0 ? 30 : 18) : (mace > 0.0 ? 46 : 34);
      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, name, 6, 3, target == null ? Theme.TEXT_DIM : Theme.TEXT, false);
      if (target == null) {
         if (mace > 0.0) {
            ctx.method_51433(mc.field_1772, String.format(Locale.ROOT, "Mace ~%.0f Schaden", mace), 6, 15, -11654, false);
         }
      } else {
         ctx.method_51433(mc.field_1772, detail, 6, 13, Theme.TEXT_DIM, false);
         float cooldown = trapCrit.target == target ? trapCrit.cooldownProgress : mc.field_1724.method_7261(0.5F);
         int bw = (int)((this.w - 12) * Math.min(1.0F, cooldown));
         ctx.method_25294(6, 25, this.w - 6, 29, -14013900);
         ctx.method_25294(6, 25, 6 + bw, 29, Theme.ACCENT);
         if (mace > 0.0) {
            ctx.method_51433(mc.field_1772, String.format(Locale.ROOT, "Mace ~%.0f Schaden", mace), 6, 33, -11654, false);
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_332;
import net.minecraft.class_640;

public final class AntiVanishHints extends Module {
   public AntiVanishHints() {
      super("AntiVanishHints", "Unverbindliche Client-Hinweise; Abwesenheit beweist keinen Vanish.", Category.MISC);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.method_1562() != null) {
         Set<String> tab = new HashSet<>();

         for (class_640 entry : mc.method_1562().method_2880()) {
            if (entry.method_2966() != null && entry.method_2966().name() != null) {
               tab.add(entry.method_2966().name().toLowerCase(Locale.ROOT));
            }
         }

         Set<String> loaded = new HashSet<>();
         mc.field_1687.method_18456().forEach(p -> loaded.add(p.method_7334().name().toLowerCase(Locale.ROOT)));
         StaffWatch staff = ModuleManager.get(StaffWatch.class);
         if (!staff.staffNames.get().isEmpty()) {
            int x = 8;
            int y = 90;
            ctx.method_51433(mc.field_1772, "AntiVanish · Beobachtbare Hinweise", x, y, Theme.ACCENT, true);
            y += 12;
            ctx.method_51433(mc.field_1772, "Abwesenheit ist kein Vanish-Beweis", x, y, -14249, true);
            y += 12;
            int shown = 0;

            for (String name : staff.staffNames.get()) {
               boolean inTab = tab.contains(name);
               boolean inWorld = loaded.contains(name);
               String signal;
               int color;
               if (inTab && inWorld) {
                  signal = "Tab + Entity sichtbar";
                  color = -9508952;
               } else if (inTab) {
                  signal = "Tab da, Entity nicht geladen";
                  color = -14249;
               } else if (inWorld) {
                  signal = "Entity da, Tab-Eintrag fehlt";
                  color = -14249;
               } else {
                  signal = "keine sichtbaren Daten · unklar";
                  color = -4737080;
               }

               ctx.method_51433(mc.field_1772, name + " · " + signal, x, y, color, false);
               y += 11;
               if (++shown >= 5) {
                  break;
               }
            }
         }
      }
   }
}

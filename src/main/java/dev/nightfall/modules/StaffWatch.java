package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.util.Theme;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_332;
import net.minecraft.class_640;
import net.minecraft.class_742;

public final class StaffWatch extends Module {
   public final ListSetting staffNames = this.add(new ListSetting("Beobachtete Namen"));

   public StaffWatch() {
      super("StaffWatch", "Clientseitige Präsenzhinweise für selbst eingetragene Namen; keine Vanish-Bestätigung.", Category.MISC);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null && !this.staffNames.get().isEmpty()) {
         Set<String> tab = new HashSet<>();
         if (mc.method_1562() != null) {
            for (class_640 entry : mc.method_1562().method_2880()) {
               if (entry.method_2966() != null && entry.method_2966().name() != null) {
                  tab.add(entry.method_2966().name().toLowerCase(Locale.ROOT));
               }
            }
         }

         Set<String> loaded = new HashSet<>();

         for (class_742 player : mc.field_1687.method_18456()) {
            loaded.add(player.method_7334().name().toLowerCase(Locale.ROOT));
         }

         int x = 8;
         int y = 44;
         ctx.method_51433(mc.field_1772, "StaffWatch · Client-Hinweise", x, y, Theme.ACCENT, true);
         y += 12;
         int count = 0;

         for (String name : this.staffNames.get()) {
            if (count++ >= 8) {
               break;
            }

            boolean inWorld = loaded.contains(name);
            boolean inTab = tab.contains(name);
            String status;
            int color;
            if (inWorld && inTab) {
               status = "Entity geladen + Tab-Eintrag";
               color = -9508952;
            } else if (inWorld) {
               status = "Entity geladen · kein Tab-Eintrag";
               color = -14249;
            } else if (inTab) {
               status = "Tab-Eintrag · Entity nicht geladen";
               color = -14249;
            } else {
               status = "weder Entity noch Tab · unklar";
               color = -4737080;
            }

            ctx.method_51433(mc.field_1772, name + " — " + status, x, y, color, true);
            y += 11;
         }
      }
   }
}

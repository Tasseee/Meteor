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
   private static final int MAX_VISIBLE_NAMES = 8;
   private static final int ROW_HEIGHT = 15;
   private static final int COLOR_VISIBLE = 0xFF72D69A;
   private static final int COLOR_PARTIAL = 0xFFFFC46B;
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   public final ListSetting staffNames = this.add(new ListSetting("Beobachtete Namen"));
   private final Set<String> tabNames = new HashSet<>();
   private final Set<String> loadedNames = new HashSet<>();

   public StaffWatch() {
      super("StaffWatch", "Clientseitige Hinweise zu manuell beobachteten Namen; keine Staff- oder Vanish-Erkennung.", Category.MISC);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || mc.field_1687 == null || this.staffNames.get().isEmpty()) {
         return;
      }

      this.tabNames.clear();
      this.loadedNames.clear();

      var network = mc.method_1562();
      boolean tabAvailable = network != null;
      if (tabAvailable) {
         for (class_640 entry : network.method_2880()) {
            if (entry == null || entry.method_2966() == null) {
               continue;
            }

            String name = entry.method_2966().name();
            if (name != null && !name.isBlank()) {
               this.tabNames.add(name.toLowerCase(Locale.ROOT));
            }
         }
      }

      for (class_742 player : mc.field_1687.method_18456()) {
         if (player == null || player.method_7334() == null) {
            continue;
         }

         String name = player.method_7334().name();
         if (name != null && !name.isBlank()) {
            this.loadedNames.add(name.toLowerCase(Locale.ROOT));
         }
      }

      int shown = 0;
      int nameWidth = 0;
      int statusWidth = 0;
      for (String name : this.staffNames.get()) {
         if (name == null || name.isBlank() || shown >= MAX_VISIBLE_NAMES) {
            continue;
         }

         shown++;
         nameWidth = Math.max(nameWidth, mc.field_1772.method_1727(name));
         statusWidth = Math.max(statusWidth, mc.field_1772.method_1727(this.status(name, tabAvailable)));
      }

      if (shown == 0) {
         return;
      }

      String countLabel = shown + "/" + this.staffNames.get().size();
      String legend = "TAB = Spielerliste · WELT = geladene Entity";
      String disclaimer = "Nur Clientdaten · kein Vanish-Nachweis";
      int contentWidth = 18 + nameWidth + 14 + statusWidth;
      int panelWidth = Math.max(
         210,
         Math.max(
            contentWidth,
            Math.max(mc.field_1772.method_1727(legend), mc.field_1772.method_1727(disclaimer))
         ) + 18
      );
      panelWidth = Math.max(160, Math.min(panelWidth, ctx.method_51421() - 16));

      int x = 8;
      if (x + panelWidth > ctx.method_51421() - 8) {
         x = Math.max(4, ctx.method_51421() - panelWidth - 8);
      }

      int y = 42;
      int headerHeight = 23;
      int footerHeight = 27;
      int panelHeight = headerHeight + shown * ROW_HEIGHT + footerHeight;
      ctx.method_25294(x, y, x + panelWidth, y + panelHeight, Theme.HUD_PANEL);
      ctx.method_25294(x, y, x + panelWidth, y + 2, Theme.HUD_EDGE);
      ctx.method_25294(x, y, x + 3, y + panelHeight, Theme.HUD_EDGE);
      ctx.method_25294(x + 3, y + 2, x + panelWidth - 1, y + headerHeight, Theme.withAlpha(Theme.ACCENT, 30));

      ctx.method_51433(mc.field_1772, "STAFFWATCH", x + 10, y + 7, Theme.ACCENT, true);
      int countX = x + panelWidth - 9 - mc.field_1772.method_1727(countLabel);
      ctx.method_51433(mc.field_1772, countLabel, countX, y + 7, Theme.TEXT_DIM, true);

      int rowY = y + headerHeight;
      int index = 0;
      for (String name : this.staffNames.get()) {
         if (name == null || name.isBlank()) {
            continue;
         }
         if (index >= MAX_VISIBLE_NAMES) {
            break;
         }

         boolean inTab = this.tabNames.contains(name);
         boolean inWorld = this.loadedNames.contains(name);
         String status = this.status(name, tabAvailable);
         int color = this.statusColor(inTab, inWorld, tabAvailable);

         if ((index & 1) == 0) {
            ctx.method_25294(x + 4, rowY, x + panelWidth - 4, rowY + ROW_HEIGHT, Theme.withAlpha(Theme.TEXT, 9));
         }
         ctx.method_25294(x + 5, rowY + 3, x + 7, rowY + ROW_HEIGHT - 3, color);
         ctx.method_51433(mc.field_1772, name, x + 11, rowY + 3, Theme.TEXT, true);
         ctx.method_51433(mc.field_1772, status, x + 11 + nameWidth + 8, rowY + 3, color, true);

         rowY += ROW_HEIGHT;
         index++;
      }

      int footerY = y + headerHeight + shown * ROW_HEIGHT;
      ctx.method_25294(x + 8, footerY, x + panelWidth - 8, footerY + 1, Theme.withAlpha(Theme.TEXT_DIM, 75));
      ctx.method_51433(mc.field_1772, legend, x + 9, footerY + 4, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, disclaimer, x + 9, footerY + 15, Theme.TEXT_DIM, false);
      int hidden = Math.max(0, this.staffNames.get().size() - shown);
      if (hidden > 0) {
         String more = "+" + hidden;
         int moreX = x + panelWidth - 9 - mc.field_1772.method_1727(more);
         ctx.method_51433(mc.field_1772, more, moreX, footerY + 4, Theme.ACCENT, true);
      }
   }

   private String status(String name, boolean tabAvailable) {
      boolean inTab = this.tabNames.contains(name);
      boolean inWorld = this.loadedNames.contains(name);
      if (!tabAvailable) {
         return inWorld ? "WELT · TAB N/A" : "TAB N/A · KEINE ENTITY";
      }
      if (inTab && inWorld) {
         return "TAB + WELT";
      }
      if (inTab) {
         return "NUR TAB";
      }
      if (inWorld) {
         return "NUR WELT";
      }
      return "NICHT SICHTBAR";
   }

   private int statusColor(boolean inTab, boolean inWorld, boolean tabAvailable) {
      if (!tabAvailable) {
         return inWorld ? COLOR_PARTIAL : COLOR_UNKNOWN;
      }
      if (inTab && inWorld) {
         return COLOR_VISIBLE;
      }
      if (inTab || inWorld) {
         return COLOR_PARTIAL;
      }
      return COLOR_UNKNOWN;
   }
}

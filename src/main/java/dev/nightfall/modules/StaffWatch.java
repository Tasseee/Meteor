package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.util.Theme;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_332;
import net.minecraft.class_640;
import net.minecraft.class_742;

public final class StaffWatch extends HudModule {
   private static final int MAX_VISIBLE_NAMES = 8;
   private static final int ROW_HEIGHT = 15;
   private static final int COLOR_VISIBLE = 0xFF72D69A;
   private static final int COLOR_PARTIAL = 0xFFFFC46B;
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   public final ListSetting staffNames = this.add(new ListSetting("Beobachtete Namen"));
   private final Set<String> tabNames = new HashSet<>();
   private final Set<String> loadedNames = new HashSet<>();

   public StaffWatch() {
      super("StaffWatch", "Verschiebbares HUD für manuell eingetragene Namen; nur clientseitige Präsenzhinweise.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{8, 42};
   }

   @Override
   protected void draw(class_332 ctx) {
      boolean preview = mc.field_1755 instanceof HudEditorScreen;
      Set<String> names = this.staffNames.get();
      boolean hasWorld = mc.field_1724 != null && mc.field_1687 != null;
      if (!preview && (!hasWorld || names.isEmpty())) {
         this.w = 210;
         this.h = 50;
         return;
      }

      this.tabNames.clear();
      this.loadedNames.clear();
      boolean tabAvailable = false;

      if (hasWorld) {
         var network = mc.method_1562();
         tabAvailable = network != null;
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
      }

      boolean emptyPreview = names.isEmpty();
      int shown = emptyPreview ? (preview ? 1 : 0) : Math.min(MAX_VISIBLE_NAMES, names.size());
      if (shown == 0) {
         this.w = 210;
         this.h = 50;
         return;
      }

      String emptyLabel = "Keine Namen eingetragen";
      int nameWidth = emptyPreview ? mc.field_1772.method_1727(emptyLabel) : 0;
      int statusWidth = mc.field_1772.method_1727(emptyPreview ? "HINWEIS" : preview ? "VORSCHAU" : "TAB + WELT");
      int measured = 0;
      if (!emptyPreview) {
         for (String name : names) {
            if (name == null || name.isBlank() || measured >= MAX_VISIBLE_NAMES) {
               continue;
            }

            measured++;
            nameWidth = Math.max(nameWidth, mc.field_1772.method_1727(name));
            String status = preview ? "VORSCHAU" : this.status(name, tabAvailable);
            statusWidth = Math.max(statusWidth, mc.field_1772.method_1727(status));
         }
      }

      String countLabel = preview && emptyPreview ? "0/0" : shown + "/" + names.size();
      String legend = "TAB = Spielerliste · WELT = geladene Entity";
      String disclaimer = "Nur Clientdaten · kein Vanish-Nachweis";
      int contentWidth = 32 + nameWidth + statusWidth;
      int panelWidth = Math.max(
         210,
         Math.max(
            contentWidth,
            Math.max(mc.field_1772.method_1727(legend), mc.field_1772.method_1727(disclaimer))
         ) + 18
      );
      panelWidth = Math.min(panelWidth, Math.max(1, ctx.method_51421() - 16));

      this.w = panelWidth;
      this.h = 23 + shown * ROW_HEIGHT + 27;
      int x = 0;
      int y = 0;
      ctx.method_25294(x, y, x + this.w, y + this.h, Theme.HUD_PANEL);
      ctx.method_25294(x, y, x + this.w, y + 2, Theme.HUD_EDGE);
      ctx.method_25294(x, y, x + 3, y + this.h, Theme.HUD_EDGE);
      ctx.method_25294(x + 3, y + 2, x + this.w - 1, y + 23, Theme.withAlpha(Theme.ACCENT, 30));

      ctx.method_51433(mc.field_1772, "STAFFWATCH", x + 10, y + 7, Theme.ACCENT, true);
      int countX = x + this.w - 9 - mc.field_1772.method_1727(countLabel);
      ctx.method_51433(mc.field_1772, countLabel, countX, y + 7, Theme.TEXT_DIM, true);

      int rowY = y + 23;
      int index = 0;
      if (emptyPreview) {
         this.drawRow(ctx, emptyLabel, "HINWEIS", COLOR_UNKNOWN, x, rowY, nameWidth, panelWidth);
      } else {
         for (String name : names) {
            if (name == null || name.isBlank()) {
               continue;
            }
            if (index >= MAX_VISIBLE_NAMES) {
               break;
            }

            boolean inTab = this.tabNames.contains(name);
            boolean inWorld = this.loadedNames.contains(name);
            String status = preview ? "VORSCHAU" : this.status(name, tabAvailable);
            int color = preview ? COLOR_UNKNOWN : this.statusColor(inTab, inWorld, tabAvailable);
            this.drawRow(ctx, name, status, color, x, rowY, nameWidth, panelWidth);
            rowY += ROW_HEIGHT;
            index++;
         }
      }

      int footerY = y + 23 + shown * ROW_HEIGHT;
      ctx.method_25294(x + 8, footerY, x + this.w - 8, footerY + 1, Theme.withAlpha(Theme.TEXT_DIM, 75));
      ctx.method_51433(mc.field_1772, legend, x + 9, footerY + 4, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, disclaimer, x + 9, footerY + 15, Theme.TEXT_DIM, false);
   }

   private void drawRow(class_332 ctx, String name, String status, int color, int x, int y, int nameWidth, int panelWidth) {
      int indexColor = color;
      ctx.method_25294(x + 4, y, x + panelWidth - 4, y + ROW_HEIGHT, Theme.withAlpha(Theme.TEXT, 9));
      ctx.method_25294(x + 5, y + 3, x + 7, y + ROW_HEIGHT - 3, indexColor);
      ctx.method_51433(mc.field_1772, name, x + 11, y + 3, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, status, x + 11 + nameWidth + 8, y + 3, indexColor, true);
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

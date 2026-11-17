package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.class_332;

public final class StaffWatch extends HudModule {
   private static final int COLOR_VISIBLE = 0xFF72D69A;
   private static final int COLOR_PARTIAL = 0xFFFFC46B;
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   public final ListSetting staffNames = this.add(new ListSetting("Beobachtete Namen"));
   private final NumberSetting maxRows = this.add(new NumberSetting("Max. HUD-Zeilen", 8.0, 3.0, 12.0, 1.0));
   private final BoolSetting compactMode = this.add(new BoolSetting("Kompaktes HUD", false));
   private final BoolSetting sortVisibleFirst = this.add(new BoolSetting("Sichtbare Treffer zuerst", true));
   private final BoolSetting showLegend = this.add(new BoolSetting("Legende anzeigen", true));

   public StaffWatch() {
      super("StaffWatch", "Verschiebbares HUD für manuell eingetragene Namen; nur clientseitige Präsenzhinweise.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{8, 42};
   }

   @Override
   protected void onEnable() {
   }

   @Override
   protected void onDisable() {
   }

   @Override
   public void onWorldLeave() {
      StaffPresence.reset();
   }

   @Override
   public void onTick() {
      // Presence is refreshed once centrally by ModuleManager.
   }

   @Override
   protected void draw(class_332 ctx) {
      boolean preview = mc.field_1755 instanceof HudEditorScreen;
      Set<String> names = this.staffNames.get();
      List<String> orderedNames = this.orderedNames(names);
      boolean hasWorld = mc.field_1724 != null && mc.field_1687 != null;
      if (!preview && (!hasWorld || orderedNames.isEmpty())) {
         this.w = 210;
         this.h = 50;
         return;
      }

      boolean emptyPreview = orderedNames.isEmpty();
      int rowHeight = this.compactMode.get() ? 12 : 15;
      int headerHeight = this.compactMode.get() ? 20 : 23;
      int footerHeight = this.showLegend.get() ? 27 : 0;
      int shown = emptyPreview ? 1 : Math.min(this.maxRows.getInt(), orderedNames.size());
      String emptyLabel = "Keine Namen eingetragen";
      int nameWidth = emptyPreview ? mc.field_1772.method_1727(emptyLabel) : 0;
      int statusWidth = mc.field_1772.method_1727(emptyPreview ? "HINWEIS" : preview ? "VORSCHAU" : "TAB + WELT");

      for (int i = 0; i < shown && !emptyPreview; i++) {
         String name = orderedNames.get(i);
         nameWidth = Math.max(nameWidth, mc.field_1772.method_1727(StaffPresence.displayName(name)));
         String status = preview ? "VORSCHAU" : this.status(name, StaffPresence.tabAvailable());
         statusWidth = Math.max(statusWidth, mc.field_1772.method_1727(status));
      }

      String countLabel = preview && emptyPreview ? "0/0" : shown + "/" + orderedNames.size();
      String legend = "TAB = Spielerliste (Netzwerk moeglich) · WELT = geladen";
      String disclaimer = "TAB allein bestaetigt keinen Unterserver";
      int contentWidth = 32 + nameWidth + statusWidth;
      int footerWidth = this.showLegend.get()
         ? Math.max(mc.field_1772.method_1727(legend), mc.field_1772.method_1727(disclaimer))
         : 0;
      int panelWidth = Math.max(210, Math.max(contentWidth, footerWidth) + 18);
      float displayScale = Math.max(0.5F, this.scale());
      int maxPanelWidth = Math.max(1, (int)((ctx.method_51421() - 16) / displayScale));
      panelWidth = Math.min(panelWidth, maxPanelWidth);

      this.w = panelWidth;
      this.h = headerHeight + shown * rowHeight + footerHeight;
      int x = 0;
      int y = 0;
      ctx.method_25294(x, y, x + this.w, y + this.h, Theme.HUD_PANEL);
      ctx.method_25294(x, y, x + this.w, y + 2, Theme.HUD_EDGE);
      ctx.method_25294(x, y, x + 3, y + this.h, Theme.HUD_EDGE);
      ctx.method_25294(x + 3, y + 2, x + this.w - 1, y + headerHeight, Theme.withAlpha(Theme.ACCENT, 34));

      ctx.method_51433(mc.field_1772, "STAFFWATCH", x + 10, y + (this.compactMode.get() ? 5 : 7), Theme.ACCENT, true);
      int countX = x + this.w - 9 - mc.field_1772.method_1727(countLabel);
      ctx.method_51433(mc.field_1772, countLabel, countX, y + (this.compactMode.get() ? 5 : 7), Theme.TEXT_DIM, true);

      int rowY = y + headerHeight;
      if (emptyPreview) {
         this.drawRow(ctx, emptyLabel, "HINWEIS", COLOR_UNKNOWN, x, rowY, nameWidth, rowHeight);
      } else {
         for (int i = 0; i < shown; i++) {
            String name = orderedNames.get(i);
            boolean inTab = StaffPresence.inTab(name);
            boolean inWorld = StaffPresence.loadedInWorld(name);
            String status = preview ? "VORSCHAU" : this.status(name, StaffPresence.tabAvailable());
            int color = preview ? COLOR_UNKNOWN : this.statusColor(inTab, inWorld, StaffPresence.tabAvailable());
            this.drawRow(ctx, this.displayName(name), status, color, x, rowY, nameWidth, rowHeight);
            rowY += rowHeight;
         }
      }

      if (this.showLegend.get()) {
         int footerY = y + headerHeight + shown * rowHeight;
         ctx.method_25294(x + 8, footerY, x + this.w - 8, footerY + 1, Theme.withAlpha(Theme.TEXT_DIM, 75));
         ctx.method_51433(mc.field_1772, legend, x + 9, footerY + 4, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, disclaimer, x + 9, footerY + 15, Theme.TEXT_DIM, false);
      }
   }

   private List<String> orderedNames(Set<String> names) {
      List<String> result = new ArrayList<>();
      for (String name : names) {
         if (name != null && !name.isBlank()) {
            result.add(name);
         }
      }

      if (this.sortVisibleFirst.get()) {
         result.sort(Comparator.comparingInt(StaffPresence::rank).thenComparing(String.CASE_INSENSITIVE_ORDER));
      }
      return result;
   }

   private void drawRow(class_332 ctx, String name, String status, int color, int x, int y, int nameWidth, int rowHeight) {
      ctx.method_25294(x + 4, y, x + this.w - 4, y + rowHeight, Theme.withAlpha(Theme.TEXT, 9));
      ctx.method_25294(x + 5, y + 3, x + 7, y + rowHeight - 3, color);
      int textY = y + (rowHeight == 12 ? 2 : 3);
      ctx.method_51433(mc.field_1772, name, x + 11, textY, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, status, x + 11 + nameWidth + 8, textY, color, true);
   }

   private String status(String name, boolean hasTab) {
      boolean inTab = StaffPresence.inTab(name);
      boolean inWorld = StaffPresence.loadedInWorld(name);
      if (!hasTab) {
         return inWorld ? "WELT · TAB N/A" : "TAB N/A · KEINE ENTITY";
      }
      if (inTab && inWorld) return "TAB + WELT";
      if (inTab) return "TAB/NETZ";
      if (inWorld) return "NUR WELT";
      return "KEINE QUELLE";
   }

   private int statusColor(boolean inTab, boolean inWorld, boolean hasTab) {
      if (!hasTab) {
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

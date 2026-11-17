package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import java.util.Set;
import net.minecraft.class_332;

public final class AntiVanishHints extends HudModule {
   private static final int COLOR_VISIBLE = 0xFF72D69A;
   private static final int COLOR_PARTIAL = 0xFFFFC46B;
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   private final NumberSetting maxRows = this.add(new NumberSetting("Max. HUD-Zeilen", 5.0, 3.0, 10.0, 1.0));
   private final BoolSetting compactMode = this.add(new BoolSetting("Kompaktes HUD", false));

   public AntiVanishHints() {
      super("AntiVanishHints", "Verschiebbares HUD für clientseitige Präsenzhinweise; Abwesenheit beweist keinen Vanish.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{8, 90};
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
      StaffWatch staff = ModuleManager.get(StaffWatch.class);
      if (staff == null || staff.isEnabled()) {
         this.w = 0;
         this.h = 0;
         return;
      }

      Set<String> names = staff.staffNames.get();
      boolean hasWorld = mc.field_1724 != null && mc.field_1687 != null;
      if (!preview && (!hasWorld || names.isEmpty())) {
         this.w = 0;
         this.h = 0;
         return;
      }

      boolean emptyPreview = names.isEmpty();
      int rowHeight = this.compactMode.get() ? 12 : 15;
      int headerHeight = this.compactMode.get() ? 20 : 23;
      int footerHeight = 27;
      int shown = emptyPreview ? 1 : Math.min(this.maxRows.getInt(), names.size());
      String emptyLabel = "Keine Namen eingetragen";
      int nameWidth = emptyPreview ? mc.field_1772.method_1727(emptyLabel) : 0;
      int statusWidth = mc.field_1772.method_1727(emptyPreview ? "HINWEIS" : "TAB + WELT");

      int measured = 0;
      for (String name : names) {
         if (name == null || name.isBlank() || measured >= shown) {
            continue;
         }
         measured++;
         nameWidth = Math.max(nameWidth, mc.field_1772.method_1727(name));
         String status = preview ? "VORSCHAU" : this.status(name);
         statusWidth = Math.max(statusWidth, mc.field_1772.method_1727(status));
      }

      String countLabel = preview && emptyPreview ? "0/0" : shown + "/" + names.size();
      String legend = "TAB = gelisteter Profil-Eintrag · WELT = Entity geladen";
      String disclaimer = "Abwesenheit ist kein Vanish-Beweis";
      int contentWidth = 32 + nameWidth + statusWidth;
      int panelWidth = Math.max(210, Math.max(contentWidth, Math.max(mc.field_1772.method_1727(legend), mc.field_1772.method_1727(disclaimer))) + 18);
      float displayScale = Math.max(0.5F, this.scale());
      int maxPanelWidth = Math.max(1, (int)((ctx.method_51421() - 16) / displayScale));
      panelWidth = Math.min(panelWidth, maxPanelWidth);

      this.w = panelWidth;
      this.h = headerHeight + shown * rowHeight + footerHeight;
      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, this.w, 2, Theme.HUD_EDGE);
      ctx.method_25294(0, 0, 3, this.h, Theme.HUD_EDGE);
      ctx.method_25294(3, 2, this.w - 1, headerHeight, Theme.withAlpha(Theme.ACCENT, 34));
      ctx.method_51433(mc.field_1772, "ANTIVANISH · HINWEISE", 10, this.compactMode.get() ? 5 : 7, Theme.ACCENT, true);
      int countX = this.w - 9 - mc.field_1772.method_1727(countLabel);
      ctx.method_51433(mc.field_1772, countLabel, countX, this.compactMode.get() ? 5 : 7, Theme.TEXT_DIM, true);

      int y = headerHeight;
      if (emptyPreview) {
         this.drawRow(ctx, emptyLabel, "HINWEIS", COLOR_UNKNOWN, y, rowHeight);
      } else {
         int index = 0;
         for (String name : names) {
            if (name == null || name.isBlank() || index >= shown) {
               continue;
            }
            String status = preview ? "VORSCHAU" : this.status(name);
            int color = preview ? COLOR_UNKNOWN : this.statusColor(name);
            this.drawRow(ctx, StaffPresence.displayName(name), status, color, y, rowHeight);
            y += rowHeight;
            index++;
         }
      }

      int footerY = headerHeight + shown * rowHeight;
      ctx.method_25294(8, footerY, this.w - 8, footerY + 1, Theme.withAlpha(Theme.TEXT_DIM, 75));
      ctx.method_51433(mc.field_1772, legend, 9, footerY + 4, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, disclaimer, 9, footerY + 15, Theme.TEXT_DIM, false);
   }

   private String status(String name) {
      boolean inTab = StaffPresence.inTab(name);
      boolean inWorld = StaffPresence.loadedInWorld(name);
      if (!StaffPresence.tabAvailable()) {
         return inWorld ? "WELT · TAB N/A" : "TAB N/A · UNKLAR";
      }
      if (inTab && inWorld) {
         return "TAB + WELT";
      }
      if (inTab) {
         return "TAB/NETZ";
      }
      if (inWorld) {
         return "NUR WELT";
      }
      return "KEINE QUELLE";
   }

   private int statusColor(String name) {
      boolean inTab = this.tabNames.contains(name);
      boolean inWorld = this.loadedNames.contains(name);
      if (inTab && inWorld) {
         return COLOR_VISIBLE;
      }
      return inTab || inWorld ? COLOR_PARTIAL : COLOR_UNKNOWN;
   }

   private void drawRow(class_332 ctx, String name, String status, int color, int y, int rowHeight) {
      ctx.method_25294(4, y, this.w - 4, y + rowHeight, Theme.withAlpha(Theme.TEXT, 9));
      ctx.method_25294(5, y + 3, 7, y + rowHeight - 3, color);
      int textY = y + (rowHeight == 12 ? 2 : 3);
      ctx.method_51433(mc.field_1772, name, 11, textY, Theme.TEXT, true);
      int statusX = this.w - 9 - mc.field_1772.method_1727(status);
      ctx.method_51433(mc.field_1772, status, statusX, textY, color, true);
   }
}

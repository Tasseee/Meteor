package dev.nightfall.modules;

import dev.nightfall.util.Theme;
import net.minecraft.class_310;
import net.minecraft.class_332;

/**
 * Gemeinsames Panel-Design für StaffWatch und AntiVanishHints.
 * Nutzt ausschließlich die bereits vorhandene Theme- und HUD-Render-Infrastruktur.
 */
public final class StaffHudPanel {
   public record Row(String name, String status, int color) {}

   private static final class_310 MC = class_310.method_1551();
   private static final int GREEN = 0xFF4ADE80;
   private static final int YELLOW = 0xFFFBBF24;
   private static final int GRAY = 0xFF9BA3AE;

   private StaffHudPanel() {}

   public static Row fromPresence(String name) {
      boolean tab = StaffPresence.inTab(name);
      boolean world = StaffPresence.loadedInWorld(name);
      String status;
      if (!StaffPresence.tabAvailable()) {
         status = world ? "WELT · TAB N/A" : "TAB N/A";
      } else if (tab && world) {
         status = "TAB + WELT";
      } else if (tab) {
         status = "TAB/NETZ";
      } else if (world) {
         status = "NUR WELT";
      } else {
         status = "KEINE QUELLE";
      }

      int color = tab && world ? GREEN : tab || world ? YELLOW : GRAY;
      return new Row(StaffPresence.displayName(name), status, color);
   }

   /**
    * Rendert das gemeinsame Panel. Die zurückgegebene Größe ist unskaliert,
    * damit HudModule und HUD-Editor die korrekte Hitbox erhalten.
    */
   public static int[] render(
      class_332 ctx,
      String title,
      String badge,
      int badgeColor,
      String subtitle,
      java.util.List<Row> rows,
      String empty,
      String footerTop,
      String footerBottom,
      boolean compact,
      boolean showFooter,
      float displayScale
   ) {
      var font = MC.field_1772;
      int rowHeight = compact ? 12 : 15;
      int headerHeight = compact ? 20 : 24;
      int subtitleHeight = subtitle != null && !compact ? 13 : 0;
      int footerHeight = showFooter && !compact ? 27 : 0;
      int bodyRows = rows.isEmpty() ? (empty == null ? 0 : 1) : rows.size();

      int headerWidth = font.method_1727(title) + 24;
      if (badge != null) headerWidth += font.method_1727(badge) + 18;
      int contentWidth = 0;
      for (Row row : rows) {
         contentWidth = Math.max(contentWidth, 33 + font.method_1727(row.name()) + font.method_1727(row.status()));
      }
      if (empty != null) contentWidth = Math.max(contentWidth, font.method_1727(empty) + 24);
      if (subtitle != null && !compact) contentWidth = Math.max(contentWidth, font.method_1727(subtitle) + 18);
      if (showFooter && !compact) {
         contentWidth = Math.max(contentWidth, Math.max(font.method_1727(footerTop), font.method_1727(footerBottom)) + 18);
      }

      int width = Math.max(194, Math.max(headerWidth, contentWidth));
      int maxWidth = Math.max(150, (int)((ctx.method_51421() - 18) / Math.max(0.5F, displayScale)));
      width = Math.min(width, maxWidth);
      int height = headerHeight + subtitleHeight + bodyRows * rowHeight + footerHeight + (bodyRows > 0 ? 3 : 0) + 2;

      // Dunkler Schatten und Theme-Hintergrund.
      rounded(ctx, -1, -1, width + 1, height + 1, Theme.withAlpha(0x000000, 72));
      rounded(ctx, 0, 0, width, height, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, width, 2, Theme.HUD_EDGE);
      ctx.method_25294(0, 2, 3, height, Theme.HUD_EDGE);

      // Kopfbereich mit dezenter Akzentfläche.
      ctx.method_25294(3, 2, width - 1, headerHeight, Theme.withAlpha(Theme.ACCENT, 28));
      ctx.method_25294(3, headerHeight, width - 2, headerHeight + 1, Theme.ACCENT);
      String shownTitle = fit(title, Math.max(28, width - 34 - (badge == null ? 0 : font.method_1727(badge) + 20)));
      ctx.method_51433(font, shownTitle, 10, compact ? 5 : 7, Theme.TEXT, true);

      if (badge != null) {
         int badgeWidth = font.method_1727(badge) + 10;
         int badgeX = width - badgeWidth - 8;
         rounded(ctx, badgeX, 4, badgeX + badgeWidth, headerHeight - 3, Theme.withAlpha(badgeColor, 38));
         String shownBadge = fit(badge, Math.max(12, badgeWidth - 8));
         ctx.method_51433(font, shownBadge, badgeX + 5, compact ? 5 : 7, badgeColor, false);
      }

      int y = headerHeight + 1;
      if (subtitleHeight > 0) {
         ctx.method_51433(font, fit(subtitle, width - 18), 9, y + 2, Theme.TEXT_DIM, false);
         y += subtitleHeight;
      }

      if (rows.isEmpty() && empty != null) {
         ctx.method_25294(4, y, width - 4, y + rowHeight, Theme.withAlpha(Theme.TEXT, 8));
         ctx.method_51433(font, fit(empty, width - 20), 10, y + (rowHeight == 12 ? 2 : 3), Theme.TEXT_DIM, false);
      } else {
         int index = 0;
         long time = System.currentTimeMillis();
         for (Row row : rows) {
            int rowY = y + index * rowHeight;
            if ((index & 1) == 1) {
               ctx.method_25294(4, rowY, width - 4, rowY + rowHeight, Theme.withAlpha(Theme.TEXT, 7));
            }

            int statusWidth = font.method_1727(row.status()) + 10;
            int statusX = width - statusWidth - 9;
            int nameX = 19;
            int nameMax = Math.max(8, statusX - nameX - 11);
            String shownName = fit(row.name(), nameMax);

            // Dezentes Leuchten und pulsierender Punkt bei lokal geladener Entity.
            float pulse = "TAB + WELT".equals(row.status())
               ? 0.76F + 0.24F * (float)Math.sin(time / 390.0)
               : 1.0F;
            int dotY = rowY + rowHeight / 2;
            ctx.method_25294(9, dotY - 3, 16, dotY + 4, Theme.withAlpha(row.color(), (int)(38 * pulse)));
            ctx.method_25294(11, dotY - 2, 14, dotY + 2, Theme.withAlpha(row.color(), (int)(255 * pulse)));

            rounded(ctx, statusX, rowY + 1, width - 8, rowY + rowHeight - 1, Theme.withAlpha(row.color(), 34));
            ctx.method_51433(font, row.status(), statusX + 5, rowY + (rowHeight == 12 ? 2 : 3), row.color(), false);
            ctx.method_51433(font, shownName, nameX, rowY + (rowHeight == 12 ? 2 : 3), Theme.TEXT, true);
            index++;
         }
      }

      if (footerHeight > 0) {
         int footerY = headerHeight + subtitleHeight + bodyRows * rowHeight + 3;
         ctx.method_25294(8, footerY, width - 8, footerY + 1, Theme.withAlpha(Theme.TEXT_DIM, 70));
         ctx.method_51433(font, fit(footerTop, width - 18), 9, footerY + 4, Theme.TEXT_DIM, false);
         ctx.method_51433(font, fit(footerBottom, width - 18), 9, footerY + 15, Theme.TEXT_DIM, false);
      }

      return new int[]{width, height};
   }

   private static String fit(String value, int maxWidth) {
      if (value == null || maxWidth <= 0) return "";
      if (MC.field_1772.method_1727(value) <= maxWidth) return value;
      String out = value;
      while (!out.isEmpty() && MC.field_1772.method_1727(out + "...") > maxWidth) {
         out = out.substring(0, out.length() - 1);
      }
      return out.isEmpty() ? "" : out + "...";
   }

   private static void rounded(class_332 ctx, int x1, int y1, int x2, int y2, int color) {
      if (x2 <= x1 || y2 <= y1) return;
      if (x2 - x1 < 5 || y2 - y1 < 5) {
         ctx.method_25294(x1, y1, x2, y2, color);
         return;
      }
      ctx.method_25294(x1 + 2, y1, x2 - 2, y1 + 1, color);
      ctx.method_25294(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
      ctx.method_25294(x1, y1 + 2, x2, y2 - 2, color);
      ctx.method_25294(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
      ctx.method_25294(x1 + 2, y2 - 1, x2 - 2, y2, color);
   }
}

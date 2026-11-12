package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_1304;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_332;
import net.minecraft.class_465;

public final class InvSee extends HudModule {
   private final StringSetting titleFilter = this.add(new StringSetting("Titel-Filter (leer = alle)", "", 48));
   private final NumberSetting maxEntries = this.add(new NumberSetting("Max. Eintraege", 18.0, 6.0, 54.0, 1.0));
   private final BoolSetting showMenuData = this.add(new BoolSetting("Geoeffnete Menue-Slots", true));
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 8.0, 2.0, 32.0, 1.0));
   private long nextRefreshAt;
   private int crystalCount;
   private int elytraCount;
   private int maceCount;
   private int totemCount;
   private boolean wasMenuOpen;
   private static final class_1304[] SLOTS = new class_1304[]{
      class_1304.field_6173, class_1304.field_6171, class_1304.field_6169, class_1304.field_6174, class_1304.field_6172, class_1304.field_6166
   };
   private static final String[] SLOT_LABELS = new String[]{"MH", "OH", "K", "R", "H", "S"};

   private void refreshItemCounts(List<class_1799> stacks) {
      this.crystalCount = this.elytraCount = this.maceCount = this.totemCount = 0;

      for (class_1799 stack : stacks) {
         if (!stack.method_7960()) {
            if (stack.method_31574(class_1802.field_8301)) {
               this.crystalCount = this.crystalCount + stack.method_7947();
            } else if (stack.method_31574(class_1802.field_8833)) {
               this.elytraCount = this.elytraCount + stack.method_7947();
            } else if (stack.method_31574(class_1802.field_49814)) {
               this.maceCount = this.maceCount + stack.method_7947();
            } else if (stack.method_31574(class_1802.field_8288)) {
               this.totemCount = this.totemCount + stack.method_7947();
            }
         }
      }
   }

   public InvSee() {
      super("InvSee", "HUD fuer sichtbare Spielerausruestung und aktuell geoeffnete Server-Menues.");
      this.w = 178;
      this.h = 108;
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{Math.max(4, sw - 186), Math.max(42, sh / 2 - 54)};
   }

   @Override
   protected void draw(class_332 ctx) {
      if (mc.field_1724 != null) {
         class_1657 aimedPlayer = mc.field_1692 instanceof class_1657 player && player != mc.field_1724 && player.method_5805() ? player : null;
         class_1657 target = aimedPlayer != null && aimedPlayer.method_5739(mc.field_1724) <= this.targetRange.get() ? aimedPlayer : null;
         boolean menuOpen = this.showMenuData.get() && mc.field_1755 instanceof class_465;
         String menuTitle = "";
         String menuStatus = !this.showMenuData.get() ? "Menue-Anzeige deaktiviert" : "Kein Server-Menue geoeffnet";
         List<class_1799> menuStacks = List.of();
         boolean menuMatches = false;
         if (menuOpen) {
            class_465<?> screen = (class_465<?>)mc.field_1755;
            menuTitle = screen.method_25440().getString();
            String filter = this.titleFilter.get().trim();
            menuMatches = filter.isEmpty() || menuTitle.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
            if (menuMatches) {
               menuStacks = mc.field_1724.field_7512.method_7602();
               menuStatus = menuTitle;
            } else {
               menuStatus = "Titel-Filter passt nicht";
            }
         }

         if (menuOpen && menuMatches) {
            if (!this.wasMenuOpen || System.currentTimeMillis() >= this.nextRefreshAt) {
               this.refreshItemCounts(menuStacks);
               this.nextRefreshAt = System.currentTimeMillis() + 5000L;
               this.wasMenuOpen = true;
            }
         } else {
            this.wasMenuOpen = false;
            this.nextRefreshAt = 0L;
            this.crystalCount = this.elytraCount = this.maceCount = this.totemCount = 0;
         }

         int screenHeight = mc.method_22683().method_4502();
         int rows = menuOpen && menuMatches && !menuStacks.isEmpty() ? Math.min(this.maxEntries.getInt(), Math.max(1, (screenHeight - 160) / 12)) : 0;
         int menuHeight = this.showMenuData.get() ? 58 + rows * 12 : 0;
         this.h = 78 + menuHeight;
         ctx.method_25294(0, 0, this.w, this.h, -451931621);
         ctx.method_25294(0, 0, this.w, 2, -10099035);
         String title = target != null ? "INVSEE · " + target.method_5477().getString() : "INVSEE · KEIN SPIELER";

         while (mc.field_1772.method_1727(title) > this.w - 14 && title.length() > 12) {
            title = title.substring(0, title.length() - 2) + "…";
         }

         ctx.method_51433(mc.field_1772, title, 7, 7, -10099035, true);
         String targetStatus = target != null
            ? String.format(Locale.ROOT, "Ausrüstung · %.1f Blöcke", target.method_5739(mc.field_1724))
            : (aimedPlayer != null ? "Spieler zu weit entfernt" : "Spieler im Fadenkreuz anvisieren");
         ctx.method_51433(mc.field_1772, targetStatus, 7, 20, -4669227, false);

         for (int i = 0; i < SLOTS.length; i++) {
            int x = 7 + i * 28;
            ctx.method_51433(mc.field_1772, SLOT_LABELS[i], x + 7, 34, -4669227, false);
            ctx.method_25294(x, 44, x + 24, 68, 1714699085);
            class_1799 stack = target == null ? class_1799.field_8037 : target.method_6118(SLOTS[i]);
            if (stack.method_7960()) {
               ctx.method_51433(mc.field_1772, target == null ? "?" : "–", x + 9, 52, target == null ? -9208945 : -5196347, false);
            } else {
               ctx.method_51427(stack, x + 4, 48);
               if (stack.method_7947() > 1) {
                  ctx.method_51433(mc.field_1772, Integer.toString(stack.method_7947()), x + 13, 60, -1, true);
               }
            }
         }

         if (this.showMenuData.get()) {
            int top = 77;
            ctx.method_25294(5, top, this.w - 5, top + 18, 1429092680);
            String header = menuOpen && menuMatches ? "SERVER-MENUE · " + menuTitle : "SERVER-MENUE";

            while (mc.field_1772.method_1727(header) > this.w - 16 && header.length() > 10) {
               header = header.substring(0, header.length() - 2) + "…";
            }

            ctx.method_51433(mc.field_1772, header, 8, top + 5, -10099035, false);
            if (menuOpen && menuMatches) {
               String summary = "CR " + this.crystalCount + "  Elytra " + this.elytraCount + "  Mace " + this.maceCount + "  Totem " + this.totemCount;
               ctx.method_51433(mc.field_1772, summary, 8, top + 19, -1518198, false);
               ctx.method_51433(mc.field_1772, "Aktualisierung alle 5 s", 8, top + 31, -6444866, false);
            }

            if (menuOpen && menuMatches) {
               int row = 0;

               for (int ix = 0; ix < menuStacks.size() && row < rows; ix++) {
                  class_1799 stack = menuStacks.get(ix);
                  if (!stack.method_7960()) {
                     int y = top + 46 + row * 12;
                     String line = ix + ": " + stack.method_7964().getString() + " x" + stack.method_7947();

                     while (mc.field_1772.method_1727(line) > this.w - 16 && line.length() > 8) {
                        line = line.substring(0, line.length() - 2) + "…";
                     }

                     if ((row & 1) == 0) {
                        ctx.method_25294(7, y - 2, this.w - 7, y + 10, 807018286);
                     }

                     ctx.method_51433(mc.field_1772, line, 9, y, -986377, false);
                     row++;
                  }
               }

               if (menuStacks.isEmpty()) {
                  ctx.method_51433(mc.field_1772, "Menue geoeffnet · keine Slots", 8, top + 46, -5196347, false);
               } else if (row == 0) {
                  ctx.method_51433(mc.field_1772, "Keine belegten Slots", 8, top + 46, -5196347, false);
               } else if (menuStacks.stream().filter(stackx -> !stackx.method_7960()).count() > row) {
                  ctx.method_51433(mc.field_1772, "… weitere Slots vorhanden", 8, top + 46 + row * 12, -5196347, false);
               }
            } else {
               ctx.method_51433(mc.field_1772, menuStatus, 8, top + 46, -5196347, false);
            }
         }
      }
   }
}

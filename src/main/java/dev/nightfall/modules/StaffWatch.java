package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.class_332;

public final class StaffWatch extends HudModule {
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   public final ListSetting staffNames = this.add(new ListSetting("Beobachtete Namen"));
   private final NumberSetting maxRows = this.add(new NumberSetting("Max. HUD-Zeilen", 8.0, 3.0, 12.0, 1.0));
   private final BoolSetting compactMode = this.add(new BoolSetting("Kompaktes HUD", false));
   private final BoolSetting sortVisibleFirst = this.add(new BoolSetting("Sichtbare Treffer zuerst", true));
   private final BoolSetting showLegend = this.add(new BoolSetting("Legende anzeigen", true));
   private final BoolSetting showSubtitle = this.add(new BoolSetting("Untertitel anzeigen", true));

   public StaffWatch() {
      super("StaffWatch", "Modernes, verschiebbares HUD für manuell eingetragene Namen; nur clientseitige Präsenzhinweise.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{8, 42};
   }

   @Override
   public void onWorldLeave() {
      StaffPresence.reset();
   }

   @Override
   public void onTick() {
      // StaffPresence wird zentral einmal pro Client-Tick aktualisiert.
   }

   @Override
   protected void draw(class_332 ctx) {
      boolean preview = mc.field_1755 instanceof HudEditorScreen;
      boolean hasWorld = mc.field_1724 != null && mc.field_1687 != null;
      List<String> names = this.orderedNames(this.staffNames.get());
      List<StaffHudPanel.Row> rows = new ArrayList<>();
      String empty = null;
      int loaded = 0;

      if (!hasWorld && !preview) {
         empty = "Keine Welt geladen";
      } else if (names.isEmpty()) {
         if (preview) {
            rows.add(new StaffHudPanel.Row("Beispiel_Admin", "TAB + WELT", 0xFF4ADE80));
            rows.add(new StaffHudPanel.Row("ModMaus", "TAB/NETZ", 0xFFFBBF24));
            rows.add(new StaffHudPanel.Row("Helferlein", "KEINE QUELLE", COLOR_UNKNOWN));
         } else {
            empty = "Keine Namen - /nf staff add <Name>";
         }
      } else {
         int limit = this.maxRows.getInt();
         for (String name : names) {
            if (StaffPresence.loadedInWorld(name)) loaded++;
            if (rows.size() >= limit) continue;
            rows.add(StaffHudPanel.fromPresence(name));
         }
         if (names.size() > limit) {
            rows.add(new StaffHudPanel.Row("+" + (names.size() - limit) + " weitere", "MEHR", COLOR_UNKNOWN));
         }
      }

      String badge = preview && names.isEmpty() ? "VORSCHAU" : loaded + "/" + names.size() + " lokal";
      String subtitle = this.showSubtitle.get() ? "Tab-Einträge können netzwerkweit sein" : null;
      int[] size = StaffHudPanel.render(
         ctx,
         "STAFFWATCH",
         badge,
         loaded > 0 ? 0xFF4ADE80 : 0xFFFBBF24,
         subtitle,
         rows,
         empty,
         "TAB/NETZ = gelistet, Backend unbekannt",
         "WELT = Entity in deiner geladenen Welt",
         this.compactMode.get(),
         this.showLegend.get(),
         this.scale()
      );
      this.w = size[0];
      this.h = size[1];
   }

   private List<String> orderedNames(Set<String> names) {
      List<String> result = new ArrayList<>();
      for (String name : names) {
         if (name != null && !name.isBlank()) result.add(name);
      }
      if (this.sortVisibleFirst.get()) {
         result.sort(Comparator.comparingInt(StaffPresence::rank).thenComparing(String.CASE_INSENSITIVE_ORDER));
      } else {
         result.sort(String.CASE_INSENSITIVE_ORDER);
      }
      return result;
   }
}

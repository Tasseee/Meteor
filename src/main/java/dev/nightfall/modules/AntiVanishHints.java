package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.class_332;

public final class AntiVanishHints extends HudModule {
   private static final int COLOR_UNKNOWN = 0xFF9BA3AE;

   private final NumberSetting maxRows = this.add(new NumberSetting("Max. HUD-Zeilen", 5.0, 3.0, 10.0, 1.0));
   private final BoolSetting compactMode = this.add(new BoolSetting("Kompaktes HUD", false));
   private final BoolSetting showLegend = this.add(new BoolSetting("Legende anzeigen", true));
   private final BoolSetting showSubtitle = this.add(new BoolSetting("Untertitel anzeigen", true));

   public AntiVanishHints() {
      super("AntiVanishHints", "Modernes HUD mit clientseitigen Präsenzhinweisen; Abwesenheit beweist keinen Vanish.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{8, 90};
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
      StaffWatch staff = ModuleManager.get(StaffWatch.class);
      if (staff.isEnabled() && !preview) {
         this.w = 0;
         this.h = 0;
         return;
      }

      boolean hasWorld = mc.field_1724 != null && mc.field_1687 != null;
      Set<String> names = staff.staffNames.get();
      List<String> hints = new ArrayList<>();
      for (String name : names) {
         if (name != null && !name.isBlank() && (StaffPresence.inTab(name) || StaffPresence.loadedInWorld(name))) {
            hints.add(name);
         }
      }
      hints.sort(Comparator.comparingInt(StaffPresence::rank).thenComparing(String.CASE_INSENSITIVE_ORDER));

      List<StaffHudPanel.Row> rows = new ArrayList<>();
      String empty = null;
      if (!hasWorld && !preview) {
         empty = "Keine Welt geladen";
      } else if (names.isEmpty()) {
         if (preview) {
            rows.add(new StaffHudPanel.Row("Beispiel_Admin", "WELT · TAB", 0xFF4ADE80));
            rows.add(new StaffHudPanel.Row("ModMaus", "TAB/NETZ", 0xFFFBBF24));
         } else {
            empty = "Keine Namen eingetragen";
         }
      } else if (hints.isEmpty()) {
         empty = "Keine sichtbaren Hinweise";
      } else {
         int limit = this.maxRows.getInt();
         for (String name : hints) {
            if (rows.size() >= limit) break;
            rows.add(StaffHudPanel.fromPresence(name));
         }
         if (hints.size() > limit) {
            rows.add(new StaffHudPanel.Row("+" + (hints.size() - limit) + " weitere", "MEHR", COLOR_UNKNOWN));
         }
      }

      String badge = preview && names.isEmpty() ? "VORSCHAU" : hints.size() + " Hinweise";
      String subtitle = this.showSubtitle.get() ? "Nur clientseitig sichtbare Datenquellen" : null;
      int[] size = StaffHudPanel.render(
         ctx,
         "ANTIVANISH · HINWEISE",
         badge,
         hints.isEmpty() ? COLOR_UNKNOWN : 0xFFFBBF24,
         subtitle,
         rows,
         empty,
         "TAB/NETZ ist kein Unterservernachweis",
         "Abwesenheit ist kein Vanish-Beweis",
         this.compactMode.get(),
         this.showLegend.get(),
         this.scale()
      );
      this.w = size[0];
      this.h = size[1];
   }
}

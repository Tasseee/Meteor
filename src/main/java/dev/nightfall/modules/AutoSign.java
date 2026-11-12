package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.StringSetting;

public final class AutoSign extends Module {
   private final StringSetting line1 = this.add(new StringSetting("Zeile 1", "", 80));
   private final StringSetting line2 = this.add(new StringSetting("Zeile 2", "", 80));
   private final StringSetting line3 = this.add(new StringSetting("Zeile 3", "", 80));
   private final StringSetting line4 = this.add(new StringSetting("Zeile 4", "", 80));
   public final BoolSetting overwrite = this.add(new BoolSetting("Vorhandenen Text ersetzen", true));

   public AutoSign() {
      super("AutoSign", "Füllt beim Öffnen eines Schildes die konfigurierten Zeilen vor; Speichern bleibt manuell.", Category.MISC);
   }

   public String[] configuredLines() {
      return new String[]{this.line1.get(), this.line2.get(), this.line3.get(), this.line4.get()};
   }
}

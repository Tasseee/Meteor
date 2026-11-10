package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.StringSetting;

/** Prefills sign text; the player still reviews and submits the sign normally. */
public final class AutoSign extends Module {
    private final StringSetting line1 = add(new StringSetting("Zeile 1", "", 80));
    private final StringSetting line2 = add(new StringSetting("Zeile 2", "", 80));
    private final StringSetting line3 = add(new StringSetting("Zeile 3", "", 80));
    private final StringSetting line4 = add(new StringSetting("Zeile 4", "", 80));
    public final BoolSetting overwrite = add(new BoolSetting("Vorhandenen Text ersetzen", true));

    public AutoSign() {
        super("AutoSign", "Füllt beim Öffnen eines Schildes die konfigurierten Zeilen vor; Speichern bleibt manuell.", Category.MISC);
    }

    public String[] configuredLines() {
        return new String[] { line1.get(), line2.get(), line3.get(), line4.get() };
    }
}

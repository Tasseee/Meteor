package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;

/**
 * Central configuration entry for user-authored, bounded macros.
 * Server-specific menu automation is intentionally not executed until its
 * protocol and server rules have been verified.
 */
public final class MacroManager extends Module {
    public final BoolSetting sellEnabled = add(new BoolSetting("Sell Macro", false));
    public final NumberSetting sellInterval = add(new NumberSetting("Sell Intervall (Sek.)", 5, 5, 300, 1));
    public final StringSetting sellItems = add(new StringSetting("Sell Items (IDs, Komma)", ""));
    public final BoolSetting orderEnabled = add(new BoolSetting("Order Macro", false));
    public final NumberSetting orderInterval = add(new NumberSetting("Order Intervall (Sek.)", 10, 10, 600, 1));
    public final StringSetting orderItem = add(new StringSetting("Order Item-ID", ""));
    public final BoolSetting spawnerEnabled = add(new BoolSetting("Spawner Macro", false));
    public final BoolSetting ahSniperEnabled = add(new BoolSetting("AH Sniper", false));
    public final BoolSetting ahAutoBuy = add(new BoolSetting("AH Auto-Buy", false));
    public final StringSetting ahItemFilter = add(new StringSetting("AH Item-Filter", ""));
    public final NumberSetting ahMaxPrice = add(new NumberSetting("AH Maximalpreis", 1000, 1, 100000000, 1));
    public final NumberSetting ahMaxPurchases = add(new NumberSetting("AH Käufe pro Aktivierung", 1, 1, 20, 1));
    public final BoolSetting macroCreator = add(new BoolSetting("Macro Creator aktiv", false));
    public final StringSetting customMacroName = add(new StringSetting("Eigenes Makro", "Neues Makro"));

    public MacroManager() {
        super("MacroManager", "Zentrale Einstellungen für Sell, Order, Spawner, AH Sniper und eigene Makros.", Category.MISC);
    }
}

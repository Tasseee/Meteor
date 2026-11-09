package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded command macros and a small one-shot user macro runner.
 *
 * Sell/Order use explicit server commands configured by the player and only run
 * when the configured item is actually present in the inventory. Server-specific
 * Auction House menus are intentionally not clicked without a verified adapter.
 */
public final class MacroManager extends Module {
    public final BoolSetting sellEnabled = add(new BoolSetting("Sell Macro", false));
    public final NumberSetting sellInterval = add(new NumberSetting("Sell Intervall (Sek.)", 5, 5, 300, 1));
    public final StringSetting sellItems = add(new StringSetting("Sell Items (IDs, Komma)", "", 256));
    public final StringSetting sellCommand = add(new StringSetting("Sell Befehl", "sell", 48));

    public final BoolSetting orderEnabled = add(new BoolSetting("Order Macro", false));
    public final NumberSetting orderInterval = add(new NumberSetting("Order Intervall (Sek.)", 10, 10, 600, 1));
    public final StringSetting orderItem = add(new StringSetting("Order Item-ID", "", 128));
    public final StringSetting orderCommand = add(new StringSetting("Order Befehl", "order", 48));

    public final BoolSetting spawnerEnabled = add(new BoolSetting("Spawner Macro", false));
    public final NumberSetting spawnerRadius = add(new NumberSetting("Spawner Suchradius", 6, 3, 10, 1));

    public final BoolSetting ahSniperEnabled = add(new BoolSetting("AH Sniper", false));
    public final BoolSetting ahAutoBuy = add(new BoolSetting("AH Auto-Buy", false));
    public final StringSetting ahItemFilter = add(new StringSetting("AH Item-Filter", "", 128));
    public final NumberSetting ahMaxPrice = add(new NumberSetting("AH Maximalpreis", 1000, 1, 100000000, 1));
    public final NumberSetting ahMaxPurchases = add(new NumberSetting("AH Käufe pro Aktivierung", 1, 1, 20, 1));

    public final BoolSetting macroCreator = add(new BoolSetting("Macro Creator aktiv", false));
    public final StringSetting customMacroName = add(new StringSetting("Eigenes Makro", "Neues Makro", 64));
    public final StringSetting macroSteps = add(new StringSetting(
            "Schritte (wait:20,chat:/sell)", "wait:20,notify:Makro gestartet,chat:/sell,notify:Fertig", 256));

    private int sellTicks;
    private int orderTicks;
    private int spawnerTicks;
    private int macroIndex;
    private int macroWait;
    private int macroCooldown;
    private int macroChatActions;
    private boolean macroRunning;

    public MacroManager() {
        super("MacroManager", "Führt begrenzte Sell-/Order-Befehle und eigene Schritt-Makros aus.", Category.MISC);
    }

    @Override protected void onEnable() {
        sellTicks = orderTicks = spawnerTicks = 0;
        macroIndex = macroWait = macroCooldown = macroChatActions = 0;
        macroRunning = false;
        if (macroCreator.get()) {
            String[] steps = parsedSteps();
            if (steps.length == 0) {
                NotificationCenter.push("Macro Creator", "Keine Schritte eingetragen.");
            } else {
                macroRunning = true;
                NotificationCenter.push("Macro Creator", customMacroName.get() + " gestartet.");
            }
        }
        if (ahSniperEnabled.get() && ahAutoBuy.get()) {
            NotificationCenter.push("AH Sniper", "Auto-Buy bleibt aus: Das Server-Menüprotokoll ist nicht verifiziert.");
        }
    }

    @Override protected void onDisable() {
        macroRunning = false;
        sellTicks = orderTicks = spawnerTicks = 0;
        macroWait = macroCooldown = 0;
    }

    @Override public void onWorldLeave() {
        macroRunning = false;
        sellTicks = orderTicks = spawnerTicks = 0;
        macroWait = macroCooldown = 0;
    }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (macroRunning) {
            tickCustomMacro();
            return;
        }

        if (sellEnabled.get()) {
            if (++sellTicks >= sellInterval.getInt() * 20) {
                sellTicks = 0;
                sellOneConfiguredItem();
            }
        } else sellTicks = 0;

        if (orderEnabled.get()) {
            if (++orderTicks >= orderInterval.getInt() * 20) {
                orderTicks = 0;
                orderConfiguredItem();
            }
        } else orderTicks = 0;

        if (spawnerEnabled.get()) {
            if (++spawnerTicks >= 100) {
                spawnerTicks = 0;
                if (nearSpawner(spawnerRadius.getInt())) sellOneConfiguredItem();
            }
        } else spawnerTicks = 0;
    }

    private void sellOneConfiguredItem() {
        for (String token : sellItems.get().split(",")) {
            Item item = resolveItem(token);
            if (item == null || !hasItem(item, 1)) continue;
            String path = Registries.ITEM.getId(item).getPath();
            if (sendConfiguredCommand(sellCommand.get(), path)) return;
        }
    }

    private void orderConfiguredItem() {
        Item item = resolveItem(orderItem.get());
        if (item == null || !hasItem(item, 1)) return;
        sendConfiguredCommand(orderCommand.get(), Registries.ITEM.getId(item).getPath());
    }

    private boolean sendConfiguredCommand(String prefix, String itemPath) {
        if (prefix == null || prefix.isBlank() || itemPath == null || itemPath.isBlank()) return false;
        String command = (prefix.trim() + " " + itemPath).trim();
        if (command.length() > 160 || command.contains("\n") || command.contains("\r")) return false;
        if (mc.player == null || mc.player.networkHandler == null) return false;
        mc.player.networkHandler.sendChatCommand(command.startsWith("/") ? command.substring(1) : command);
        return true;
    }

    private Item resolveItem(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.trim().toLowerCase(Locale.ROOT);
        if (!clean.contains(":")) clean = "minecraft:" + clean;
        Identifier id = Identifier.tryParse(clean);
        if (id == null) return null;
        Item item = Registries.ITEM.get(id);
        return item == net.minecraft.item.Items.AIR ? null : item;
    }

    private boolean hasItem(Item item, int needed) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) count += stack.getCount();
            if (count >= needed) return true;
        }
        return false;
    }

    private boolean nearSpawner(int radius) {
        BlockPos center = mc.player.getBlockPos();
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > r2) continue;
                    BlockPos pos = center.add(x, y, z);
                    if (mc.world.isChunkLoaded(pos) && mc.world.getBlockState(pos).isOf(Blocks.SPAWNER)) return true;
                }
            }
        }
        return false;
    }

    private String[] parsedSteps() {
        List<String> out = new ArrayList<>();
        for (String step : macroSteps.get().split(",")) {
            String clean = step.trim();
            if (!clean.isEmpty()) out.add(clean);
            if (out.size() >= 32) break;
        }
        return out.toArray(String[]::new);
    }

    private void tickCustomMacro() {
        if (macroWait > 0) { macroWait--; return; }
        if (macroCooldown > 0) { macroCooldown--; return; }
        String[] steps = parsedSteps();
        if (macroIndex >= steps.length) {
            macroRunning = false;
            NotificationCenter.push("Macro Creator", customMacroName.get() + " abgeschlossen.");
            return;
        }

        String step = steps[macroIndex++];
        int split = step.indexOf(':');
        if (split < 1) { stopMacro("Ungültiger Schritt: " + step); return; }
        String action = step.substring(0, split).trim().toLowerCase(Locale.ROOT);
        String value = step.substring(split + 1).trim();

        switch (action) {
            case "wait" -> {
                try {
                    int ticks = Integer.parseInt(value);
                    if (ticks < 1 || ticks > 12000) { stopMacro("wait muss zwischen 1 und 12000 Ticks liegen."); return; }
                    macroWait = ticks;
                } catch (NumberFormatException ex) { stopMacro("Ungültiger wait-Wert."); }
            }
            case "notify" -> NotificationCenter.push(customMacroName.get(), value.isBlank() ? "Schritt ausgeführt." : value);
            case "chat" -> {
                if (value.isBlank() || value.length() > 160 || value.contains("\n") || value.contains("\r")) {
                    stopMacro("Ungültige Chat-Aktion."); return;
                }
                if (++macroChatActions > 20) { stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf."); return; }
                if (value.startsWith("/")) mc.player.networkHandler.sendChatCommand(value.substring(1));
                else mc.player.networkHandler.sendChatMessage(value);
                macroCooldown = 10;
            }
            case "sell" -> {
                Item item = resolveItem(value);
                if (item == null || !hasItem(item, 1)) { stopMacro("Sell-Schritt abgebrochen: Item fehlt."); return; }
                if (++macroChatActions > 20) { stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf."); return; }
                sendConfiguredCommand(sellCommand.get(), Registries.ITEM.getId(item).getPath());
                macroCooldown = 10;
            }
            case "order" -> {
                Item item = resolveItem(value);
                if (item == null || !hasItem(item, 1)) { stopMacro("Order-Schritt abgebrochen: Item fehlt."); return; }
                if (++macroChatActions > 20) { stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf."); return; }
                sendConfiguredCommand(orderCommand.get(), Registries.ITEM.getId(item).getPath());
                macroCooldown = 10;
            }
            case "has" -> {
                int lastColon = value.lastIndexOf(':');
                if (lastColon < 1) { stopMacro("has-Format: minecraft:diamond:2"); return; }
                Item item = resolveItem(value.substring(0, lastColon));
                int needed;
                try { needed = Integer.parseInt(value.substring(lastColon + 1)); }
                catch (NumberFormatException ex) { stopMacro("Ungültige Anzahl bei has."); return; }
                if (item == null || needed < 1 || needed > 64 || !hasItem(item, needed)) {
                    stopMacro("Makro gestoppt: benötigte Items fehlen."); return;
                }
            }
            default -> stopMacro("Unbekannte Aktion: " + action);
        }
    }

    private void stopMacro(String reason) {
        macroRunning = false;
        NotificationCenter.push("Macro Creator", reason);
    }
}

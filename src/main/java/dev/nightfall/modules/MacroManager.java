package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded command macros, AH menu reader and a small one-shot user macro runner.
 * The AH buyer fails closed unless an auction title, item filter, explicit price,
 * configured limits and (when shown) a matching confirmation screen are all present.
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
    public final BoolSetting spawnerAutoBreak = add(new BoolSetting("Spawner abbauen (Fadenkreuz)", false));
    public final NumberSetting spawnerBreakDistance = add(new NumberSetting("Spawner-Abstand", 2.5, 1.5, 4, 0.5));
    public final BoolSetting spawnerAutoSellDrops = add(new BoolSetting("Aufgenommene Drops verkaufen", false));

    public final BoolSetting ahSniperEnabled = add(new BoolSetting("AH Sniper", false));
    public final BoolSetting ahAutoBuy = add(new BoolSetting("AH Auto-Buy", false));
    public final StringSetting ahItemFilter = add(new StringSetting("AH Item-Filter", "", 128));
    public final StringSetting ahMenuTitle = add(new StringSetting("AH Menütitel enthält", "auction", 32));
    public final NumberSetting ahMaxPrice = add(new NumberSetting("AH Maximalpreis", 1000, 1, 100000000, 1));
    public final NumberSetting ahMaxPurchases = add(new NumberSetting("AH Käufe pro Aktivierung", 1, 1, 20, 1));
    public final NumberSetting ahMaxTotalSpend = add(new NumberSetting("AH Gesamtbudget", 10000, 1, 1000000000, 1));
    public final NumberSetting ahCooldownTicks = add(new NumberSetting("AH Kaufpause (Ticks)", 40, 20, 200, 5));

    public final BoolSetting macroCreator = add(new BoolSetting("Macro Creator aktiv", false));
    public final StringSetting customMacroName = add(new StringSetting("Eigenes Makro", "Neues Makro", 64));
    public final StringSetting macroSteps = add(new StringSetting(
            "Schritte (wait:20,chat:/sell)", "wait:20,notify:Makro gestartet,chat:/sell,notify:Fertig", 256));

    private static final Pattern LABELLED_PRICE = Pattern.compile(
            "(?i)(?:price|preis|kosten|cost|buy\\s*now|kaufpreis)\\D{0,18}([$€]?\\s*[0-9][0-9.,]*\\s*[kmb]?)");
    private static final Pattern CURRENCY_PRICE = Pattern.compile("[$€]\\s*([0-9][0-9.,]*\\s*[kmb]?)");

    private int sellTicks;
    private int orderTicks;
    private int spawnerTicks;
    private int macroIndex;
    private int macroWait;
    private int macroCooldown;
    private int macroChatActions;
    private boolean macroRunning;

    private BlockPos spawnerBreaking;
    private Direction spawnerFace = Direction.UP;
    private int spawnerBreakTicks;
    private int spawnerPickupTicks;
    private final Map<Item, Integer> spawnerCountsBefore = new HashMap<>();

    private int ahScanTicks;
    private int ahCooldown;
    private int ahPurchases;
    private long ahSpent;
    private boolean ahPending;
    private boolean ahConfirmClicked;
    private int ahPendingTicks;
    private int ahListingSyncId = -1;
    private Item ahPendingItem;
    private String ahPendingName = "";
    private long ahPendingPrice;
    private int ahInventoryBefore;
    private boolean ahLastNotice;
    private int ahLastNoticeSlot = -1;

    public MacroManager() {
        super("MacroManager", "Führt Sell-/Order-Makros, einen begrenzten AH-Sniper, Spawner-Abbau und eigene Schritt-Makros aus.", Category.MISC);
    }

    @Override protected void onEnable() {
        sellTicks = orderTicks = spawnerTicks = 0;
        macroIndex = macroWait = macroCooldown = macroChatActions = 0;
        macroRunning = false;
        ahPurchases = 0;
        ahSpent = 0;
        resetAuctionPending();
        resetSpawner();
        if (macroCreator.get()) {
            String[] steps = parsedSteps();
            if (steps.length == 0) {
                NotificationCenter.push("Macro Creator", "Keine Schritte eingetragen.");
            } else {
                macroRunning = true;
                NotificationCenter.push("Macro Creator", customMacroName.get() + " gestartet.");
            }
        }
    }

    @Override protected void onDisable() {
        macroRunning = false;
        sellTicks = orderTicks = spawnerTicks = 0;
        macroWait = macroCooldown = 0;
        resetAuctionPending();
        resetSpawner();
    }

    @Override public void onWorldLeave() {
        macroRunning = false;
        sellTicks = orderTicks = spawnerTicks = 0;
        macroWait = macroCooldown = 0;
        resetAuctionPending();
        resetSpawner();
    }

    private void resetAuctionPending() {
        ahPending = false;
        ahConfirmClicked = false;
        ahPendingTicks = 0;
        ahListingSyncId = -1;
        ahPendingItem = null;
        ahPendingName = "";
        ahPendingPrice = 0;
        ahInventoryBefore = 0;
    }

    private void resetSpawner() {
        spawnerBreaking = null;
        spawnerBreakTicks = 0;
        spawnerPickupTicks = 0;
        spawnerCountsBefore.clear();
    }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (ahCooldown > 0) ahCooldown--;

        if (macroRunning) {
            tickCustomMacro();
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

        if (spawnerEnabled.get()) tickSpawnerMacro();
        else resetSpawner();

        if (ahSniperEnabled.get()) tickAuctionSniper();
        else resetAuctionPending();
    }

    // ---- Auction House scanner / bounded auto-buy ----

    private void tickAuctionSniper() {
        if (ahPending) {
            tickPendingAuctionPurchase();
            return;
        }
        if (ahCooldown > 0 || ++ahScanTicks < 10) return;
        ahScanTicks = 0;
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return;

        String title = mc.currentScreen.getTitle().getString();
        if (!isAuctionBrowseTitle(title)) return;

        String filter = ahItemFilter.get().trim();
        if (filter.isEmpty()) return; // Never buy without an explicit item filter.
        ScreenHandler handler = screen.getScreenHandler();
        int containerSlots = Math.max(0, handler.getStacks().size() - 36);
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty() || !matchesAuctionFilter(stack, filter)) continue;
            long price = findPrice(stack);
            if (price <= 0 || price > ahMaxPrice.getInt()) continue;
            if (price > ahMaxTotalSpend.getInt() - ahSpent) continue;

            if (!ahAutoBuy.get()) {
                if (!ahLastNotice || ahLastNoticeSlot != slot) {
                    NotificationCenter.push("AH Sniper", "Treffer: " + stack.getName().getString() + " für " + price + ".");
                    ahLastNotice = true;
                    ahLastNoticeSlot = slot;
                }
                continue;
            }
            if (ahPurchases >= ahMaxPurchases.getInt()) {
                NotificationCenter.push("AH Sniper", "Kauflimit dieser Aktivierung erreicht.");
                return;
            }

            ahPending = true;
            ahPendingTicks = 0;
            ahListingSyncId = handler.syncId;
            ahPendingItem = stack.getItem();
            ahPendingName = stack.getName().getString();
            ahPendingPrice = price;
            ahInventoryBefore = countInventory(ahPendingItem);
            ahConfirmClicked = false;
            mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
            ahCooldown = ahCooldownTicks.getInt();
            NotificationCenter.push("AH Sniper", "Preis geprüft; Angebot wird verifiziert: " + ahPendingName + " · " + price);
            return;
        }
    }

    private boolean isAuctionBrowseTitle(String title) {
        String keyword = ahMenuTitle.get().trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) return false;
        String normalized = title.toLowerCase(Locale.ROOT);
        return normalized.contains(keyword) && !isConfirmationTitle(normalized);
    }

    private static boolean isConfirmationTitle(String title) {
        return title.contains("confirm") || title.contains("purchase")
                || title.contains("bestätig") || title.contains("bestaetig")
                || title.contains("kauf bestätigen") || title.contains("purchase");
    }

    private static boolean matchesAuctionFilter(ItemStack stack, String filter) {
        String name = stack.getName().getString().toLowerCase(Locale.ROOT);
        String id = Registries.ITEM.getId(stack.getItem()).toString().toLowerCase(Locale.ROOT);
        for (String part : filter.split("[,;]")) {
            String token = part.trim().toLowerCase(Locale.ROOT);
            if (!token.isEmpty() && (name.contains(token) || id.contains(token))) return true;
        }
        return false;
    }

    private static long findPrice(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        StringBuilder text = new StringBuilder();
        for (Text line : lore.lines()) text.append(line.getString()).append(' ');
        Matcher labelled = LABELLED_PRICE.matcher(text);
        if (labelled.find()) return parsePriceToken(labelled.group(1));
        Matcher currency = CURRENCY_PRICE.matcher(text);
        return currency.find() ? parsePriceToken(currency.group(1)) : -1;
    }

    private static long parsePriceToken(String token) {
        if (token == null) return -1;
        String value = token.toLowerCase(Locale.ROOT).replace("$", "").replace("€", "").replace(" ", "");
        double multiplier = 1;
        if (value.endsWith("k")) { multiplier = 1_000; value = value.substring(0, value.length() - 1); }
        else if (value.endsWith("m")) { multiplier = 1_000_000; value = value.substring(0, value.length() - 1); }
        else if (value.endsWith("b")) { multiplier = 1_000_000_000; value = value.substring(0, value.length() - 1); }

        int comma = value.lastIndexOf(',');
        int dot = value.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            if (dot > comma) value = value.replace(",", "");
            else value = value.replace(".", "").replace(',', '.');
        } else if (comma >= 0 || dot >= 0) {
            int separator = Math.max(comma, dot);
            int decimals = value.length() - separator - 1;
            if (multiplier != 1 || decimals == 1 || decimals == 2) {
                value = value.replace(',', '.');
            } else {
                value = value.replace(",", "").replace(".", "");
            }
        }
        try {
            double parsed = Double.parseDouble(value) * multiplier;
            if (!Double.isFinite(parsed) || parsed < 1 || parsed > Long.MAX_VALUE) return -1;
            return Math.round(parsed);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void tickPendingAuctionPurchase() {
        if (ahPendingItem == null) { resetAuctionPending(); return; }
        ahPendingTicks++;

        int currentCount = countInventory(ahPendingItem);
        if (currentCount > ahInventoryBefore) {
            finishAuctionPurchase(true, "Inventar-Zuwachs erkannt");
            return;
        }

        if (mc.currentScreen instanceof HandledScreen<?> screen) {
            ScreenHandler handler = screen.getScreenHandler();
            String title = mc.currentScreen.getTitle().getString().toLowerCase(Locale.ROOT);
            if (handler.syncId != ahListingSyncId && isConfirmationTitle(title)) {
                if (!ahConfirmClicked) verifyAndConfirmAuction(handler);
            } else if (handler.syncId != ahListingSyncId && !isAuctionBrowseTitle(title)) {
                // Unknown menu transition: never guess a button or slot.
                finishAuctionPurchase(false, "Unbekanntes Menü; Kauf abgebrochen.");
                return;
            }
        }

        if (ahPendingTicks > 80) {
            finishAuctionPurchase(false, "Kein bestätigter Kauf erkannt; Sniper pausiert.");
        }
    }

    private void verifyAndConfirmAuction(ScreenHandler handler) {
        boolean matchingItem = false;
        long visiblePrice = -1;
        int containerSlots = Math.max(0, handler.getStacks().size() - 36);
        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) continue;
            if (stack.getItem() == ahPendingItem
                    && matchesAuctionFilter(stack, ahItemFilter.get())) {
                matchingItem = true;
                long stackPrice = findPrice(stack);
                if (stackPrice > 0) visiblePrice = stackPrice;
            }
            if (visiblePrice <= 0) {
                long otherPrice = findPrice(stack);
                if (otherPrice > 0) visiblePrice = otherPrice;
            }
        }

        if (!matchingItem || visiblePrice <= 0 || visiblePrice != ahPendingPrice
                || visiblePrice > ahMaxPrice.getInt()
                || visiblePrice > ahMaxTotalSpend.getInt() - ahSpent) {
            finishAuctionPurchase(false, "Bestätigungsdaten stimmen nicht exakt überein.");
            return;
        }

        for (int slot = 0; slot < containerSlots; slot++) {
            ItemStack button = handler.getSlot(slot).getStack();
            if (button.isEmpty()) continue;
            String label = stackText(button).toLowerCase(Locale.ROOT);
            boolean confirm = containsAny(label, "confirm", "purchase", "buy now", "kaufen", "bestätigen", "bestaetigen");
            boolean cancel = containsAny(label, "cancel", "abbrechen", "zurück", "zurueck", "decline");
            if (confirm && !cancel) {
                mc.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
                ahConfirmClicked = true;
                ahCooldown = ahCooldownTicks.getInt();
                NotificationCenter.push("AH Sniper", "Kauf bestätigt: " + ahPendingName + " · " + ahPendingPrice);
                return;
            }
        }
        finishAuctionPurchase(false, "Kein eindeutiger Kaufen-Button gefunden.");
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String stackText(ItemStack stack) {
        StringBuilder text = new StringBuilder(stack.getName().getString());
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) for (Text line : lore.lines()) text.append(' ').append(line.getString());
        return text.toString();
    }

    private void finishAuctionPurchase(boolean success, String reason) {
        if (success) {
            ahPurchases++;
            ahSpent += ahPendingPrice;
            NotificationCenter.push("AH Sniper", "Kauf erkannt: " + ahPendingName + " (" + ahPendingPrice + ").");
        } else {
            NotificationCenter.push("AH Sniper", reason);
        }
        resetAuctionPending();
        ahCooldown = Math.max(ahCooldown, ahCooldownTicks.getInt());
    }

    private int countInventory(Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    // ---- Sell / Order ----

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
        return countInventory(item) >= needed;
    }

    // ---- Spawner macro ----

    private void tickSpawnerMacro() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (spawnerBreaking != null) {
            if (mc.world.getBlockState(spawnerBreaking).isAir()) {
                spawnerBreaking = null;
                spawnerBreakTicks = 0;
                spawnerPickupTicks = 100; // allow nearby vanilla item pickup to complete
                NotificationCenter.push("Spawner Macro", "Spawner abgebaut; prüfe aufgenommene Drops.");
                return;
            }
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || !hit.getBlockPos().equals(spawnerBreaking)) {
                spawnerBreaking = null;
                spawnerBreakTicks = 0;
                NotificationCenter.push("Spawner Macro", "Abbau gestoppt: Ziel nicht mehr im Fadenkreuz.");
                return;
            }
            if (++spawnerBreakTicks > 160) {
                spawnerBreaking = null;
                spawnerBreakTicks = 0;
                NotificationCenter.push("Spawner Macro", "Abbau nach Zeitlimit gestoppt.");
                return;
            }
            mc.interactionManager.updateBlockBreakingProgress(spawnerBreaking, spawnerFace);
            return;
        }

        if (spawnerPickupTicks > 0) {
            spawnerPickupTicks--;
            if (spawnerAutoSellDrops.get() && spawnerPickupTicks % 20 == 0) {
                boolean newConfiguredDrop = false;
                for (Map.Entry<Item, Integer> entry : spawnerCountsBefore.entrySet()) {
                    if (countInventory(entry.getKey()) > entry.getValue()) { newConfiguredDrop = true; break; }
                }
                if (newConfiguredDrop) {
                    sellOneConfiguredItem();
                    spawnerPickupTicks = 0;
                    spawnerCountsBefore.clear();
                    NotificationCenter.push("Spawner Macro", "Aufgenommene konfigurierte Drops zum Verkauf gesendet.");
                }
            }
            if (spawnerPickupTicks == 0) spawnerCountsBefore.clear();
        }

        if (!spawnerAutoBreak.get() || !(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.SPAWNER)) return;
        if (mc.player.getEyePos().squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(pos))
                > spawnerBreakDistance.get() * spawnerBreakDistance.get()) return;
        if (!mc.player.getMainHandStack().isSuitableFor(state)) return;

        // Nearby search radius is a guard for the configured farm area; actual mining still
        // requires the exact spawner to be deliberately placed under the crosshair.
        if (!nearSpawner(spawnerRadius.getInt())) return;
        spawnerCountsBefore.clear();
        for (String token : sellItems.get().split(",")) {
            Item item = resolveItem(token);
            if (item != null) spawnerCountsBefore.put(item, countInventory(item));
        }
        spawnerBreaking = pos.toImmutable();
        spawnerFace = hit.getSide();
        spawnerBreakTicks = 0;
        mc.interactionManager.attackBlock(pos, spawnerFace);
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

    // ---- Macro Creator ----

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

package dev.nightfall.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.nightfall.util.Storage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;

/**
 * Values only the inventory that the player explicitly opened with /invsee.
 * This module never requests or reconstructs hidden inventory data.
 */
public final class InvSeeValue extends Module {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PRICE_MAP = new TypeToken<Map<String, Double>>() {}.getType();
    private static final Map<String, Double> PRICES = new HashMap<>();
    private static boolean pricesLoaded;

    private final BoolSetting showLastResult = add(new BoolSetting("Letzten Wert im HUD behalten", true));
    private final BoolSetting showUnknownCount = add(new BoolSetting("Nicht bewertete Items anzeigen", true));
    private final BoolSetting autoOpenQueueOpponent = add(new BoolSetting("Bei RTPQ-Gegner automatisch /invsee", true));

    private static final Pattern[] OPPONENT_PATTERNS = new Pattern[] {
            Pattern.compile("(?i)(?:your\\s+opponent(?:\\s+is)?|opponent|dein(?:e)?\\s+gegner(?:\\s+ist)?|gegner(?:in)?)(?:\\s+gefunden)?\\s*[:=\\-»>]*\\s*([A-Za-z0-9_]{3,16})\\b"),
            Pattern.compile("(?i)(?:you(?:'re|\\s+are)\\s+(?:now\\s+)?(?:matched|fighting)\\s+with|matched\\s+with|du\\s+spielst\\s+gegen|du\\s+bist\\s+gegen|du\\s+wurdest\\s+mit|match\\s+gegen|kampf\\s+gegen)\\s*[:=\\-»>]*\\s*([A-Za-z0-9_]{3,16})\\b"),
            Pattern.compile("(?i)\\b([A-Za-z0-9_]{3,16})\\s+(?:is your opponent|ist dein gegner)\\b")
    };

    private String pendingTarget;
    private int previousSyncId = -1;
    private long pendingUntil;
    private long queueWatchUntil;
    private String lastTarget;
    private double lastTotal;
    private int lastUnpriced;
    private int lastPricedStacks;
    private long lastInspectionAt;

    public InvSeeValue() {
        super("InvSeeValue", "Zeigt den Wert eines selbst geoeffneten /invsee-Inventars.", Category.MISC);
        loadPrices();
        setEnabled(true);
    }

    private static Path priceFile() {
        return Storage.dir().resolve("invsee_item_values.json");
    }

    private static void loadPrices() {
        if (pricesLoaded) return;
        pricesLoaded = true;
        Path file = priceFile();
        if (!Files.exists(file)) return;
        try {
            Map<String, Double> loaded = GSON.fromJson(Files.readString(file), PRICE_MAP);
            if (loaded != null) PRICES.putAll(loaded);
        } catch (Exception e) {
            dev.nightfall.Nightfall.LOGGER.error("InvSee-Preise konnten nicht geladen werden", e);
        }
    }

    private static void savePrices() {
        loadPrices();
        try {
            Files.writeString(priceFile(), GSON.toJson(PRICES));
        } catch (IOException e) {
            dev.nightfall.Nightfall.LOGGER.error("InvSee-Preise konnten nicht gespeichert werden", e);
        }
    }

    public static void setPrice(String itemId, double price) {
        loadPrices();
        PRICES.put(itemId, Math.max(0, price));
        savePrices();
    }

    public static boolean removePrice(String itemId) {
        loadPrices();
        boolean removed = PRICES.remove(itemId) != null;
        if (removed) savePrices();
        return removed;
    }

    public static Map<String, Double> prices() {
        loadPrices();
        return Collections.unmodifiableMap(PRICES);
    }

    public static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static double price(ItemStack stack) {
        loadPrices();
        return PRICES.getOrDefault(itemId(stack), -1.0);
    }

    /** Observe the player's own queue / invsee commands; no hidden inventory requests are made. */
    public static void onOutgoingCommand(String command) {
        try {
            InvSeeValue module = dev.nightfall.module.ModuleManager.get(InvSeeValue.class);
            if (!module.isEnabled() || command == null) return;
            String trimmed = command.trim().replaceFirst("^/", "");
            if (trimmed.isEmpty()) return;
            String[] parts = trimmed.split("\\s+");
            String root = parts[0].toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();

            if (root.equals("rtpq") || root.equals("rtpqueue")
                    || (root.equals("trigger") && parts.length > 1 && parts[1].equalsIgnoreCase("rtpq"))) {
                module.queueWatchUntil = now + 5 * 60_000L;
                return;
            }
            if (root.equals("invsee") || root.equals("inventorysee") || root.equals("openinv")) {
                if (parts.length < 2 || mc.player == null) return;
                module.pendingTarget = parts[1];
                module.previousSyncId = mc.player.currentScreenHandler.syncId;
                module.pendingUntil = now + 15_000L;
            }
        } catch (IllegalStateException ignored) {
        }
    }

    /** Called for server game/chat messages. Only a queue window opened by /rtpq arms this parser. */
    public static void onGameMessage(String rawMessage) {
        try {
            InvSeeValue module = dev.nightfall.module.ModuleManager.get(InvSeeValue.class);
            if (!module.isEnabled() || !module.autoOpenQueueOpponent.get()
                    || rawMessage == null || System.currentTimeMillis() > module.queueWatchUntil
                    || mc.player == null || mc.getNetworkHandler() == null) return;

            String message = rawMessage.replaceAll("(?i)§[0-9A-FK-OR]", "")
                    .replaceAll("\\[[^\\]]+\\]", " ").replaceAll("\\s+", " ").trim();
            for (Pattern pattern : OPPONENT_PATTERNS) {
                Matcher matcher = pattern.matcher(message);
                if (!matcher.find()) continue;
                String opponent = matcher.group(1);
                if (opponent.equalsIgnoreCase(mc.player.getName().getString())) return;
                if (!opponent.matches("[A-Za-z0-9_]{3,16}")) return;

                module.pendingTarget = opponent;
                module.previousSyncId = mc.player.currentScreenHandler.syncId;
                module.pendingUntil = System.currentTimeMillis() + 15_000L;
                module.queueWatchUntil = 0;
                mc.getNetworkHandler().sendChatCommand("invsee " + opponent);
                mc.player.sendMessage(net.minecraft.text.Text.literal(
                        "[Nightfall] Gegner erkannt: " + opponent + " — /invsee wird geöffnet."), false);
                return;
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private void updateFromVisibleWindow(GenericContainerScreenHandler handler) {
        int targetSlots = Math.min(handler.getRows() * 9, handler.slots.size());
        double total = 0;
        int unpriced = 0;
        int priced = 0;
        for (int i = 0; i < targetSlots; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            double unit = price(stack);
            if (unit < 0) unpriced++;
            else {
                total += unit * stack.getCount();
                priced++;
            }
        }
        lastTarget = pendingTarget == null ? lastTarget : pendingTarget;
        lastTotal = total;
        lastUnpriced = unpriced;
        lastPricedStacks = priced;
        lastInspectionAt = System.currentTimeMillis();
    }

    /** Render hook called by the HandledScreen mixin after vanilla's UI. */
    public static void renderWindow(Screen screen, DrawContext ctx) {
        try {
            InvSeeValue module = dev.nightfall.module.ModuleManager.get(InvSeeValue.class);
            if (!module.isEnabled() || mc.player == null || !(screen instanceof GenericContainerScreen)) return;
            if (module.pendingTarget != null && System.currentTimeMillis() <= module.pendingUntil
                    && mc.player.currentScreenHandler.syncId != module.previousSyncId
                    && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
                module.activeSyncId = handler.syncId;
                module.updateFromVisibleWindow(handler);
                module.pendingTarget = null;
            }
            if (module.activeSyncId == mc.player.currentScreenHandler.syncId
                    && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
                module.updateFromVisibleWindow(handler);
                module.drawValuePanel(ctx, true);
            }
        } catch (IllegalStateException ignored) {
        }
    }

    private int activeSyncId = -1;

    private void drawValuePanel(DrawContext ctx, boolean inWindow) {
        String target = lastTarget == null ? "Spieler" : lastTarget;
        String totalText = NumberFormat.getNumberInstance(Locale.GERMANY).format(lastTotal);
        int x = Math.max(6, ctx.getScaledWindowWidth() - 156);
        int y = 14;
        ctx.fill(x - 5, y - 5, x + 148, y + 43, 0xD9161722);
        ctx.fill(x - 5, y - 5, x + 148, y - 3, 0xFF8D72FF);
        ctx.drawText(mc.textRenderer, "InvSee-Wert", x, y, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, target, x, y + 11, 0xFFB9B4D4, true);
        ctx.drawText(mc.textRenderer, "Gesamt: " + totalText, x, y + 22, 0xFF7DFFB2, true);
        if (showUnknownCount.get())
            ctx.drawText(mc.textRenderer, "Unbewertet: " + lastUnpriced, x, y + 33, 0xFFFFCC80, true);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!showLastResult.get() || mc.player == null || lastTarget == null) return;
        if (System.currentTimeMillis() - lastInspectionAt > 300_000L) return;
        if (mc.currentScreen instanceof GenericContainerScreen) return;
        int x = Math.max(4, ctx.getScaledWindowWidth() - 154);
        int y = 38;
        ctx.fill(x - 5, y - 5, x + 146, y + 32, 0xB9161722);
        ctx.fill(x - 5, y - 5, x + 146, y - 3, 0xFF8D72FF);
        ctx.drawText(mc.textRenderer, "Letztes InvSee: " + lastTarget, x, y, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "Wert: " + NumberFormat.getNumberInstance(Locale.GERMANY).format(lastTotal),
                x, y + 13, 0xFF7DFFB2, true);
    }

    @Override
    public void onTick() {
        if (pendingTarget != null && System.currentTimeMillis() > pendingUntil) pendingTarget = null;
        if (mc.currentScreen == null && activeSyncId != -1) activeSyncId = -1;
    }
}

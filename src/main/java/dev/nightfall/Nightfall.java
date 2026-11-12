package dev.nightfall;

import dev.nightfall.config.Config;
import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.gui.MapScreen;
import dev.nightfall.gui.NightfallGuiScreen;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AntiCheatMonitor;
import dev.nightfall.modules.AutoReconnect;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.SeedCracker;
import dev.nightfall.render.HatsRenderer;
import dev.nightfall.render.SkyVisualRenderer;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.seed.SeedResultListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStarted;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_3417;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nightfall implements ClientModInitializer {
   public static final String NAME = "Nightfall";
   public static final String VERSION = "0.5.15";
   public static final Logger LOGGER = LoggerFactory.getLogger("nightfall");
   private boolean prevGui;
   private boolean prevHud;
   private boolean prevMap;

   public void onInitializeClient() {
      ModuleManager.init();
      Config.load();
      SkyVisualRenderer.register();
      HatsRenderer.register();
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientStarted)client -> SeedCracker.syncState());
      ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
      HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, class_2960.method_60655("nightfall", "hud"), (ctx, tickCounter) -> {
         class_310 mc = class_310.method_1551();
         if (!mc.field_1690.field_1842 && mc.field_1687 != null) {
            ModuleManager.renderHud(ctx, tickCounter.method_60637(false));
         }
      });
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> {
         SeedResultListener.resetForNewWorld();
         ModuleManager.get(AutoReconnect.class).onJoined();
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         ModuleManager.get(AutoReconnect.class).onDisconnected(client.method_1558());
         ModuleManager.onWorldLeave();
         SeedResultListener.resetForNewWorld();
         Config.save();
      });
      ClientReceiveMessageEvents.GAME.register((Game)(message, overlay) -> {
         if (!overlay) {
            String raw = message.getString();
            ModuleManager.get(AutoTpa.class).onMessage(raw);
            ModuleManager.get(AntiCheatMonitor.class).onServerMessage(raw);
         }
      });
      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> Commands.register(dispatcher));
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)c -> {
         ModuleManager.get(BaseFinder.class).saveMarks();
         WaypointManager.save();
         Config.save();
      });
      LOGGER.info("{} {} geladen", "Nightfall", "0.5.15");
   }

   private void onTick(class_310 mc) {
      ModuleManager.get(AutoReconnect.class).tickDisconnected(mc);
      Nightfall.KeySound.tick(
         mc, mc.field_1724 != null && mc.field_1687 != null && (mc.field_1755 instanceof NightfallGuiScreen || mc.field_1755 instanceof HudEditorScreen)
      );
      if (mc.method_22683() != null) {
         long h = mc.method_22683().method_4490();
         boolean g = GLFW.glfwGetKey(h, Config.guiKey) == 1;
         boolean hu = GLFW.glfwGetKey(h, Config.hudKey) == 1;
         boolean m = GLFW.glfwGetKey(h, Config.mapKey) == 1;
         if (mc.field_1755 == null && mc.field_1724 != null) {
            if (g && !this.prevGui) {
               open(mc, new NightfallGuiScreen());
            } else if (hu && !this.prevHud) {
               open(mc, new HudEditorScreen());
            } else if (m && !this.prevMap) {
               open(mc, new MapScreen());
            }
         }

         this.prevGui = g;
         this.prevHud = hu;
         this.prevMap = m;
         ModuleManager.pollKeys(mc);
      }

      ModuleManager.tick();
   }

   private static void open(class_310 mc, class_437 s) {
      mc.method_1507(s);
   }

   private static final class KeySound {
      private static final boolean[] PREVIOUS = new boolean[349];
      private static boolean wasInUi;

      public static void tick(class_310 mc, boolean inUi) {
         if (mc.method_22683() != null) {
            long handle = mc.method_22683().method_4490();
            if (!inUi) {
               wasInUi = false;
               sync(handle);
            } else if (!wasInUi) {
               sync(handle);
               wasInUi = true;
            } else {
               for (int key = 32; key <= 348; key++) {
                  boolean down = GLFW.glfwGetKey(handle, key) == 1;
                  if (down && !PREVIOUS[key]) {
                     play(mc, key);
                  }

                  PREVIOUS[key] = down;
               }
            }
         }
      }

      private static void sync(long handle) {
         for (int key = 32; key <= 348; key++) {
            PREVIOUS[key] = GLFW.glfwGetKey(handle, key) == 1;
         }
      }

      private static void play(class_310 mc, int key) {
         if (Config.keySounds && mc.field_1724 != null) {
            float pitch = 0.88F + key * 37 % 17 / 100.0F;
            mc.field_1724.method_5783((class_3414)class_3417.field_15015.comp_349(), 0.16F, pitch);
         }
      }
   }
}

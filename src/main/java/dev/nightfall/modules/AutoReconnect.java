package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/** Reconnects to the last server after an unexpected disconnect, with a bounded retry count. */
public final class AutoReconnect extends Module {
    private final NumberSetting delaySeconds = add(new NumberSetting("Verzoegerung (Sek.)", 5, 2, 60, 1));
    private final NumberSetting maxAttempts = add(new NumberSetting("Max. Versuche", 3, 1, 10, 1));
    private ServerInfo lastServer;
    private int attempts;
    private int ticksRemaining;
    private int screenGrace;
    private boolean pending;

    public AutoReconnect() {
        super("AutoReconnect", "Verbindet nach einem unerwarteten Disconnect begrenzt erneut.", Category.MISC);
    }

    @Override protected void onEnable() {
        attempts = 0;
        pending = false;
        lastServer = null;
    }

    @Override protected void onDisable() {
        pending = false;
        lastServer = null;
        attempts = 0;
    }

    public void onDisconnected(ServerInfo server) {
        if (!isEnabled() || server == null) return;
        if (lastServer == null || attempts == 0) lastServer = server;
        if (attempts >= maxAttempts.getInt()) {
            pending = false;
            NotificationCenter.push("AutoReconnect", "Maximale Anzahl an Verbindungsversuchen erreicht.");
            return;
        }
        pending = true;
        ticksRemaining = delaySeconds.getInt() * 20;
        screenGrace = 0;
    }

    public void onJoined() {
        pending = false;
        attempts = 0;
        lastServer = null;
    }

    /** Called every client tick, including while no world/player is loaded. */
    public void tickDisconnected(MinecraftClient client) {
        if (!isEnabled() || !pending || lastServer == null || client.getNetworkHandler() != null) return;
        if (!(client.currentScreen instanceof DisconnectedScreen)) {
            // Manual exits lead to the title screen, not DisconnectedScreen. Do not reconnect those.
            if (++screenGrace > 40) pending = false;
            return;
        }
        if (ticksRemaining > 0) {
            ticksRemaining--;
            return;
        }
        if (attempts >= maxAttempts.getInt()) {
            pending = false;
            NotificationCenter.push("AutoReconnect", "Verbindung nach " + attempts + " Versuchen abgebrochen.");
            return;
        }
        attempts++;
        pending = false;
        NotificationCenter.push("AutoReconnect", "Verbindungsversuch " + attempts + "/" + maxAttempts.getInt());
        ConnectScreen.connect(client.currentScreen, client, ServerAddress.parse(lastServer.address), lastServer, false, null);
    }
}

package dev.nightfall.util;

import dev.nightfall.Nightfall;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Storage {
    private Storage() {}

    public static Path dir() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("nightfall");
        try { Files.createDirectories(d); } catch (IOException e) { Nightfall.LOGGER.error("Ordner nicht erstellbar: {}", d, e); }
        return d;
    }

    /** Dateisicherer Schluessel fuer den aktuellen Server bzw. Singleplayer. */
    public static String server() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo si = mc.getCurrentServerEntry();
        String s = si != null ? si.address : (mc.isInSingleplayer() ? "singleplayer" : "unknown");
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static String dim() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.world == null ? "none" : mc.world.getRegistryKey().getValue().getPath();
    }
}

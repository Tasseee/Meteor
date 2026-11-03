package dev.nightfall.util;

import net.minecraft.client.MinecraftClient;

import java.util.Locale;

/**
 * Optional compatibility guard for public HugoSMP Anti-ESP behavior.
 *
 * The guard is deliberately conservative: it only activates on the hugosmp.net host
 * (or one of its subdomains) and only while the local player is above Y=5.
 */
public final class HugoSmpCompat {
    private static final String DOMAIN = "hugosmp.net";

    private HugoSmpCompat() {}

    public static boolean isHugoSmpServer() {
        MinecraftClient mc = MinecraftClient.getInstance();
        var server = mc.getCurrentServerEntry();
        if (server == null || server.address == null) return false;

        String host = server.address.trim().toLowerCase(Locale.ROOT);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);

        return host.equals(DOMAIN) || host.endsWith("." + DOMAIN);
    }

    /**
     * A public community Anti-ESP implementation describes fake block updates at Y<=0
     * while the player is above Y=5. When enabled, scanners should not score that region.
     */
    public static boolean undergroundBlocksMayBeMasked() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return isHugoSmpServer() && mc.player != null && mc.player.getBlockY() > 5;
    }
}

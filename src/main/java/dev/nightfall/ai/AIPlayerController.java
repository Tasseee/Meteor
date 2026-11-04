package dev.nightfall.ai;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Small, bounded client-side movement primitive for explicit AI commands.
 * It deliberately does not pretend to provide obstacle-aware pathfinding.
 */
public final class AIPlayerController {
    private static int forwardTicks;
    private static boolean walking;

    private AIPlayerController() {}

    public static String walkForward(int ticks) {
        if (ticks < 1 || ticks > 100) throw new IllegalArgumentException("Die Laufdauer muss zwischen 1 und 100 Ticks liegen.");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null)
            throw new IllegalArgumentException("Zum Laufen musst du in einer Welt sein und das Chat-/Menüfenster schließen.");
        if (walking) stop("Vorherige Bewegung beendet.");
        forwardTicks = ticks;
        walking = true;
        mc.player.sendMessage(Text.literal("[Nightfall] Laufe geradeaus für " + ticks + " Ticks. Mit „Stopp“ kannst du abbrechen."), false);
        return "Vorwärtsbewegung gestartet (" + ticks + " Ticks). Das ist eine kurze Geradeausbewegung ohne Hindernis-Umgehung.";
    }

    public static void tick() {
        if (!walking) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.player.isDead() || mc.player.isSubmergedInWater()) {
            stop("Bewegung aus Sicherheitsgründen gestoppt.");
            return;
        }
        mc.options.forwardKey.setPressed(true);
        if (--forwardTicks <= 0) stop("Bewegung abgeschlossen.");
    }

    public static String stop(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) mc.options.forwardKey.setPressed(false);
        boolean wasWalking = walking;
        walking = false;
        forwardTicks = 0;
        if (wasWalking && mc.player != null && reason != null)
            mc.player.sendMessage(Text.literal("[Nightfall] " + reason), false);
        return wasWalking ? "Bewegung gestoppt." : "Nightfall bewegt dich gerade nicht.";
    }

    public static void onDisconnect() { stop(null); }
    public static boolean isWalking() { return walking; }
}

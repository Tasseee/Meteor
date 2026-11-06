package dev.nightfall.ai;

import dev.nightfall.gui.AIChatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;

/**
 * Bounded melee combat against nearby vanilla hostile mobs.
 * Players, passive mobs and neutral mobs are never selected.
 */
public final class AICombatController {
    private static final double ACQUIRE_RANGE = 12.0;
    private static final double GUARD_RANGE = 10.0;
    private static final double MELEE_RANGE = 3.35;
    private static final int MAX_ACTIVE_TICKS = 20 * 45;
    private static final int MAX_ATTACKS = 12;
    private static final int MAX_GUARD_TICKS = 20 * 90;
    private static final int MAX_GUARD_ATTACKS = 24;

    private static HostileEntity target;
    private static boolean active;
    private static boolean guardMode;
    private static int activeTicks;
    private static int attacks;

    private AICombatController() {}

    public static String preview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null)
            throw new IllegalArgumentException("Du musst dich in einer verbundenen Welt befinden.");
        HostileEntity nearest = findNearest(mc);
        if (nearest == null)
            throw new IllegalArgumentException("Kein feindlicher Mob innerhalb von " + (int) ACQUIRE_RANGE + " Blöcken gefunden.");
        return "Nightfall greift nach deiner Bestätigung den feindlichen Mob "
                + nearest.getType().getName().getString() + " in deiner Nähe an. "
                + "Nightfall sucht nur HostileEntity-Mobs, verfolgt das Ziel auf einem sicheren lokalen Weg "
                + "und stoppt nach höchstens " + MAX_ATTACKS + " Treffern oder 45 Sekunden. "
                + "Spieler und neutrale Tiere werden nicht angegriffen.";
    }

    /** Preview a short, bounded escort-defense mode around the player's current position. */
    public static String previewGuard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null)
            throw new IllegalArgumentException("Du musst dich in einer verbundenen Welt befinden.");
        HostileEntity nearest = findNearest(mc, GUARD_RANGE);
        if (nearest == null)
            throw new IllegalArgumentException("Kein feindlicher Mob innerhalb von " + (int) GUARD_RANGE + " Blöcken gefunden.");
        return "Nightfall übernimmt nach deiner Bestätigung vorübergehend deine Bewegung und wehrt feindliche Mobs "
                + "im Umkreis von " + (int) GUARD_RANGE + " Blöcken ab. Erstes Ziel: "
                + nearest.getType().getName().getString() + ". Der Wachmodus endet nach höchstens 90 Sekunden "
                + "oder 24 Angriffen. Es werden ausschließlich HostileEntity-Mobs angegriffen, niemals Spieler "
                + "oder neutrale/passive Tiere. Deine manuelle Bewegung ist währenddessen vorübergehend eingeschränkt; „Stopp“ beendet den Modus sofort.";
    }

    /** Start a confirmed, bounded nearby-hostile defense mode. */
    public static String startGuard() {
        MinecraftClient mc = MinecraftClient.getInstance();
        previewGuard();
        stop(null);
        target = findNearest(mc, GUARD_RANGE);
        if (target == null) throw new IllegalArgumentException("Es ist kein feindlicher Mob mehr in Reichweite.");
        active = true;
        guardMode = true;
        activeTicks = 0;
        attacks = 0;
        notifyPlayer(mc, "Wachmodus gestartet · wehre nahe Gegner ab. „Stopp“ beendet ihn.");
        return "Wachmodus gestartet. Nightfall verteidigt dich bis zu 90 Sekunden oder 24 Angriffe lang und greift ausschließlich nahe feindliche Mobs an.";
    }

    public static String start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        preview();
        stop(null);
        target = findNearest(mc);
        if (target == null) throw new IllegalArgumentException("Das Ziel ist nicht mehr in Reichweite.");
        active = true;
        guardMode = false;
        activeTicks = 0;
        attacks = 0;
        notifyPlayer(mc, "Kampf gestartet · " + target.getType().getName().getString()
                + ". „Stopp“ beendet den Kampf.");
        return "Kampf gestartet gegen " + target.getType().getName().getString()
                + ". Begrenzung: " + MAX_ATTACKS + " Angriffe oder 45 Sekunden.";
    }

    public static void tick() {
        if (!active) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.player.isDead() || mc.player.hasVehicle()
                || (mc.currentScreen != null && !(mc.currentScreen instanceof AIChatScreen))) {
            stop("Kampf aus Sicherheitsgründen gestoppt.");
            return;
        }
        int tickLimit = guardMode ? MAX_GUARD_TICKS : MAX_ACTIVE_TICKS;
        int attackLimit = guardMode ? MAX_GUARD_ATTACKS : MAX_ATTACKS;
        double acquireRange = guardMode ? GUARD_RANGE : ACQUIRE_RANGE;
        if (++activeTicks > tickLimit || attacks >= attackLimit) {
            stop("Kampf beendet · Sicherheitslimit erreicht.");
            return;
        }

        if (target == null || !target.isAlive() || target.isRemoved()
                || mc.player.squaredDistanceTo(target.getX(), target.getY(), target.getZ()) > (acquireRange + 6.0) * (acquireRange + 6.0)) {
            target = findNearest(mc, acquireRange);
            if (target == null) {
                stop("Kein feindlicher Mob mehr in der Nähe. Kampf beendet.");
                return;
            }
        }

        double distanceSquared = mc.player.squaredDistanceTo(target.getX(), target.getY(), target.getZ());
        if (distanceSquared > MELEE_RANGE * MELEE_RANGE) {
            if (!AIPlayerController.isNavigating()
                    && !AIPlayerController.navigateNear(target.getBlockPos(), 1)) {
                stop("Kein sicherer Weg zum feindlichen Mob gefunden. Kampf beendet.");
            }
            return;
        }

        if (AIPlayerController.isNavigating()) AIPlayerController.stop(null);
        faceTarget(mc, target);
        if (!mc.player.canSee(target)) {
            if (!AIPlayerController.navigateNear(target.getBlockPos(), 1))
                stop("Sichtlinie zum Ziel blockiert. Kampf beendet.");
            return;
        }

        if (mc.player.getAttackCooldownProgress(0.5f) >= 0.95f) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            attacks++;
            if (attacks >= MAX_ATTACKS)
                stop("Kampf beendet · " + attacks + " Angriffe ausgeführt.");
        }
    }

    public static String stop(String reason) {
        boolean wasActive = active;
        active = false;
        target = null;
        activeTicks = 0;
        attacks = 0;
        guardMode = false;
        if (AIPlayerController.isNavigating()) AIPlayerController.stop(null);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (wasActive && mc.player != null && reason != null) notifyPlayer(mc, reason);
        return wasActive ? "Kampf beendet." : "Nightfall kämpft gerade nicht.";
    }

    public static void onDisconnect() { stop(null); }
    public static boolean isActive() { return active; }

    private static HostileEntity findNearest(MinecraftClient mc) {
        return findNearest(mc, ACQUIRE_RANGE);
    }

    private static HostileEntity findNearest(MinecraftClient mc, double range) {
        if (mc.player == null || mc.world == null) return null;
        List<HostileEntity> hostiles = mc.world.getEntitiesByClass(HostileEntity.class,
                mc.player.getBoundingBox().expand(range), entity -> entity.isAlive() && !entity.isRemoved());
        return hostiles.stream()
                .min(Comparator.comparingDouble(entity -> mc.player.squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ())))
                .orElse(null);
    }

    private static void faceTarget(MinecraftClient mc, HostileEntity entity) {
        Vec3d delta = entity.getBoundingBox().getCenter().subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0));
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(delta.y, horizontal)));
    }

    private static void notifyPlayer(MinecraftClient mc, String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
    }
}

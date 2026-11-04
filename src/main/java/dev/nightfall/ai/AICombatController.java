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
    private static final double MELEE_RANGE = 3.35;
    private static final int MAX_ACTIVE_TICKS = 20 * 45;
    private static final int MAX_ATTACKS = 12;

    private static HostileEntity target;
    private static boolean active;
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

    public static String start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        preview();
        stop(null);
        target = findNearest(mc);
        if (target == null) throw new IllegalArgumentException("Das Ziel ist nicht mehr in Reichweite.");
        active = true;
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
        if (++activeTicks > MAX_ACTIVE_TICKS || attacks >= MAX_ATTACKS) {
            stop("Kampf beendet · Sicherheitslimit erreicht.");
            return;
        }

        if (target == null || !target.isAlive() || target.isRemoved()
                || mc.player.getPos().squaredDistanceTo(target.getPos()) > (ACQUIRE_RANGE + 6.0) * (ACQUIRE_RANGE + 6.0)) {
            target = findNearest(mc);
            if (target == null) {
                stop("Kein feindlicher Mob mehr in der Nähe. Kampf beendet.");
                return;
            }
        }

        double distanceSquared = mc.player.getPos().squaredDistanceTo(target.getPos());
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
        if (AIPlayerController.isNavigating()) AIPlayerController.stop(null);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (wasActive && mc.player != null && reason != null) notifyPlayer(mc, reason);
        return wasActive ? "Kampf beendet." : "Nightfall kämpft gerade nicht.";
    }

    public static void onDisconnect() { stop(null); }
    public static boolean isActive() { return active; }

    private static HostileEntity findNearest(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return null;
        List<HostileEntity> hostiles = mc.world.getEntitiesByClass(HostileEntity.class,
                mc.player.getBoundingBox().expand(ACQUIRE_RANGE), entity -> entity.isAlive() && !entity.isRemoved());
        return hostiles.stream()
                .min(Comparator.comparingDouble(entity -> mc.player.getPos().squaredDistanceTo(entity.getPos())))
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

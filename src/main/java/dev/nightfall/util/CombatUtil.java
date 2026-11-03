package dev.nightfall.util;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.KillAura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class CombatUtil {
    private CombatUtil() {}

    /**
     * GESCHAETZTER Explosionsschaden (Vanilla-Formel ohne Sichtlinien-Exposure und Schwierigkeitsgrad,
     * Ruestung vereinfacht). Exposure = 1 angenommen -> eher Ueberschaetzung (sicher fuer Eigenschutz).
     */
    public static double explosionDamage(Vec3d center, LivingEntity e, double power) {
        double q = power * 2.0;
        double d = e.getEntityPos().distanceTo(center) / q;
        if (d > 1.0) return 0;
        double impact = 1.0 - d;
        double dmg = (impact * impact + impact) / 2.0 * 7.0 * q + 1.0;
        double armor = e.getArmor();
        double f = Math.min(20.0, Math.max(armor / 5.0, armor - dmg / 2.0)) / 25.0;
        return dmg * (1.0 - f);
    }

    public static double effectiveHealth(LivingEntity e) { return e.getHealth() + e.getAbsorptionAmount(); }

    public static boolean isFriend(PlayerEntity p) {
        return ModuleManager.get(KillAura.class).friends.has(p.getName().getString());
    }

    public static PlayerEntity nearestPlayer(double range) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity me = mc.player;
        if (me == null || mc.world == null) return null;
        PlayerEntity best = null;
        double bd = range * range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == me || !p.isAlive() || p.isSpectator() || isFriend(p)) continue;
            double d = p.squaredDistanceTo(me);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }
}

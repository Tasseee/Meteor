package dev.nightfall.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.Vec3d;

public final class MoveUtil {
    private MoveUtil() {}

    /** Normalisierte XZ-Bewegungsrichtung aus den WASD-Tasten; Vec3d.ZERO wenn keine Taste gedrueckt. */
    public static Vec3d direction() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return Vec3d.ZERO;
        GameOptions o = mc.options;
        float f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        float s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(mc.player.getYaw());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = -sin * f + cos * s;
        double z = cos * f + sin * s;
        double len = Math.sqrt(x * x + z * z);
        return new Vec3d(x / len, 0, z / len);
    }

    public static boolean isMoving() { return direction() != Vec3d.ZERO; }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Detached-position camera. The player entity stays at its server-side position;
 * only the rendered camera is translated locally.
 */
public final class Freecam extends Module {
    private final NumberSetting speed = add(new NumberSetting("Geschwindigkeit", 0.5, 0.05, 2.0, 0.05));
    private static double offsetX, offsetY, offsetZ;

    public Freecam() {
        super("Freecam", "Freie Kamera ohne den Spieler durch die Welt zu bewegen.", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        offsetX = offsetY = offsetZ = 0;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        double amount = speed.get();
        if (mc.options.sprintKey.isPressed()) amount *= 2.0;

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0)
                - (mc.options.backKey.isPressed() ? 1 : 0);
        double sideways = (mc.options.leftKey.isPressed() ? 1 : 0)
                - (mc.options.rightKey.isPressed() ? 1 : 0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1 : 0)
                - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(player.getYaw());
        double pitch = Math.toRadians(player.getPitch());
        double horizontalLength = Math.sqrt(forward * forward + sideways * sideways);
        if (horizontalLength > 0) {
            forward /= horizontalLength;
            sideways /= horizontalLength;
        }

        double cosPitch = Math.cos(pitch);
        double dx = -Math.sin(yaw) * cosPitch * forward + Math.cos(yaw) * sideways;
        double dy = -Math.sin(pitch) * forward + vertical;
        double dz = Math.cos(yaw) * cosPitch * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 0) {
            dx /= length;
            dy /= length;
            dz /= length;
        }

        offsetX += dx * amount;
        offsetY += dy * amount;
        offsetZ += dz * amount;
    }

    @Override
    protected void onDisable() {
        offsetX = offsetY = offsetZ = 0;
    }

    public static double offsetX() { return offsetX; }
    public static double offsetY() { return offsetY; }
    public static double offsetZ() { return offsetZ; }
}

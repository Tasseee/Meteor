package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Client-side no-clip movement. This changes local collision only; multiplayer
 * servers remain authoritative and may correct or reject the resulting position.
 */
public final class Noclip extends Module {
    private final NumberSetting speed = add(new NumberSetting("Geschwindigkeit", 0.35, 0.05, 1.5, 0.05));
    private boolean previousNoClip;
    private boolean previousNoGravity;

    public Noclip() {
        super("Noclip", "Bewegt den Spieler lokal durch Bloecke (Server kann Position korrigieren).", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        previousNoClip = player.noClip;
        previousNoGravity = player.hasNoGravity();
        player.noClip = true;
        player.setNoGravity(true);
        player.setVelocity(0, 0, 0);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        player.noClip = true;
        player.setNoGravity(true);
        player.setVelocity(player.getVelocity().multiply(0.0));
        double velocity = speed.get();
        if (mc.options.sprintKey.isPressed()) velocity *= 1.75;
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0)
                - (mc.options.backKey.isPressed() ? 1 : 0);
        double sideways = (mc.options.leftKey.isPressed() ? 1 : 0)
                - (mc.options.rightKey.isPressed() ? 1 : 0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1 : 0)
                - (mc.options.sneakKey.isPressed() ? 1 : 0);
        double yaw = Math.toRadians(player.getYaw());
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * sideways;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(x * x + z * z);
        if (length > 0) { x /= length; z /= length; }
        player.setVelocity(x * velocity, vertical * velocity, z * velocity);
        player.velocityModified = true;
    }

    @Override
    protected void onDisable() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        player.noClip = previousNoClip;
        player.setNoGravity(previousNoGravity);
        player.setVelocity(0, 0, 0);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;

/** Client-side free look with separate rendered camera angles. */
public final class FreeLook extends Module {
    private static float viewYaw;
    private static float viewPitch;

    public FreeLook() {
        super("FreeLook", "Kamera unabhängig von der Blickrichtung des Spielers bewegen.", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            viewYaw = mc.player.getYaw();
            viewPitch = mc.player.getPitch();
        }
    }

    public static boolean isActive() {
        try {
            return dev.nightfall.module.ModuleManager.get(FreeLook.class).isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void captureMouseLook(ClientPlayerEntity player, float originalYaw, float originalPitch) {
        if (player == null) return;
        viewYaw = player.getYaw();
        viewPitch = Math.max(-90.0f, Math.min(90.0f, player.getPitch()));
        player.setYaw(originalYaw);
        player.setPitch(originalPitch);
    }

    public static float getViewYaw(float fallback) {
        return isActive() ? viewYaw : fallback;
    }

    public static float getViewPitch(float fallback) {
        return isActive() ? viewPitch : fallback;
    }
}

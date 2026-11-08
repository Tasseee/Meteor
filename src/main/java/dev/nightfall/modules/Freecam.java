package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Independent, movable render camera. The player entity is kept at its start position. */
public final class Freecam extends Module {
    private static Vec3d cameraPosition = Vec3d.ZERO;
    private static boolean active;
    private Vec3d playerStart = Vec3d.ZERO;
    private float savedYaw, savedPitch;
    private final NumberSetting speed = add(new NumberSetting("Geschwindigkeit", 0.6, 0.1, 3.0, 0.1));

    public Freecam() {
        super("Freecam", "Bewegt die Kamera unabhaengig vom Spieler durch die Welt.", Category.MOVEMENT);
    }

    public static boolean isActive() { return active; }
    public static Vec3d cameraPosition() { return cameraPosition; }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        try {
            FreeLook look = dev.nightfall.module.ModuleManager.get(FreeLook.class);
            if (look.isEnabled()) look.setEnabled(false);
        } catch (IllegalStateException ignored) {}
        playerStart = mc.player.getPos();
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        cameraPosition = mc.player.getEyePos();
        active = true;
    }

    @Override
    public void onTick() {
        if (!active || mc.player == null || mc.getWindow() == null) return;
        long window = mc.getWindow().getHandle();
        float yaw = (float) Math.toRadians(savedYaw);
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d move = Vec3d.ZERO;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) move = move.add(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) move = move.subtract(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) move = move.add(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) move = move.subtract(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) move = move.add(0, 1, 0);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) move = move.add(0, -1, 0);
        if (move.lengthSquared() > 0) cameraPosition = cameraPosition.add(move.normalize().multiply(speed.get()));

        mc.player.setPosition(playerStart);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
    }

    @Override
    protected void onDisable() {
        active = false;
        if (mc.player != null) {
            mc.player.setPosition(playerStart);
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Independent camera with its own mouse rotation and movement; the player stays put. */
public final class Freecam extends Module {
    private static Vec3d cameraPosition = Vec3d.ZERO;
    private static Vec3d previousCameraPosition = Vec3d.ZERO;
    private static float previousCameraYaw, previousCameraPitch;
    private long lastMoveNanos;
    private static boolean active;
    private static float cameraYaw, cameraPitch;
    private Vec3d playerStart = Vec3d.ZERO;
    private float savedYaw, savedPitch;
    private double lastMouseX, lastMouseY;
    private boolean mouseInitialized;
    private final NumberSetting speed = add(new NumberSetting("Geschwindigkeit", 0.45, 0.1, 2.0, 0.05));
    private final NumberSetting sensitivity = add(new NumberSetting("Kamera-Sensitivitaet", 0.15, 0.03, 0.5, 0.01));

    public Freecam() {
        super("Freecam", "Bewegt und dreht die Kamera unabhaengig vom Spieler durch die Welt.", Category.MOVEMENT);
    }

    public static boolean isActive() { return active; }
    public static Vec3d cameraPosition() { return cameraPosition; }
    public static float cameraYaw() { return cameraYaw; }
    public static float cameraPitch() { return cameraPitch; }
    public static Vec3d interpolatedPosition(float tickDelta) {
        return previousCameraPosition.lerp(cameraPosition, Math.max(0.0f, Math.min(1.0f, tickDelta)));
    }
    public static float interpolatedYaw(float tickDelta) {
        float difference = (cameraYaw - previousCameraYaw) % 360.0f;
        if (difference >= 180.0f) difference -= 360.0f;
        if (difference < -180.0f) difference += 360.0f;
        return previousCameraYaw + difference * Math.max(0.0f, Math.min(1.0f, tickDelta));
    }
    public static float interpolatedPitch(float tickDelta) {
        return previousCameraPitch + (cameraPitch - previousCameraPitch) * Math.max(0.0f, Math.min(1.0f, tickDelta));
    }

    private boolean activateIfReady() {
        if (mc.player == null || mc.world == null) return false;
        try {
            FreeLook look = dev.nightfall.module.ModuleManager.get(FreeLook.class);
            if (look.isEnabled()) look.setEnabled(false);
        } catch (IllegalStateException ignored) {}
        playerStart = mc.player.getEntityPos();
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        cameraYaw = savedYaw;
        cameraPitch = savedPitch;
        previousCameraYaw = cameraYaw;
        previousCameraPitch = cameraPitch;
        // Start just behind the player so the local model is visible immediately.
        Vec3d lookDirection = new Vec3d(-Math.sin(Math.toRadians(cameraYaw)), 0,
                Math.cos(Math.toRadians(cameraYaw)));
        cameraPosition = mc.player.getEyePos().subtract(lookDirection.multiply(2.5));
        previousCameraPosition = cameraPosition;
        lastMoveNanos = System.nanoTime();
        mouseInitialized = false;
        active = true;
        return true;
    }

    @Override
    protected void onEnable() {
        // Config may enable modules before a world/player exists. onTick retries.
        activateIfReady();
    }

    @Override
    public void onTick() {
        if (!active) {
            activateIfReady();
            return;
        }
        if (mc.player == null || mc.world == null || mc.getWindow() == null) return;
        long window = mc.getWindow().getHandle();
        previousCameraPosition = cameraPosition;
        previousCameraYaw = cameraYaw;
        previousCameraPitch = cameraPitch;
        long now = System.nanoTime();
        double frameSeconds = Math.max(0.0, Math.min(0.05, (now - lastMoveNanos) / 1_000_000_000.0));
        lastMoveNanos = now;
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        if (mouseInitialized) {
            double dx = Math.max(-100.0, Math.min(100.0, x[0] - lastMouseX));
            double dy = Math.max(-100.0, Math.min(100.0, y[0] - lastMouseY));
            cameraYaw += (float) (dx * sensitivity.get());
            cameraPitch += (float) (dy * sensitivity.get());
            cameraPitch = Math.max(-90.0f, Math.min(90.0f, cameraPitch));
        }
        lastMouseX = x[0];
        lastMouseY = y[0];
        mouseInitialized = true;

        float yaw = (float) Math.toRadians(cameraYaw);
        float pitch = (float) Math.toRadians(cameraPitch);
        Vec3d forward = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d move = Vec3d.ZERO;

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) move = move.add(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) move = move.subtract(forward);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) move = move.add(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) move = move.subtract(right);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) move = move.add(0, 1, 0);
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) move = move.add(0, -1, 0);
        if (move.lengthSquared() > 0) {
            // Speed is measured in blocks per tick, scaled by elapsed time for stable movement.
            cameraPosition = cameraPosition.add(move.normalize().multiply(speed.get() * 20.0 * frameSeconds));
        }

        // Keep the player anchored independently of camera movement and placement input.
        if (mc.player.getEntityPos().squaredDistanceTo(playerStart) > 1.0E-8) {
            mc.player.setPosition(playerStart);
        }
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
    }

    @Override
    public void onWorldLeave() {
        active = false;
        mouseInitialized = false;
        cameraPosition = Vec3d.ZERO;
        previousCameraPosition = Vec3d.ZERO;
        lastMoveNanos = 0L;
    }

    @Override
    protected void onDisable() {
        boolean wasActive = active;
        active = false;
        // Only restore a position if this session actually initialized Freecam.
        if (wasActive && mc.player != null) {
            mc.player.setPosition(playerStart);
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
        }
        mouseInitialized = false;
        previousCameraPosition = Vec3d.ZERO;
        lastMoveNanos = 0L;
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** Independent camera with its own mouse rotation and movement; the player stays put. */
public final class Freecam extends Module {
    private static Vec3d cameraPosition = Vec3d.ZERO;
    private static boolean active;
    private static float cameraYaw, cameraPitch;
    private Vec3d playerStart = Vec3d.ZERO;
    private float savedYaw, savedPitch;
    private double lastMouseX, lastMouseY;
    private boolean mouseInitialized;
    private final NumberSetting speed = add(new NumberSetting("Geschwindigkeit", 0.6, 0.1, 3.0, 0.1));

    public Freecam() {
        super("Freecam", "Bewegt und dreht die Kamera unabhaengig vom Spieler durch die Welt.", Category.MOVEMENT);
    }

    public static boolean isActive() { return active; }
    public static Vec3d cameraPosition() { return cameraPosition; }
    public static float cameraYaw() { return cameraYaw; }
    public static float cameraPitch() { return cameraPitch; }

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
        cameraPosition = mc.player.getEyePos();
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
        double[] x = new double[1], y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        if (mouseInitialized) {
            cameraYaw += (float) ((x[0] - lastMouseX) * 0.15);
            cameraPitch += (float) ((y[0] - lastMouseY) * 0.15);
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
        if (move.lengthSquared() > 0) cameraPosition = cameraPosition.add(move.normalize().multiply(speed.get()));

        // Keep the actual player fixed while the detached camera moves.
        mc.player.setPosition(playerStart);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
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
    }
}

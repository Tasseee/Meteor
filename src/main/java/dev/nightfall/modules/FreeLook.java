package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Entkoppelt die Renderkamera von der Spielerausrichtung.
 * Die Maus bewegt nur die Kamera; die gespeicherte Spielerausrichtung wird gehalten.
 */
public final class FreeLook extends Module {
    private static boolean active;
    private static float cameraYaw, cameraPitch;
    private float playerYaw, playerPitch;
    private double lastMouseX, lastMouseY;
    private boolean mouseInitialized;

    public FreeLook() {
        super("FreeLook", "Mit der Maus umsehen, ohne die Blickrichtung des Spielers zu aendern.", Category.VISUALS);
    }

    public static boolean isActive() { return active; }
    public static float cameraYaw() { return cameraYaw; }
    public static float cameraPitch() { return cameraPitch; }

    @Override
    protected void onEnable() {
        if (mc.player == null) return;
        Freecam freecam = null;
        try { freecam = dev.nightfall.module.ModuleManager.get(Freecam.class); }
        catch (IllegalStateException ignored) {}
        if (freecam != null && freecam.isEnabled()) freecam.setEnabled(false);

        playerYaw = mc.player.getYaw();
        playerPitch = mc.player.getPitch();
        cameraYaw = playerYaw;
        cameraPitch = playerPitch;
        mouseInitialized = false;
        active = true;
    }

    @Override
    public void onTick() {
        if (!active || mc.player == null || mc.getWindow() == null) return;
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

        // Undo vanilla mouse rotation for the local player. The mixin applies our
        // independent angles to the render camera only.
        mc.player.setYaw(playerYaw);
        mc.player.setPitch(playerPitch);
    }

    @Override
    protected void onDisable() {
        active = false;
        if (mc.player != null) {
            mc.player.setYaw(playerYaw);
            mc.player.setPitch(playerPitch);
        }
    }
}

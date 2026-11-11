package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Hats;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws the local player's cosmetic in the 3D world render, never as a HUD or on other players. */
public final class HatsRenderer {
    private static final int STRANDS = 3;
    private static final int BEADS_PER_STRAND = 24;
    private HatsRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(HatsRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || context.matrices() == null || context.consumers() == null) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        Hats hats;
        try { hats = ModuleManager.get(Hats.class); }
        catch (Throwable ignored) { return; }
        if (!hats.isEnabled()) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double scale = hats.size();
        double baseY = mc.player.getY() + mc.player.getHeight() + hats.height();
        double phase = System.nanoTime() * 1.0e-9 * hats.rotationSpeed() * Math.PI * 2.0;

        MatrixStack matrices = context.matrices();
        VertexConsumer fills = context.consumers().getBuffer(RenderLayers.debugFilledBox());
        int rgb = hats.color();
        float red = ((rgb >> 16) & 255) / 255.0f;
        float green = ((rgb >> 8) & 255) / 255.0f;
        float blue = (rgb & 255) / 255.0f;

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        try {
            for (int strand = 0; strand < STRANDS; strand++) {
                double offset = strand * (Math.PI * 2.0 / STRANDS);
                for (int i = 0; i <= BEADS_PER_STRAND; i++) {
                    double progress = i / (double) BEADS_PER_STRAND;
                    double angle = phase + offset + progress * Math.PI * 2.4;
                    double radius = scale * (0.38 * (1.0 - progress) + 0.065);
                    double x = playerPos.x + Math.cos(angle) * radius;
                    double y = baseY + progress * 0.78 * scale;
                    double z = playerPos.z + Math.sin(angle) * radius;
                    double bead = scale * (0.028 + 0.012 * (1.0 - progress));
                    float brightness = (float) (0.66 + 0.34 * Math.sin(Math.PI * (0.15 + progress * 0.7)));
                    drawCube(matrices, fills, x, y, z, bead,
                            Math.min(1.0f, red * brightness), Math.min(1.0f, green * brightness),
                            Math.min(1.0f, blue * brightness), 0.96f);
                }
            }
        } finally {
            matrices.pop();
        }
    }

    private static void drawCube(MatrixStack matrices, VertexConsumer consumer,
                                 double x, double y, double z, double halfSize,
                                 float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x0 = (float) (x - halfSize), x1 = (float) (x + halfSize);
        float y0 = (float) (y - halfSize), y1 = (float) (y + halfSize);
        float z0 = (float) (z - halfSize), z1 = (float) (z + halfSize);
        quad(consumer, matrix, red, green, blue, alpha, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(consumer, matrix, red, green, blue, alpha, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
        quad(consumer, matrix, red, green, blue, alpha, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        quad(consumer, matrix, red, green, blue, alpha, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        quad(consumer, matrix, red, green, blue, alpha, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        quad(consumer, matrix, red, green, blue, alpha, x1,y0,z1, x1,y1,z1, x0,y1,z1, x0,y0,z1);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             float red, float green, float blue, float alpha,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        consumer.vertex(matrix, x0, y0, z0).color(red, green, blue, alpha);
        consumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha);
        consumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha);
        consumer.vertex(matrix, x3, y3, z3).color(red, green, blue, alpha);
    }
}

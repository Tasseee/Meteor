package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SchematicBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Bounded wireframe preview for unbuilt schematic blocks. */
public final class SchematicGhostRenderer {
    private SchematicGhostRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(SchematicGhostRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MatrixStack matrices = context.matrices();
        if (matrices == null || context.consumers() == null) return;
        SchematicBuilder builder;
        try { builder = ModuleManager.get(SchematicBuilder.class); }
        catch (RuntimeException ignored) { return; }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !builder.isPreviewEnabled()) return;

        VertexConsumer lines = context.consumers().getBuffer(RenderLayers.linesTranslucent());
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            int shown = 0;
            for (BlockPos pos : builder.ghostPositions(512)) {
                if (client.player.squaredDistanceTo(pos.toCenterPos()) > 48.0 * 48.0) continue;
                boolean next = shown++ == 0;
                int rgb = next ? 0x2EF2E0 : 0x618CEB;
                float alpha = 0.9f;
                drawWireBox(matrices, lines, new Box(pos).expand(0.002), rgb, alpha);
            }
        } finally {
            matrices.pop();
        }
    }

    private static void drawWireBox(MatrixStack matrices, VertexConsumer consumer, Box box, int rgb, float alpha) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        // Four vertical edges.
        line(matrices, consumer, x0,y0,z0, x0,y1,z0, rgb, alpha);
        line(matrices, consumer, x1,y0,z0, x1,y1,z0, rgb, alpha);
        line(matrices, consumer, x1,y0,z1, x1,y1,z1, rgb, alpha);
        line(matrices, consumer, x0,y0,z1, x0,y1,z1, rgb, alpha);
        // Top and bottom rectangles.
        line(matrices, consumer, x0,y0,z0, x1,y0,z0, rgb, alpha);
        line(matrices, consumer, x1,y0,z0, x1,y0,z1, rgb, alpha);
        line(matrices, consumer, x1,y0,z1, x0,y0,z1, rgb, alpha);
        line(matrices, consumer, x0,y0,z1, x0,y0,z0, rgb, alpha);
        line(matrices, consumer, x0,y1,z0, x1,y1,z0, rgb, alpha);
        line(matrices, consumer, x1,y1,z0, x1,y1,z1, rgb, alpha);
        line(matrices, consumer, x1,y1,z1, x0,y1,z1, rgb, alpha);
        line(matrices, consumer, x0,y1,z1, x0,y1,z0, rgb, alpha);
    }

    private static void line(MatrixStack matrices, VertexConsumer consumer,
                             double x0, double y0, double z0, double x1, double y1, double z1,
                             int rgb, float alpha) {
        MatrixStack.Entry entry = matrices.peek();
        var matrix = entry.getPositionMatrix();
        float red = ((rgb >> 16) & 255) / 255f;
        float green = ((rgb >> 8) & 255) / 255f;
        float blue = (rgb & 255) / 255f;
        consumer.vertex(matrix, (float)x0, (float)y0, (float)z0).color(red, green, blue, alpha)
                .normal(entry, 0f, 1f, 0f).lineWidth(1.5f);
        consumer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(red, green, blue, alpha)
                .normal(entry, 0f, 1f, 0f).lineWidth(1.5f);
    }
}

package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SkyVisuals;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Camera-facing 3D sky scenery. The vanilla sky remains underneath as a fallback. */
public final class SkyVisualRenderer {
    private SkyVisualRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(SkyVisualRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || context.matrices() == null || context.consumers() == null) return;

        SkyVisuals sky;
        try { sky = ModuleManager.get(SkyVisuals.class); }
        catch (Throwable ignored) { return; }
        if (!sky.isEnabled()) return;

        MatrixStack matrices = context.matrices();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        Vec3d forward = mc.player.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
        if (right.lengthSquared() < 1.0E-6) right = new Vec3d(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3d up = forward.crossProduct(right).normalize();
        VertexConsumer fills = context.consumers().getBuffer(RenderLayers.debugFilledBox());
        double distance = sky.distance();
        float intensity = sky.intensity();
        int color = sky.color();

        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            drawStars(matrices, fills, camera, distance, intensity);
            switch (sky.style()) {
                case "Nordlicht" -> drawAurora(matrices, fills, camera, right, up, forward, distance, color, intensity);
                case "Planet" -> drawPlanet(matrices, fills, camera, right, up, forward, distance, color, intensity, sky.planetScale());
                default -> drawNebula(matrices, fills, camera, right, up, forward, distance, color, intensity);
            }
        } finally {
            matrices.pop();
        }
    }

    private static Vec3d point(Vec3d origin, Vec3d right, Vec3d up, Vec3d forward,
                               double x, double y, double z) {
        return origin.add(right.multiply(x)).add(up.multiply(y)).add(forward.multiply(z));
    }

    /** Emits six translucent quad faces using Minecraft's buffered debug-filled-box pipeline. */
    private static void drawFilledBox(MatrixStack matrices, VertexConsumer consumer, Box box,
                                      float red, float green, float blue, float alpha) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        // West, east, bottom, top, north, south faces.
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

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
                             Vec3d a, Vec3d b, Vec3d c, Vec3d d,
                             float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, (float)a.x, (float)a.y, (float)a.z).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float)b.x, (float)b.y, (float)b.z).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float)c.x, (float)c.y, (float)c.z).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float)d.x, (float)d.y, (float)d.z).color(red, green, blue, alpha);
    }

    private static void drawStars(MatrixStack m, VertexConsumer fills, Vec3d c, double d, float power) {
        for (int i = 0; i < 120; i++) {
            double a = i * 2.399963229728653;
            double y = Math.sin(i * 91.17) * 0.76;
            double r = Math.sqrt(1 - y * y);
            double x = Math.cos(a) * r, z = Math.sin(a) * r;
            double radius = d * (0.78 + (i % 7) * 0.035);
            double px = c.x + x * radius, py = c.y + y * radius, pz = c.z + z * radius;
            double size = (i % 11 == 0 ? 0.12 : 0.045) * power;
            int col = (i % 5 == 0) ? 0xFFB8DFFF : 0xFFFFFFFF;
            drawFilledBox(m, fills, new Box(px-size, py-size, pz-size, px+size, py+size, pz+size),
                    ((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, 0.95f);
        }
    }

    private static void drawAurora(MatrixStack m, VertexConsumer fills, Vec3d c, Vec3d right,
                                   Vec3d up, Vec3d forward, double d, int rgb, float power) {
        int g = (rgb >> 8) & 255, r = (rgb >> 16) & 255, b = rgb & 255;
        for (int band = 0; band < 4; band++) {
            double phase = System.currentTimeMillis() * 0.00025 + band * 1.4;
            for (int i = 0; i < 42; i++) {
                double t = i / 41.0;
                double x = (t - 0.5) * d * 1.5;
                double z = d * (0.42 + band * 0.035) + Math.sin(t * 8 + phase) * 5;
                double y = d * 0.30 + Math.sin(t * 11 + phase) * 5 + band * 2.6;
                Vec3d p = point(c, right, up, forward, x, y, z);
                double w = 0.08 * power;
                Box segment = new Box(p.x-w, p.y-w, p.z-w, p.x+w, p.y+1.2*power, p.z+w);
                float mix = 0.55f + 0.35f * (float)Math.sin(t * Math.PI);
                drawFilledBox(m, fills, segment, r/255f*mix, g/255f*mix, b/255f*mix, 0.62f);
            }
        }
    }

    private static Vec3d spherePoint(Vec3d center, Vec3d right, Vec3d up, Vec3d forward,
                                     double radius, double latitude, double longitude) {
        double ring = radius * Math.cos(latitude);
        return point(center, right, up, forward,
                ring * Math.cos(longitude), radius * Math.sin(latitude), ring * Math.sin(longitude));
    }

    private static void drawPlanet(MatrixStack m, VertexConsumer fills, Vec3d c, Vec3d right,
                                   Vec3d up, Vec3d forward, double d, int rgb, float power, float scale) {
        Vec3d center = point(c, right, up, forward, d * 0.24, d * 0.10, d * 0.72);
        double rad = d * 0.13 * scale;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        // A buffered latitude/longitude quad mesh gives the planet a continuous filled 3D surface.
        Matrix4f matrix = m.peek().getPositionMatrix();
        int latitudeSteps = 18, longitudeSteps = 72;
        for (int lat = 0; lat < latitudeSteps; lat++) {
            double phi0 = -Math.PI / 2.0 + lat * Math.PI / latitudeSteps;
            double phi1 = -Math.PI / 2.0 + (lat + 1) * Math.PI / latitudeSteps;
            for (int lon = 0; lon < longitudeSteps; lon++) {
                double theta0 = lon * Math.PI * 2.0 / longitudeSteps;
                double theta1 = (lon + 1) * Math.PI * 2.0 / longitudeSteps;
                Vec3d p00 = spherePoint(center, right, up, forward, rad, phi0, theta0);
                Vec3d p01 = spherePoint(center, right, up, forward, rad, phi0, theta1);
                Vec3d p11 = spherePoint(center, right, up, forward, rad, phi1, theta1);
                Vec3d p10 = spherePoint(center, right, up, forward, rad, phi1, theta0);
                float shade = 0.58f + 0.42f * Math.max(0f, (float)(Math.cos(theta0) * Math.cos((phi0 + phi1) * 0.5)));
                quad(fills, matrix, p00, p10, p11, p01,
                        Math.min(1f, r/255f*shade), Math.min(1f, g/255f*shade), Math.min(1f, b/255f*shade), 0.98f*power);
            }
        }
        // Broad orbital ring: a real 3D point cloud around the spherical body.
        for (int i = 0; i < 72; i++) {
            double a = i * Math.PI * 2 / 72;
            Vec3d p = point(center, right, up, forward, Math.cos(a) * rad * 1.65,
                    Math.sin(a) * rad * 0.35, 0);
            double s = 0.10 * scale;
            drawFilledBox(m, fills, new Box(p.x-s,p.y-s,p.z-s,p.x+s,p.y+s,p.z+s), 0.45f, 0.78f, 1.0f, 0.82f);
        }
    }

    private static void drawNebula(MatrixStack m, VertexConsumer fills, Vec3d c, Vec3d right,
                                   Vec3d up, Vec3d forward, double d, int rgb, float power) {
        for (int cloud = 0; cloud < 5; cloud++) {
            double cx = Math.sin(cloud * 2.1) * d * 0.55;
            double cy = Math.cos(cloud * 1.7) * d * 0.32;
            double cz = d * 0.82;
            for (int i = 0; i < 18; i++) {
                double a = i * 2.399963;
                double rr = (4 + i % 6) * power;
                Vec3d p = point(c, right, up, forward,
                        cx + Math.cos(a) * rr, cy + Math.sin(a * 1.4) * rr, cz + Math.sin(a) * rr);
                double s = 0.45 * power;
                float red = Math.min(1, ((rgb >> 16) & 255) / 255f + 0.15f);
                float green = ((rgb >> 8) & 255) / 255f;
                float blue = Math.min(1, (rgb & 255) / 255f + 0.2f);
                drawFilledBox(m, fills, new Box(p.x-s,p.y-s,p.z-s,p.x+s,p.y+s,p.z+s), red, green, blue, 0.42f);
            }
        }
    }
}

package dev.nightfall.util;

import dev.nightfall.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Projects world coordinates with the same camera rotation and projection matrix
 * Minecraft uses for the current world frame.
 */
public final class Projection {
    private static double ex, ey, ez;
    private static int sw, sh;
    private static float tickDelta;
    private static Matrix4f viewProjection;
    private static boolean valid;

    private Projection() {}

    public static boolean valid() { return valid; }
    public static int width() { return sw; }
    public static int height() { return sh; }
    public static double eyeX() { return ex; }
    public static double eyeY() { return ey; }
    public static double eyeZ() { return ez; }
    public static float tickDelta() { return tickDelta; }

    public static void update(MinecraftClient mc, float frameTickDelta, int width, int height) {
        valid = false;
        viewProjection = null;

        if (mc.player == null || mc.world == null || width <= 0 || height <= 0
                || !Float.isFinite(frameTickDelta)) return;

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        Vec3d cameraPos = camera.getCameraPos();
        ex = cameraPos.x;
        ey = cameraPos.y;
        ez = cameraPos.z;

        GameRendererAccessor renderer = (GameRendererAccessor) mc.gameRenderer;
        float fov = renderer.nightfall$invokeGetFov(camera, frameTickDelta, true);
        if (!Float.isFinite(fov) || fov <= 0.0f) return;

        // Use Minecraft's actual projection matrix (including dynamic FOV effects),
        // not a second approximation based only on the user's configured FOV.
        Matrix4f projection = renderer.nightfall$invokeGetProjectionMatrix(fov);
        Quaternionf inverseCameraRotation = camera.getRotation().conjugate(new Quaternionf());
        Matrix4f view = new Matrix4f().rotation(inverseCameraRotation);
        viewProjection = projection.mul(view);

        sw = width;
        sh = height;
        tickDelta = frameTickDelta;
        valid = Double.isFinite(ex) && Double.isFinite(ey) && Double.isFinite(ez);
    }

    /**
     * Tests block visibility from the actual render camera. This matters when FreeLook,
     * Freecam or a third-person camera is not at the local player's eye position.
     */
    public static boolean canSeeFromCamera(MinecraftClient mc, Entity target) {
        if (mc == null || mc.world == null || mc.player == null || target == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d start = camera != null && camera.isReady()
                ? camera.getCameraPos()
                : mc.player.getEyePos();

        Box box = target.getBoundingBox();
        Vec3d end = new Vec3d(
                (box.minX + box.maxX) * 0.5,
                (box.minY + box.maxY) * 0.5,
                (box.minZ + box.maxZ) * 0.5
        );
        if (start.squaredDistanceTo(end) < 1.0e-8) return true;

        return mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        )).getType() == HitResult.Type.MISS;
    }

    /**
     * @return false when the point is behind/too close to the camera or invalid.
     * out = {x, y} in scaled GUI pixels.
     */
    public static boolean project(double x, double y, double z, double[] out) {
        if (!valid || viewProjection == null || out == null || out.length < 2
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;

        float dx = (float) (x - ex);
        float dy = (float) (y - ey);
        float dz = (float) (z - ez);

        // JOML stores matrices column-major: these are the x, y and w
        // components of projection * inverseCameraRotation * position.
        float clipX = viewProjection.m00() * dx + viewProjection.m10() * dy
                + viewProjection.m20() * dz + viewProjection.m30();
        float clipY = viewProjection.m01() * dx + viewProjection.m11() * dy
                + viewProjection.m21() * dz + viewProjection.m31();
        float clipW = viewProjection.m03() * dx + viewProjection.m13() * dy
                + viewProjection.m23() * dz + viewProjection.m33();

        if (!Float.isFinite(clipX) || !Float.isFinite(clipY)
                || !Float.isFinite(clipW) || clipW <= 1.0e-4f) return false;

        double ndcX = clipX / clipW;
        double ndcY = clipY / clipW;
        out[0] = (ndcX * 0.5 + 0.5) * sw;
        out[1] = (0.5 - ndcY * 0.5) * sh;
        return Double.isFinite(out[0]) && Double.isFinite(out[1]);
    }
}

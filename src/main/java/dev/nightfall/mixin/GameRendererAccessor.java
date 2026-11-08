package dev.nightfall.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to the exact FOV and projection matrix used by Minecraft's world renderer. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    float nightfall$invokeGetFov(Camera camera, float tickProgress, boolean changingFov);

    @Invoker("getProjectionMatrix")
    Matrix4f nightfall$invokeGetProjectionMatrix(float fov);
}

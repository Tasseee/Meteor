package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies FreeLook angles to the rendered camera only. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("TAIL"))
    private void nightfall$applyFreeLook(CallbackInfo ci) {
        Camera camera = (Camera) (Object) this;
        camera.setRotation(FreeLook.getViewYaw(camera.getYaw()), FreeLook.getViewPitch(camera.getPitch()));
    }
}

package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import dev.nightfall.modules.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies independent camera state after Minecraft updates its normal camera. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow public abstract void setPos(double x, double y, double z);
    @Shadow public abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void nightfall$applyCameraState(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                              boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (Freecam.isActive()) {
            var pos = Freecam.cameraPosition();
            setPos(pos.x, pos.y, pos.z);
            setRotation(Freecam.cameraYaw(), Freecam.cameraPitch());
        } else if (FreeLook.isActive()) {
            setRotation(FreeLook.cameraYaw(), FreeLook.cameraPitch());
        }
    }
}

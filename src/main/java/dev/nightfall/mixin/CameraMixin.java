package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Freecam;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies a local-only translation after vanilla positions the render camera. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private net.minecraft.util.math.Vec3d pos;

    @Invoker("setPos")
    protected abstract void nightfall$setPos(double x, double y, double z);

    @Inject(method = "update", at = @At("TAIL"))
    private void nightfall$applyFreecamOffset(CallbackInfo ci) {
        try {
            if (!ModuleManager.get(Freecam.class).isEnabled()) return;
            nightfall$setPos(pos.x + Freecam.offsetX(),
                    pos.y + Freecam.offsetY(),
                    pos.z + Freecam.offsetZ());
        } catch (Throwable ignored) {
            // Camera rendering must remain safe if modules are not initialized.
        }
    }
}

package dev.nightfall.mixin;

import dev.nightfall.modules.FreeLook;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Separates vanilla mouse look from the player's gameplay rotation. */
@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Unique private float nightfall$originalYaw;
    @Unique private float nightfall$originalPitch;
    @Unique private boolean nightfall$capturing;

    @Inject(method = "updateMouse", at = @At("HEAD"))
    private void nightfall$beforeMouseLook(double timeDelta, CallbackInfo ci) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        nightfall$capturing = FreeLook.isActive() && player != null;
        if (nightfall$capturing) {
            nightfall$originalYaw = player.getYaw();
            nightfall$originalPitch = player.getPitch();
        }
    }

    @Inject(method = "updateMouse", at = @At("TAIL"))
    private void nightfall$afterMouseLook(double timeDelta, CallbackInfo ci) {
        if (!nightfall$capturing) return;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) FreeLook.captureMouseLook(player, nightfall$originalYaw, nightfall$originalPitch);
        nightfall$capturing = false;
    }
}

package dev.nightfall.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes the vanilla potion/status-effect list from the right side of the HUD. */
@Mixin(InGameHud.class)
public abstract class StatusEffectOverlayMixin {
    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void nightfall$hideStatusEffects(RenderTickCounter tickCounter, CallbackInfo ci) {
        ci.cancel();
    }
}

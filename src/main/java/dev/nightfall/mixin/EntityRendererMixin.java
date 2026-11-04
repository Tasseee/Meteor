package dev.nightfall.mixin;

import dev.nightfall.modules.NameTags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides only the local player's own rendered vanilla nametag; it cannot hide the name server-side. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void nightfall$hideOwnName(Entity entity, double squaredDistanceToCamera,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (entity == MinecraftClient.getInstance().player && NameTags.shouldHideOwnName(entity))
            cir.setReturnValue(false);
    }
}

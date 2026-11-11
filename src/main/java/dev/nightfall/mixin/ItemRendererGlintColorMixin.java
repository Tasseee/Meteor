package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import dev.nightfall.render.GlintColorVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tint only the actual vanilla glint buffers. ItemRenderer combines a glint
 * buffer with the ordinary item buffer; wrapping the provider itself also
 * tinted the ordinary item buffer and was not a reliable way to isolate glint.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlintColorMixin {
    @Redirect(
            method = "getItemGlintConsumer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"
            )
    )
    private static VertexConsumer nightfall$tintRegularGlint(VertexConsumerProvider provider, RenderLayer layer) {
        return nightfall$wrapGlint(provider, layer);
    }

    @Redirect(
            method = "getSpecialItemGlintConsumer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"
            )
    )
    private static VertexConsumer nightfall$tintSpecialGlint(VertexConsumerProvider provider, RenderLayer layer) {
        return nightfall$wrapGlint(provider, layer);
    }

    private static VertexConsumer nightfall$wrapGlint(VertexConsumerProvider provider, RenderLayer layer) {
        VertexConsumer consumer = provider.getBuffer(layer);
        if (layer != RenderLayers.glint()
                && layer != RenderLayers.glintTranslucent()
                && layer != RenderLayers.entityGlint()) {
            return consumer;
        }

        try {
            ItemGlint module = ModuleManager.get(ItemGlint.class);
            if (module == null || !module.shouldTint()) return consumer;
            return new GlintColorVertexConsumer(consumer, module::currentGlintColor);
        } catch (Throwable ignored) {
            return consumer;
        }
    }
}

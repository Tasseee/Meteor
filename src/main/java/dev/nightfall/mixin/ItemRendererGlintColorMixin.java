package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import dev.nightfall.render.GlintColorVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps the provider used by ItemRenderer's glint-consumer factory.
 * That factory requests the glint buffer through this provider, so checking
 * RenderLayer.toString() is unnecessary and can silently miss valid glint layers.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlintColorMixin {
    @ModifyVariable(method = "getItemGlintConsumer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static VertexConsumerProvider nightfall$wrapGlintBuffers(VertexConsumerProvider original) {
        try {
            ItemGlint module = ModuleManager.get(ItemGlint.class);
            if (module == null || !module.shouldTint()) return original;

            return layer -> {
                VertexConsumer consumer = original.getBuffer(layer);
                return new GlintColorVertexConsumer(consumer, module::currentGlintColor);
            };
        } catch (Throwable ignored) {
            return original;
        }
    }
}

package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import dev.nightfall.render.GlintColorVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Locale;

/** Wraps only glint buffers so custom colors never tint the actual item texture. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererGlintColorMixin {
    @ModifyVariable(method = "getItemGlintConsumer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static VertexConsumerProvider nightfall$wrapGlintBuffers(VertexConsumerProvider original) {
        try {
            ItemGlint module = ModuleManager.get(ItemGlint.class);
            if (module == null || !module.shouldTint()) return original;

            return layer -> {
                VertexConsumer consumer = original.getBuffer(layer);
                if (layer.toString().toLowerCase(Locale.ROOT).contains("glint")) {
                    return new GlintColorVertexConsumer(consumer, module::currentGlintColor);
                }
                return consumer;
            };
        } catch (Throwable ignored) {
            return original;
        }
    }
}

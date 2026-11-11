package dev.nightfall.render;

import net.minecraft.client.render.VertexConsumer;

import java.util.function.IntSupplier;

/** Tints only vertices written to a glint render layer; the underlying item layer stays unchanged. */
public final class GlintColorVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final IntSupplier rgbSupplier;

    public GlintColorVertexConsumer(VertexConsumer delegate, IntSupplier rgbSupplier) {
        this.delegate = delegate;
        this.rgbSupplier = rgbSupplier;
    }

    private int rgb() {
        return rgbSupplier.getAsInt() & 0xFFFFFF;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int rgb = rgb();
        delegate.color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha) {
        int rgb = rgb();
        delegate.color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF,
                Math.max(0, Math.min(255, Math.round(alpha * 255.0f))));
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width) {
        delegate.lineWidth(width);
        return this;
    }
}

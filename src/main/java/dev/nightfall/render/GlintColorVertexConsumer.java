package dev.nightfall.render;

import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import net.minecraft.class_4588;

/**
 * Applies the configured Item Glow color and opacity to Minecraft's existing
 * enchanted-item overlay without replacing its render layer or texture.
 */
public final class GlintColorVertexConsumer implements class_4588 {
   private final class_4588 delegate;
   private final IntSupplier rgbSupplier;
   private final DoubleSupplier opacitySupplier;

   public GlintColorVertexConsumer(class_4588 delegate, IntSupplier rgbSupplier, DoubleSupplier opacitySupplier) {
      this.delegate = delegate;
      this.rgbSupplier = rgbSupplier;
      this.opacitySupplier = opacitySupplier;
   }

   private int rgb() {
      return this.rgbSupplier.getAsInt() & 0xFFFFFF;
   }

   private float opacity() {
      return Math.max(0.0F, Math.min(2.0F, (float)this.opacitySupplier.getAsDouble()));
   }

   private int scaleAlpha(int alpha) {
      return Math.max(0, Math.min(255, Math.round(alpha * this.opacity())));
   }

   public class_4588 method_22912(float x, float y, float z) {
      this.delegate.method_22912(x, y, z);
      return this;
   }

   public class_4588 method_39415(int color) {
      int rgb = this.rgb();
      int alpha = this.scaleAlpha(color >>> 24 & 0xFF);
      this.delegate.method_39415(alpha << 24 | rgb);
      return this;
   }

   public class_4588 method_1336(int red, int green, int blue, int alpha) {
      int rgb = this.rgb();
      this.delegate.method_1336(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, this.scaleAlpha(alpha));
      return this;
   }

   public class_4588 method_22915(float red, float green, float blue, float alpha) {
      int rgb = this.rgb();
      float adjustedAlpha = Math.max(0.0F, Math.min(1.0F, alpha * this.opacity()));
      this.delegate.method_1336(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.round(adjustedAlpha * 255.0F));
      return this;
   }

   public class_4588 method_22913(float u, float v) {
      this.delegate.method_22913(u, v);
      return this;
   }

   public class_4588 method_60796(int u, int v) {
      this.delegate.method_60796(u, v);
      return this;
   }

   public class_4588 method_22921(int u, int v) {
      this.delegate.method_22921(u, v);
      return this;
   }

   public class_4588 method_22914(float x, float y, float z) {
      this.delegate.method_22914(x, y, z);
      return this;
   }

   public class_4588 method_75298(float width) {
      this.delegate.method_75298(width);
      return this;
   }
}

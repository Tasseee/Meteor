package dev.nightfall.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1060;
import net.minecraft.class_12247;
import net.minecraft.class_12250;
import net.minecraft.class_1921;
import net.minecraft.class_310;

/**
 * Creates a small, seamless ribbon mask and reuses the vanilla glint pipeline
 * with that mask. The vanilla enchantment texture is never sampled.
 */
public final class NightfallGlintRenderer {
   private static final Map<class_1921, class_1921> LAYERS = new HashMap<>();
   private static net.minecraft.class_2960 textureId;

   private NightfallGlintRenderer() {
   }

   public static class_1921 layerFor(class_1921 source) {
      return LAYERS.computeIfAbsent(source, NightfallGlintRenderer::createLayer);
   }

   private static class_1921 createLayer(class_1921 source) {
      class_12247.class_12248 setup = class_12247.method_75927(source.method_73243())
         .method_75934("Sampler0", ensureTexture())
         .method_75933(class_12250.field_64069)
         .method_75937();

      return class_1921.method_75940("nightfall_custom_item_glint", setup.method_75938());
   }

   private static net.minecraft.class_2960 ensureTexture() {
      if (textureId != null) return textureId;

      class_1043 texture = new class_1043("Nightfall flowing item glint", 64, 64, false);
      class_1011 image = texture.method_4525();

      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            double waveA = y * 0.42 + Math.sin(x * 0.16) * 3.8;
            double waveB = y * 0.42 + Math.sin(x * 0.16 + 2.4) * 3.8 + 20.0;
            double dA = wrappedDistance(y - waveA, 64.0);
            double dB = wrappedDistance(y - waveB, 64.0);
            double ribbonA = Math.exp(-(dA * dA) / 5.5);
            double ribbonB = Math.exp(-(dB * dB) / 12.0) * 0.42;
            double glimmer = 0.78 + 0.22 * Math.sin((x * 0.31) + (y * 0.17));
            int alpha = (int) (Math.min(0.88, (ribbonA * 0.72 + ribbonB) * glimmer) * 255.0);
            image.method_4305(x, y, (alpha << 24) | 0x00FFFFFF);
         }
      }

      texture.method_4524();
      class_1060 manager = class_310.method_1551().method_1531();
      textureId = manager.method_4617("nightfall_item_glint", texture);
      return textureId;
   }

   private static double wrappedDistance(double value, double period) {
      double wrapped = value % period;
      if (wrapped < 0.0) wrapped += period;
      return Math.min(wrapped, period - wrapped);
   }
}

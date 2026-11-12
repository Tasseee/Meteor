package dev.nightfall.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1060;
import net.minecraft.class_12247;
import net.minecraft.class_12250;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_310;

/** Builds a custom flowing ribbon texture and applies it through the item glint pipeline. */
public final class NightfallGlintRenderer {
   private static final Map<class_1921, class_1921> LAYERS = new HashMap<>();
   private static class_2960 textureId;

   private NightfallGlintRenderer() {}

   public static class_1921 layerFor(class_1921 source) {
      return LAYERS.computeIfAbsent(source, NightfallGlintRenderer::createLayer);
   }

   private static class_1921 createLayer(class_1921 source) {
      class_12247 setup = class_12247.method_75927(source.method_73243())
         .method_75934("Sampler0", ensureTexture())
         .method_75933(class_12250.field_64069)
         .method_75937()
         .method_75938();
      return class_1921.method_75940("nightfall_custom_item_glint", setup);
   }

   private static class_2960 ensureTexture() {
      if (textureId != null) return textureId;
      class_1043 dynamic = new class_1043("Nightfall custom glint", 64, 64, false);
      class_1011 image = dynamic.method_4525();
      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            double d1 = circularDistance(x - y - 12, 64);
            double d2 = circularDistance(x - y - 43, 64);
            double ribbon = Math.exp(-(d1 * d1) / 7.0) * 0.78
               + Math.exp(-(d2 * d2) / 13.0) * 0.34;
            double shimmer = 0.82 + 0.18 * Math.sin((x + y) * 0.22);
            int alpha = (int) (Math.min(0.92, ribbon * shimmer) * 255.0);
            image.method_4305(x, y, (alpha << 24) | 0x00FFFFFF);
         }
      }
      class_1060 manager = class_310.method_1551().method_1531();
      textureId = class_2960.method_60655("nightfall", "item_glint");
      manager.method_4616(textureId, dynamic);
      return textureId;
   }

   private static double circularDistance(int value, int period) {
      int wrapped = Math.floorMod(value, period);
      return Math.min(wrapped, period - wrapped);
   }
}

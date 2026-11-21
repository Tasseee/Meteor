package dev.nightfall.gui;

import net.minecraft.class_332;

/**
 * Shared Clean UI drawing helpers used by Nightfall's screen mixins.
 *
 * This class deliberately lives outside the configured mixin package:
 * Minecraft classes injected by mixins must not directly load helper classes
 * from the mixin package itself.
 */
public final class CleanUi {
   public static final int BG_TOP = 0xFF0B0D12;
   public static final int BG_BOTTOM = 0xFF151926;
   public static final int BTN = 0xFF1B2030;
   public static final int BTN_HOVER = 0xFF262D44;
   public static final int BTN_OFF = 0xFF14171F;
   public static final int BORDER = 0xFF2A2F40;
   public static final int OUTLINE = 0x30FFFFFF;
   public static final int ACCENT = 0xFF7C5CFF;
   public static final int TEXT = 0xFFEAECF3;
   public static final int TEXT_OFF = 0xFF5B6070;
   public static final int RADIUS = 5;

   private CleanUi() {
   }

   public static void background(class_332 context, int width, int height) {
      context.method_25296(0, 0, width, height, BG_TOP, BG_BOTTOM);
      context.method_25294(0, 0, width, 1, ACCENT);
   }

   public static void roundRect(class_332 context, int x, int y, int width, int height, int radius, int color) {
      if (width <= 0 || height <= 0) return;
      int r = Math.min(radius, Math.min(width, height) / 2);
      for (int row = 0; row < height; row++) {
         int distance = row < r ? r - row : (row >= height - r ? row - (height - 1 - r) : 0);
         int inset = distance <= 0 ? 0 : r - (int)Math.round(Math.sqrt(Math.max(0.0, r * r - (distance - 0.5) * (distance - 0.5))));
         context.method_25294(x + inset, y + row, x + width - inset, y + row + 1, color);
      }
   }

   public static int lerp(int from, int to, float amount) {
      float t = Math.max(0.0F, Math.min(1.0F, amount));
      int red = (int)((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
      int green = (int)((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
      int blue = (int)((from & 255) + ((to & 255) - (from & 255)) * t);
      return 0xFF000000 | red << 16 | green << 8 | blue;
   }
}

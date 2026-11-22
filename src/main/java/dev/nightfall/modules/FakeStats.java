package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import net.minecraft.class_124;
import net.minecraft.class_1799;
import net.minecraft.class_2378;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_310;

public final class FakeStats extends HudModule {
   private static final int W = 128;
   private static final int H = 160;

   private static final String[] LABELS = {
      "Geld", "Gems", "Stars", "Kills", "Tode", "Ping", "Keyall", "Spielzeit"
   };
   private static final String[] VALUES = {
      "104,18M", "550", "898", "886", "1,138", "46ms", "21m 4s", "60d 5h"
   };
   private static final int[] VALUE_COLORS = {
      0xFFE066, 0xFF55FF, 0xFFC825, 0xFFAA00, 0xFF5555,
      0x55FFFF, 0x55FF55, 0x55FF55
   };
   private static final String[] ICONS = {
      "minecraft:emerald", "minecraft:redstone", "minecraft:nether_star",
      "minecraft:iron_sword", "minecraft:skeleton_skull",
      "minecraft:compass", "minecraft:tripwire_hook", "minecraft:clock"
   };

   public FakeStats() {
      super("FakeStats", "HugoSMP-Scoreboard als lokales HUD; keine Serverdaten werden veraendert.");
      this.scalable = true;
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - W - 3, (sh - H) / 2 + 8};
   }

   private static int argb(int rgb) {
      return 0xFF000000 | (rgb & 0xFFFFFF);
   }

   private static void drawScaled(class_332 ctx, class_327 tr, class_2561 text,
                                  float x, float y, float scale, int color, int outline) {
      var m = ctx.method_51448();
      m.pushMatrix();
      m.translate(x, y);
      m.scale(scale, scale);
      if (outline != 0) {
         int[][] d = {{-1, 0}, {1, 0}, {0, -1}, {0, 1},
                      {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};
         for (int[] o : d) {
            ctx.method_51433(tr, text, o[0], o[1], argb(outline), false);
         }
      }
      ctx.method_51433(tr, text, 0, 0, argb(color), false);
      m.popMatrix();
   }

   private static void drawCentered(class_332 ctx, class_327 tr, String s,
                                    float cx, float y, float scale, int color, int outline) {
      class_2561 text = class_2561.method_43470(s).formatted(class_124.field_1076);
      float w = tr.method_1727(text) * scale;
      drawScaled(ctx, tr, text, cx - w / 2f, y, scale, color, outline);
   }

   private static void drawItemIcon(class_332 ctx, String id, float x, float y, float scale) {
      try {
         class_1799 stack = new class_1799(class_2378.field_11142.get(class_2960.method_60654(id)));
         var m = ctx.method_51448();
         m.pushMatrix();
         m.translate(x, y);
         m.scale(scale, scale);
         ctx.method_51431(stack, 0, 0);
         m.popMatrix();
      } catch (Exception ignored) {
      }
   }

   private static void drawCrown(class_332 ctx, int cx, int top, int height) {
      int gold = 0xFFF5B82E;
      int dark = 0xFF8A5A1E;
      for (int r = 0; r < height; r++) {
         ctx.method_25294(cx - r - 1, top + r, cx + r + 2, top + r + 1, dark);
         ctx.method_25294(cx - r, top + r, cx + r + 1, top + r + 1, gold);
      }
   }

   @Override
   protected void draw(class_332 ctx) {
      class_310 mc = class_310.method_1551();
      class_327 tr = mc.field_1772;
      int x = 0;
      int y = 0;
      int by = y + 30;

      // HugoSMP-Rahmen: braun, gold, braun, dunkle Innenflaeche.
      ctx.method_25294(x, by, x + W, y + H, 0xFF6B3F14);
      ctx.method_25294(x + 1, by + 1, x + W - 1, y + H - 1, 0xFFC98B2E);
      ctx.method_25294(x + 2, by + 2, x + W - 2, y + H - 2, 0xFF8A5A1E);
      ctx.method_25294(x + 3, by + 3, x + W - 3, y + H - 3, 0xF01E140C);

      int acc = 0xFFF5B82E;
      ctx.method_25294(x, by, x + 4, by + 4, acc);
      ctx.method_25294(x + W - 4, by, x + W, by + 4, acc);
      ctx.method_25294(x, y + H - 4, x + 4, y + H, acc);
      ctx.method_25294(x + W - 4, y + H - 4, x + W, y + H, acc);

      ctx.method_25294(x + 4, by - 3, x + 22, by + 1, 0xFFC98B2E);
      ctx.method_25294(x + W - 22, by - 3, x + W - 4, by + 1, 0xFFC98B2E);

      int cx = x + W / 2;
      drawCrown(ctx, cx - 26, y + 2, 7);
      drawCrown(ctx, cx, y - 2, 9);
      drawCrown(ctx, cx + 26, y + 2, 7);

      drawCentered(ctx, tr, "HUGO", cx, y + 8, 2.6f, 0xFF2020, 0x3A0A00);
      drawCentered(ctx, tr, "SMP", cx, y + 29, 1.5f, 0x8A55E0, 0x1A0A33);

      int i = 0;
      for (; i < LABELS.length; i++) {
         float ly = y + 46 + i * 9;
         drawItemIcon(ctx, ICONS[i], x + 6, ly - 1, 0.56f);

         int lx = x + 17;
         ctx.method_51433(tr, class_2561.method_43470(LABELS[i]), lx, (int) ly,
               argb(0xFFFFFF), true);
         int vx = lx + tr.method_1727(class_2561.method_43470(LABELS[i])) + 4;
         ctx.method_51433(tr, class_2561.method_43470(VALUES[i]), vx, (int) ly,
               argb(VALUE_COLORS[i]), true);
      }

      ctx.method_51433(tr, class_2561.method_43470("HUGOSMP.NET"), x + 8, y + 131,
            argb(0xFF3030), true);
      ctx.method_51433(tr, class_2561.method_43470("10410/13750"), x + 8, y + 141,
            argb(0x55FF55), true);

      String[] bottom = {
         "minecraft:gold_nugget", "minecraft:diamond",
         "minecraft:diamond_pickaxe", "minecraft:gold_nugget"
      };
      for (int b = 0; b < bottom.length; b++) {
         drawItemIcon(ctx, bottom[b], x + W - 50 + b * 11, y + H - 14, 0.6f);
      }
   }
}

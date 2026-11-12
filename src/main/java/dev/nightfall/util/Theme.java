package dev.nightfall.util;

public final class Theme {
   public static int BG_DIM = -1946157056;
   public static int PANEL = -434891752;
   public static int HEADER_TOP = -267706331;
   public static int HEADER_BOT = -267382502;
   public static int ROW_HOVER = 822083583;
   public static int ACCENT = -13246256;
   public static int ACCENT_DIM = -9879098;
   public static int ACCENT_BG = 1429594320;
   public static int TEXT = -1513232;
   public static int TEXT_DIM = -6645074;
   public static int MAP_BG = 921106;
   public static int HUD_PANEL = -921693669;
   public static int HUD_EDGE = -13246256;
   public static final int[] PALETTE = new int[]{3530960, 9133302, 15680580, 2278750, 3900150, 16096779, 15485081, 16777215};
   public static String DESIGN = "Gradient";
   private static final int DEFAULT_BG_DIM = -1946157056;
   private static final int DEFAULT_PANEL = -434891752;
   private static final int DEFAULT_HEADER_TOP = -267706331;
   private static final int DEFAULT_HEADER_BOT = -267382502;
   private static final int DEFAULT_ROW_HOVER = 822083583;
   private static final int DEFAULT_ACCENT = -13246256;
   private static final int DEFAULT_ACCENT_DIM = -9879098;
   private static final int DEFAULT_ACCENT_BG = 1429594320;
   private static final int DEFAULT_TEXT = -1513232;
   private static final int DEFAULT_TEXT_DIM = -6645074;
   private static final int DEFAULT_MAP_BG = 921106;

   public static void apply(int accent, int secondary, int panel, int text, int dim, int overlay, String design) {
      ACCENT = 0xFF000000 | accent & 16777215;
      ACCENT_DIM = 0xFF000000 | secondary & 16777215;
      ACCENT_BG = 1426063360 | accent & 16777215;
      PANEL = ("Glass".equals(design) ? -1476395008 : -436207616) | panel & 16777215;
      BG_DIM = -1946157056 | overlay & 16777215;
      int headerAlpha = "Glass".equals(design) ? -1342177280 : -268435456;
      HEADER_TOP = headerAlpha | secondary & 16777215;
      HEADER_BOT = headerAlpha | panel & 16777215;
      TEXT = 0xFF000000 | text & 16777215;
      TEXT_DIM = 0xFF000000 | dim & 16777215;
      ROW_HOVER = 805306368 | accent & 16777215;
      MAP_BG = panel & 16777215;
      HUD_PANEL = ("Glass".equals(design) ? -1476395008 : -654311424) | panel & 16777215;
      HUD_EDGE = 0xFF000000 | accent & 16777215;
      DESIGN = design == null ? "Gradient" : design;
   }

   public static void reset() {
      BG_DIM = -1946157056;
      PANEL = -434891752;
      HEADER_TOP = -267706331;
      HEADER_BOT = -267382502;
      ROW_HOVER = 822083583;
      ACCENT = -13246256;
      ACCENT_DIM = -9879098;
      ACCENT_BG = 1429594320;
      TEXT = -1513232;
      TEXT_DIM = -6645074;
      MAP_BG = 921106;
      HUD_PANEL = -921693669;
      HUD_EDGE = -13246256;
      DESIGN = "Gradient";
   }

   private Theme() {
   }

   public static int withAlpha(int rgb, int a) {
      return a << 24 | rgb & 16777215;
   }

   public static int lerp(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int r = (int)((a >> 16 & 0xFF) * (1.0F - t) + (b >> 16 & 0xFF) * t);
      int g = (int)((a >> 8 & 0xFF) * (1.0F - t) + (b >> 8 & 0xFF) * t);
      int bl = (int)((a & 0xFF) * (1.0F - t) + (b & 0xFF) * t);
      return r << 16 | g << 8 | bl;
   }
}

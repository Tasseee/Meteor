package dev.nightfall.util;

public final class Theme {
    public static int BG_DIM      = 0x8C08060D;
    public static int PANEL       = 0xD9171222;
    public static int HEADER_TOP  = 0xF05B35B8;
    public static int HEADER_BOT  = 0xF0171222;
    public static int ROW_HOVER   = 0x30B66CFF;
    public static int ACCENT      = 0xFFB66CFF;
    public static int ACCENT_DIM  = 0xFF5B35B8;
    public static int ACCENT_BG   = 0x55B66CFF;
    public static int TEXT        = 0xFFF1EAFE;
    public static int TEXT_DIM    = 0xFFB2A4CC;
    public static int MAP_BG      = 0x171222;
    public static int HUD_PANEL   = 0xC9171222;
    public static int HUD_EDGE    = 0xFFB66CFF;

    public static final int[] PALETTE = {0xB66CFF, 0x8B5CF6, 0xEF4444, 0x22C55E, 0x3B82F6, 0xF59E0B, 0xEC4899, 0xFFFFFF};

    public static String DESIGN = "Glass";

    private static final int DEFAULT_BG_DIM = 0x8C08060D;
    private static final int DEFAULT_PANEL = 0xD9171222;
    private static final int DEFAULT_HEADER_TOP = 0xF05B35B8;
    private static final int DEFAULT_HEADER_BOT = 0xF0171222;
    private static final int DEFAULT_ROW_HOVER = 0x30B66CFF;
    private static final int DEFAULT_ACCENT = 0xFFB66CFF;
    private static final int DEFAULT_ACCENT_DIM = 0xFF5B35B8;
    private static final int DEFAULT_ACCENT_BG = 0x55B66CFF;
    private static final int DEFAULT_TEXT = 0xFFF1EAFE;
    private static final int DEFAULT_TEXT_DIM = 0xFFB2A4CC;
    private static final int DEFAULT_MAP_BG = 0x171222;

    public static void apply(int accent, int secondary, int panel, int text, int dim, int overlay, String design) {
        ACCENT = 0xFF000000 | (accent & 0xFFFFFF);
        ACCENT_DIM = 0xFF000000 | (secondary & 0xFFFFFF);
        ACCENT_BG = 0x55000000 | (accent & 0xFFFFFF);
        PANEL = ("Glass".equals(design) ? 0xA8000000 : 0xE6000000) | (panel & 0xFFFFFF);
        BG_DIM = 0x8C000000 | (overlay & 0xFFFFFF);
        int headerAlpha = "Glass".equals(design) ? 0xB0000000 : 0xF0000000;
        HEADER_TOP = headerAlpha | (secondary & 0xFFFFFF);
        HEADER_BOT = headerAlpha | (panel & 0xFFFFFF);
        TEXT = 0xFF000000 | (text & 0xFFFFFF);
        TEXT_DIM = 0xFF000000 | (dim & 0xFFFFFF);
        ROW_HOVER = 0x30000000 | (accent & 0xFFFFFF);
        MAP_BG = panel & 0xFFFFFF;
        HUD_PANEL = ("Glass".equals(design) ? 0xA8000000 : 0xD9000000) | (panel & 0xFFFFFF);
        HUD_EDGE = 0xFF000000 | (accent & 0xFFFFFF);
        DESIGN = design == null ? "Glass" : design;
    }

    public static void reset() {
        BG_DIM = DEFAULT_BG_DIM; PANEL = DEFAULT_PANEL; HEADER_TOP = DEFAULT_HEADER_TOP;
        HEADER_BOT = DEFAULT_HEADER_BOT; ROW_HOVER = DEFAULT_ROW_HOVER; ACCENT = DEFAULT_ACCENT;
        ACCENT_DIM = DEFAULT_ACCENT_DIM; ACCENT_BG = DEFAULT_ACCENT_BG; TEXT = DEFAULT_TEXT;
        TEXT_DIM = DEFAULT_TEXT_DIM; MAP_BG = DEFAULT_MAP_BG; HUD_PANEL = 0xC9171222; HUD_EDGE = DEFAULT_ACCENT; DESIGN = "Glass";
    }

    private Theme() {}

    public static int withAlpha(int rgb, int a) { return (a << 24) | (rgb & 0xFFFFFF); }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}

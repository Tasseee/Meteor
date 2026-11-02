package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.util.Theme;

/** Persistent ClickGUI theme editor. Custom mode exposes all RGB colors in the ClickGUI. */
public final class GUITheme extends Module {
    private final ModeSetting preset = add(new ModeSetting("Preset", "Nightfall", "Nightfall", "Violet", "Crimson", "Emerald", "Ocean", "Mono", "Custom"));
    private final ModeSetting design = add(new ModeSetting("Design", "Gradient", "Gradient", "Flat", "Glass"));
    private final ColorSetting accent = add(new ColorSetting("Akzentfarbe", 0x35E0D0));
    private final ColorSetting secondary = add(new ColorSetting("Sekundärfarbe", 0x6941C6));
    private final ColorSetting panel = add(new ColorSetting("Panel-Farbe", 0x141418));
    private final ColorSetting text = add(new ColorSetting("Textfarbe", 0xE8E8F0));
    private final ColorSetting dimText = add(new ColorSetting("Sekundärtext", 0x9A9AAE));
    private final ColorSetting overlay = add(new ColorSetting("Overlay-Farbe", 0x08080D));

    public GUITheme() {
        super("GUITheme", "ClickGUI-Presets, Design und frei einstellbare RGB-Farben.", Category.VISUALS);
        setEnabled(true);
    }

    public boolean prepareColorEdit(ColorSetting selected) {
        if (selected != accent && selected != secondary && selected != panel
                && selected != text && selected != dimText && selected != overlay) return false;
        preset.set("Custom");
        return true;
    }

    @Override protected void onEnable() { applyTheme(); }
    @Override protected void onDisable() { Theme.reset(); }
    @Override public void onTick() { applyTheme(); }

    private void applyTheme() {
        switch (preset.get()) {
            case "Violet" -> { overlay.set(0x08060D); accent.set(0xB66CFF); secondary.set(0x5B35B8); panel.set(0x171222); text.set(0xF1EAFE); dimText.set(0xB2A4CC); }
            case "Crimson" -> { overlay.set(0x10060A); accent.set(0xFF526A); secondary.set(0x8C2545); panel.set(0x1B1118); text.set(0xFFF0F2); dimText.set(0xC5A8B0); }
            case "Emerald" -> { overlay.set(0x06100B); accent.set(0x49E6A2); secondary.set(0x187B62); panel.set(0x101C19); text.set(0xE9FFF6); dimText.set(0x9BC6B5); }
            case "Ocean" -> { overlay.set(0x050A12); accent.set(0x45B9FF); secondary.set(0x285CBB); panel.set(0x101927); text.set(0xEAF6FF); dimText.set(0x9BB8D0); }
            case "Mono" -> { overlay.set(0x080808); accent.set(0xFFFFFF); secondary.set(0x777777); panel.set(0x151515); text.set(0xFFFFFF); dimText.set(0xAAAAAA); }
            case "Nightfall" -> { overlay.set(0x08080D); accent.set(0x35E0D0); secondary.set(0x6941C6); panel.set(0x141418); text.set(0xE8E8F0); dimText.set(0x9A9AAE); }
            default -> { /* Custom: keep user supplied RGB values. */ }
        }
        Theme.apply(accent.rgb(), secondary.rgb(), panel.rgb(), text.rgb(), dimText.rgb(), overlay.rgb(), design.get());
    }
}

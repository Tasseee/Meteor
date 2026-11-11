package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;

/** A single configurable, local-player Spinjitzu-style cosmetic. */
public final class Hats extends Module {
    private final ColorSetting color = add(new ColorSetting("Farbe", 0x55DDEE));
    private final NumberSetting size = add(new NumberSetting("Größe", 1.0, 0.5, 2.0, 0.1));
    private final NumberSetting height = add(new NumberSetting("Höhe", 0.08, -0.2, 0.6, 0.02));
    private final NumberSetting rotationSpeed = add(new NumberSetting("Drehgeschwindigkeit", 1.0, 0.1, 3.0, 0.1));

    public Hats() {
        super("Hats", "Ein rotierender 3D-Spinjitzu-Hut über deinem Kopf (nur lokal, Third-Person).", Category.VISUALS);
    }

    public int color() { return color.rgb(); }
    public double size() { return size.get(); }
    public double height() { return height.get(); }
    public double rotationSpeed() { return rotationSpeed.get(); }
}

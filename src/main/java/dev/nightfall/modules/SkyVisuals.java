package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;

/** Configuration for the client-side 3D sky objects rendered by SkyVisualRenderer. */
public final class SkyVisuals extends Module {
    private final ModeSetting style = add(new ModeSetting("Himmel", "Weltraum", "Weltraum", "Nordlicht", "Planet"));
    private final ColorSetting color = add(new ColorSetting("Hauptfarbe", 0x55DDEE));
    private final NumberSetting distance = add(new NumberSetting("Abstand", 112, 64, 180, 8));
    private final NumberSetting intensity = add(new NumberSetting("Intensität", 1.0, 0.2, 2.0, 0.05));
    private final NumberSetting scale = add(new NumberSetting("Planetengröße", 1.0, 0.5, 2.0, 0.05));

    public SkyVisuals() { super("SkyVisuals", "Animierte 3D-Sterne, Aurora-Bänder und Planetenkörper im Weltraum-Himmel.", Category.VISUALS); }
    public String style() { return style.get(); }
    public int color() { return color.rgb(); }
    public double distance() { return distance.get(); }
    public float intensity() { return intensity.get().floatValue(); }
    public float planetScale() { return scale.get().floatValue(); }
}

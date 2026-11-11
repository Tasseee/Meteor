package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;

/**
 * Applies Minecraft's native enchantment glint to item stacks in every vanilla item-render path.
 * The settings are exposed for the client UI; native glint does not support arbitrary bloom radius
 * or per-stack RGB tint without replacing Minecraft's item render pipeline.
 */
public final class ItemGlint extends Module {
    private final ColorSetting color = add(new ColorSetting("Neon Color", 0xA020F0));
    private final BoolSetting rainbow = add(new BoolSetting("Rainbow Mode", false));
    private final NumberSetting intensity = add(new NumberSetting("Glow Intensity", 100, 0, 100, 1));
    private final NumberSetting radius = add(new NumberSetting("Glow Radius", 20, 1, 20, 1));
    private final NumberSetting opacity = add(new NumberSetting("Glow Opacity", 100, 0, 100, 1));
    private final NumberSetting speed = add(new NumberSetting("Glow Speed", 1.0, 0.1, 5.0, 0.1));
    private final BoolSetting particles = add(new BoolSetting("Particle Effects", false));
    private final BoolSetting animated = add(new BoolSetting("Animated Glow", true));
    private final BoolSetting outline = add(new BoolSetting("Item Outline", true));

    public ItemGlint() {
        super("Item Glint", "Aktiviert den nativen Minecraft-Glint fuer Items in Hand, Inventar, Welt und Itemrahmen.", Category.VISUALS);
    }

    public boolean forceGlint() { return isEnabled() && intensity.get() > 0 && opacity.get() > 0; }
    public int neonColor() {
        if (rainbow.get()) {
            float hue = (System.currentTimeMillis() * 0.00008f * speed.get().floatValue()) % 1.0f;
            return java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF;
        }
        return color.rgb();
    }
    public int glowIntensity() { return intensity.getInt(); }
    public int glowRadius() { return radius.getInt(); }
    public int glowOpacity() { return opacity.getInt(); }
    public boolean particlesEnabled() { return particles.get() && animated.get(); }
    public boolean animatedEnabled() { return animated.get(); }
    public boolean outlineEnabled() { return outline.get(); }
}

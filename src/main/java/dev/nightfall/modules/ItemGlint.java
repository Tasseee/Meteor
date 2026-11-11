package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import net.minecraft.item.ItemStack;

import java.awt.Color;

/** Enables vanilla glint and locally tints its render vertices with a fixed or animated color. */
public final class ItemGlint extends Module {
    private final ModeSetting scope = add(new ModeSetting("Umfang", "Nur Hand", "Nur Hand", "Alle Items"));
    private final ModeSetting colorMode = add(new ModeSetting("Farbmodus", "Fest", "Fest", "Regenbogen"));
    private final ColorSetting fixedColor = add(new ColorSetting("Glitzerfarbe", 0x55AAFF));

    public ItemGlint() {
        super("Item Glint", "Aktiviert den Glitzereffekt mit fester Farbe oder Regenbogen für gehaltene oder alle Items.", Category.VISUALS);
    }

    public boolean forceGlint(ItemStack stack) {
        if (!isEnabled() || stack == null) return false;
        if (scope.is("Alle Items")) return true;
        return mc.player != null
                && (stack == mc.player.getMainHandStack() || stack == mc.player.getOffHandStack());
    }

    public boolean shouldTint() {
        return isEnabled();
    }

    /** RGB color used by the local glint render hook. Rainbow mode loops about every 3 seconds. */
    public int currentGlintColor() {
        if (!colorMode.is("Regenbogen")) return fixedColor.rgb();
        float hue = (System.currentTimeMillis() % 3000L) / 3000.0f;
        return Color.HSBtoRGB(hue, 0.9f, 1.0f) & 0xFFFFFF;
    }
}

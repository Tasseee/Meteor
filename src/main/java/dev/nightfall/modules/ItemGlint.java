package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import net.minecraft.item.ItemStack;

/** Adds vanilla enchantment glint only to the selected item-render scope. */
public final class ItemGlint extends Module {
    private final ModeSetting scope = add(new ModeSetting("Umfang", "Nur Hand", "Nur Hand", "Alle Items"));

    public ItemGlint() {
        super("Item Glint", "Aktiviert den nativen Glitzereffekt für gehaltene oder alle Items.", Category.VISUALS);
    }

    public boolean forceGlint(ItemStack stack) {
        if (!isEnabled() || stack == null) return false;
        if (scope.is("Alle Items")) return true;
        return mc.player != null
                && (stack == mc.player.getMainHandStack() || stack == mc.player.getOffHandStack());
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.util.Hand;

/**
 * Conservative fishing helper. Reels only when the bobber has a downward bite-like
 * velocity; it never casts repeatedly or switches items.
 */
public final class AutoFish extends Module {
    private final NumberSetting minimumInterval = add(new NumberSetting("Mindestabstand (Ticks)", 12, 8, 40, 1));
    private final BoolSetting mainHandOnly = add(new BoolSetting("Nur Haupthand", true));
    private int cooldown;

    public AutoFish() {
        super("AutoFish", "Zieht die Angel bei erkanntem Biss ein; wirft nicht automatisch erneut aus.", Category.MISC);
    }

    @Override protected void onEnable() { cooldown = 0; }
    @Override protected void onDisable() { cooldown = 0; }
    @Override public void onWorldLeave() { cooldown = 0; }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (mc.player.fishHook == null || !mc.player.fishHook.isAlive()) return;
        if (mainHandOnly.get() && !mc.player.getMainHandStack().isOf(net.minecraft.item.Items.FISHING_ROD)) return;
        // A bite briefly pulls the bobber down. Require a clear downward impulse to avoid
        // reeling on ordinary water movement.
        if (mc.player.fishHook.getVelocity().y < -0.08) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            cooldown = minimumInterval.getInt();
        }
    }
}

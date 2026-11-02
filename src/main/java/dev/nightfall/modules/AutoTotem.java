package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;

/** Haelt einen Totem in der Offhand (Tausch per Container-Klick, wie manuell im Inventar). */
public class AutoTotem extends Module {
    private final NumberSetting health = add(new NumberSetting("Health-Schwelle (0 = immer)", 0, 0, 20, 1));
    private final NumberSetting delay  = add(new NumberSetting("Delay (Ticks)", 2, 0, 20, 1));
    private int wait;

    public AutoTotem() { super("AutoTotem", "Legt einen Totem in die Offhand.", Category.COMBAT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;
        if (wait > 0) { wait--; return; }
        if (p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (health.get() > 0 && p.getHealth() + p.getAbsorptionAmount() > health.get()) return;
        int slot = InvUtil.findInventory(Items.TOTEM_OF_UNDYING);
        if (slot < 0) return;
        InvUtil.swapWithOffhand(slot);
        wait = delay.getInt();
    }
}

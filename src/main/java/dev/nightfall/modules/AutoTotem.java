package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
/** Keeps a Totem in the offhand without spamming clicks while the server is lagging. */
public final class AutoTotem extends Module {
    private final NumberSetting health = add(new NumberSetting("Health-Schwelle (0 = immer)", 0, 0, 20, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay (Ticks)", 2, 0, 20, 1));
    private static final int CONFIRM_TIMEOUT_TICKS = 12;
    private int wait, confirmTicks;
    private boolean swapPending;
    public AutoTotem() { super("AutoTotem", "Legt einen Totem mit Server-Bestaetigung in die Offhand.", Category.COMBAT); }
    @Override protected void onEnable() { reset(); }
    @Override protected void onDisable() { reset(); }
    @Override public void onWorldLeave() { reset(); }
    private void reset() { wait = confirmTicks = 0; swapPending = false; }
    @Override public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (swapPending) {
            if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                swapPending = false; confirmTicks = 0; wait = delay.getInt();
            } else if (--confirmTicks <= 0) {
                swapPending = false; wait = Math.max(4, delay.getInt());
            }
            return;
        }
        if (wait > 0) { wait--; return; }
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        if (health.get() > 0 && player.getHealth() + player.getAbsorptionAmount() > health.get()) return;
        int slot = InvUtil.findInventory(Items.TOTEM_OF_UNDYING);
        if (slot >= 0 && InvUtil.swapWithOffhand(slot)) { swapPending = true; confirmTicks = CONFIRM_TIMEOUT_TICKS; }
    }
}

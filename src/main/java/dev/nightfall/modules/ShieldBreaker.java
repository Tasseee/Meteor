package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;

/**
 * Selects an available hotbar axe for one client tick before attacking, so the
 * selected-slot update can reach the server before the attack packet.
 */
public final class ShieldBreaker extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 4.2, 2.0, 6.0, 0.2));
    private final NumberSetting delay = add(new NumberSetting("Angriffsintervall (Ticks)", 3, 1, 12, 1));
    private int cooldown;
    private int previousSlot = -1;
    private PlayerEntity pendingTarget;

    public ShieldBreaker() {
        super("ShieldBreaker", "Wechselt bei blockenden Gegnern kurz zur Axt und greift an.", Category.COMBAT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            restore();
            return;
        }

        // Finish a slot switch on the following tick, after vanilla has had a
        // chance to synchronize the selected hotbar slot with the server.
        if (previousSlot >= 0) {
            PlayerEntity target = pendingTarget;
            if (target != null && target.isAlive() && target.isBlocking()
                    && mc.player.squaredDistanceTo(target) <= range.get() * range.get()
                    && mc.player.canSee(target)) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
                cooldown = delay.getInt();
            }
            restore();
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        PlayerEntity target = CombatUtil.nearestPlayer(range.get());
        if (target == null || !target.isBlocking() || !mc.player.canSee(target)) return;

        int axeSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                axeSlot = i;
                break;
            }
        }
        if (axeSlot < 0) return;

        previousSlot = mc.player.getInventory().getSelectedSlot();
        pendingTarget = target;
        mc.player.getInventory().setSelectedSlot(axeSlot);
    }

    private void restore() {
        if (previousSlot >= 0 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
        pendingTarget = null;
    }

    @Override
    protected void onDisable() {
        restore();
        cooldown = 0;
    }

    @Override
    public void onWorldLeave() {
        restore();
        cooldown = 0;
    }
}

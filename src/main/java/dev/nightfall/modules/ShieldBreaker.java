package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;

/** Uses an available hotbar axe against a nearby opponent who is actively blocking. */
public final class ShieldBreaker extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 4.2, 2.0, 6.0, 0.2));
    private final NumberSetting delay = add(new NumberSetting("Angriffsintervall (Ticks)", 3, 1, 12, 1));
    private int cooldown;
    private int previousSlot = -1;
    public ShieldBreaker() { super("ShieldBreaker", "Wechselt bei blockenden Gegnern kurz zur Axt und greift an.", Category.COMBAT); }
    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (cooldown-- > 0) return;
        PlayerEntity target = CombatUtil.nearestPlayer(range.get());
        if (target == null || !target.isBlocking() || !mc.player.canSee(target)) return;
        int axe = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) { axe = i; break; }
        if (axe < 0) return;
        previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(axe);
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previousSlot);
        previousSlot = -1;
        cooldown = delay.getInt();
    }
    @Override protected void onDisable() { restore(); }
    @Override public void onWorldLeave() { restore(); }
    private void restore() { if (previousSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(previousSlot); previousSlot = -1; }
}

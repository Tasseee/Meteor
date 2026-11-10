package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Briefly raises an equipped shield when a nearby opponent starts a swing animation. */
public final class AutoShield extends Module {
    private final NumberSetting range = add(new NumberSetting("Erkennungsreichweite", 5.0, 2.0, 8.0, 0.5));
    private final NumberSetting holdTicks = add(new NumberSetting("Blockdauer (Ticks)", 7, 2, 15, 1));
    private final BoolSetting requireVisible = add(new BoolSetting("Nur sichtbare Gegner", true));

    private int ticksRemaining;
    private boolean ownsUseKey;
    private boolean previousUsePressed;

    public AutoShield() {
        super("AutoShield", "Hebt das ausgeruestete Schild bei einem nahen gegnerischen Schlag.", Category.COMBAT);
    }

    /** Called by the incoming entity-animation mixin; this is not an outgoing local swing. */
    public void onAttackAnimation(PlayerEntity attacker) {
        ClientPlayerEntity player = mc.player;
        if (!isEnabled() || player == null || mc.world == null || attacker == null
                || attacker == player || !attacker.isAlive() || attacker.isSpectator()
                || CombatUtil.isFriend(attacker)) return;
        if (player.squaredDistanceTo(attacker) > range.get() * range.get()) return;
        if (requireVisible.get() && !player.canSee(attacker)) return;
        if (!hasShieldEquipped(player)) return;
        ticksRemaining = Math.max(ticksRemaining, holdTicks.getInt());
    }

    private boolean hasShieldEquipped(ClientPlayerEntity player) {
        return player.getMainHandStack().isOf(Items.SHIELD)
                || player.getOffHandStack().isOf(Items.SHIELD);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.options == null || mc.currentScreen != null
                || !hasShieldEquipped(player) || ticksRemaining <= 0) {
            release();
            ticksRemaining = 0;
            return;
        }

        if (!ownsUseKey) {
            previousUsePressed = mc.options.useKey.isPressed();
            ownsUseKey = true;
        }
        mc.options.useKey.setPressed(true);
        ticksRemaining--;
        if (ticksRemaining <= 0) release();
    }

    private void release() {
        if (ownsUseKey && mc.options != null) {
            mc.options.useKey.setPressed(previousUsePressed);
        }
        ownsUseKey = false;
    }

    @Override protected void onDisable() {
        ticksRemaining = 0;
        release();
    }

    @Override public void onWorldLeave() {
        ticksRemaining = 0;
        release();
    }
}

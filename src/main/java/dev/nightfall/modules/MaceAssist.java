package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/**
 * Mace-Helfer fuer echte Smash-Angriffe: wechselt waehrend des Falls zur Mace und greift das Ziel unter dem
 * Fadenkreuz an, sobald die Fallhoehe reicht. Es wird KEINE Fallstrecke gefaelscht (keine Paket-Manipulation).
 * "estimate" = geschaetzter Schaden nach Vanilla-Formel (Basis 6 + Fall-Bonus).
 */
public class MaceAssist extends Module {
    private final NumberSetting minFall   = add(new NumberSetting("Min. Fallhoehe", 1.5, 1.5, 20, 0.5));
    private final BoolSetting autoSwitch  = add(new BoolSetting("Auto-Switch zur Mace", true));
    private final BoolSetting switchBack  = add(new BoolSetting("Zurueckwechseln", true));
    private final BoolSetting autoAttack  = add(new BoolSetting("Auto-Angriff", true));
    private final BoolSetting players     = add(new BoolSetting("Spieler", true));
    private final BoolSetting mobs        = add(new BoolSetting("Mobs", false));

    public double estimate;
    private int prevSlot = -1;

    public MaceAssist() { super("MaceAssist", "Mace-Smash: Auto-Switch und Angriff bei ausreichender Fallhoehe.", Category.COMBAT); }

    @Override protected void onDisable() {
        restoreSlot();
        estimate = 0;
    }

    @Override public void onWorldLeave() {
        restoreSlot();
        estimate = 0;
    }

    private void restoreSlot() {
        if (switchBack.get() && prevSlot >= 0 && mc.player != null) InvUtil.select(prevSlot);
        prevSlot = -1;
    }

    public static double smashDamage(double fall) {
        double bonus = fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
        return 6 + bonus;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        estimate = 0;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        double fall = p.fallDistance;
        if (p.isOnGround() || fall < minFall.get() || p.isTouchingWater() || p.isGliding()) {
            if (p.isOnGround() && prevSlot >= 0 && switchBack.get()) { InvUtil.select(prevSlot); prevSlot = -1; }
            return;
        }
        if (!p.getMainHandStack().isOf(Items.MACE)) {
            if (!autoSwitch.get()) return;
            int s = InvUtil.findHotbar(Items.MACE);
            if (s < 0) return;
            if (prevSlot < 0) prevSlot = InvUtil.selected();
            InvUtil.select(s);
        }
        estimate = smashDamage(fall);

        Entity t = mc.targetedEntity;
        if (!autoAttack.get() || !(t instanceof LivingEntity le) || !le.isAlive()) return;
        if (le instanceof PlayerEntity pe) { if (!players.get() || CombatUtil.isFriend(pe)) return; }
        else if (!mobs.get()) return;
        if (p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        mc.interactionManager.attackEntity(p, le);
        p.swingHand(Hand.MAIN_HAND);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

/** Greift an, sobald das Fadenkreuz auf ein gueltiges Ziel zeigt. Kein Zielen, keine Rotation. */
public class TriggerBot extends Module {
    private final BoolSetting players    = add(new BoolSetting("Spieler", true));
    private final BoolSetting hostile    = add(new BoolSetting("Feindliche Mobs", true));
    private final BoolSetting passive    = add(new BoolSetting("Passive Mobs", false));
    private final BoolSetting cooldown   = add(new BoolSetting("Cooldown pruefen", true));
    private final BoolSetting crit       = add(new BoolSetting("Crit abwarten", false));
    private final BoolSetting weaponOnly = add(new BoolSetting("Nur Waffe", true));
    private final NumberSetting delay    = add(new NumberSetting("Delay (Ticks)", 0, 0, 20, 1));
    private int wait;

    public TriggerBot() { super("TriggerBot", "Greift automatisch an, wenn das Fadenkreuz ein Ziel trifft.", Category.COMBAT); }

    @Override public void onWorldLeave() { wait = 0; }
    @Override protected void onDisable() { wait = 0; }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (wait > 0) { wait--; return; }
        Entity t = mc.targetedEntity;
        if (!(t instanceof LivingEntity le) || !le.isAlive() || le == p) return;
        if (le instanceof PlayerEntity pe) {
            if (!players.get() || CombatUtil.isFriend(pe)) return;
        } else if (le.getType().getSpawnGroup() == SpawnGroup.MONSTER) {
            if (!hostile.get()) return;
        } else if (!passive.get()) return;
        if (weaponOnly.get()) {
            var s = p.getMainHandStack();
            if (!(s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isOf(Items.MACE) || s.isOf(Items.TRIDENT))) return;
        }
        if (cooldown.get() && p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        if (crit.get()) {
            boolean c = !p.isOnGround() && p.fallDistance > 0 && !p.isClimbing() && !p.isTouchingWater()
                    && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();
            if (!c) return;
        }
        mc.interactionManager.attackEntity(p, le);
        p.swingHand(Hand.MAIN_HAND);
        wait = delay.getInt();
    }
}

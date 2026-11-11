package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Angriffs-Assistent: wirkt nur auf das Ziel unter dem Fadenkreuz (kein Aim-Bot). Greift an, wenn
 * Cooldown, Reichweite, Crit-Fenster und (optional) 1x2-Einfassung passen. Mit "Attack-Taste noetig"
 * greift er nur, solange die Angriffstaste gehalten wird.
 */
public class TrapCrit extends Module {
    private final NumberSetting range     = add(new NumberSetting("Reichweite", 3.0, 1.0, 6.0, 0.1));
    private final BoolSetting onlyTrapped = add(new BoolSetting("Nur eingeschlossene", true));
    private final BoolSetting cooldown    = add(new BoolSetting("Cooldown pruefen", true));
    private final BoolSetting crit        = add(new BoolSetting("Crit abwarten", true));
    private final BoolSetting needKey     = add(new BoolSetting("Attack-Taste noetig", true));
    private final BoolSetting players     = add(new BoolSetting("Spieler", true));
    private final BoolSetting mobs        = add(new BoolSetting("Mobs", false));

    public LivingEntity target;
    public boolean trapped, critReady;
    public float cooldownProgress;

    public TrapCrit() { super("TrapCrit", "Treffer-Assistent fuer eingeschlossene Ziele (Crit/Cooldown).", Category.COMBAT); }

    @Override protected void onDisable() { resetState(); }
    @Override public void onWorldLeave() { resetState(); }

    private void resetState() {
        target = null;
        trapped = false;
        critReady = false;
        cooldownProgress = 0.0f;
    }

    @Override
    public void onTick() {
        resetState();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || mc.interactionManager == null) return;
        Entity t = mc.targetedEntity;
        if (!(t instanceof LivingEntity le) || !le.isAlive() || le == p) return;
        boolean isPlayer = le instanceof PlayerEntity;
        if (isPlayer ? !players.get() : !mobs.get()) return;
        if (p.distanceTo(le) > range.get() || !p.canSee(le)) return;

        trapped = enclosed(le);
        if (onlyTrapped.get() && !trapped) return;

        target = le;
        cooldownProgress = p.getAttackCooldownProgress(0.5f);
        critReady = !p.isOnGround() && p.fallDistance > 0 && !p.isClimbing() && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS) && !p.hasVehicle() && !p.isSprinting();

        if (needKey.get() && !mc.options.attackKey.isPressed()) return;
        if (cooldown.get() && cooldownProgress < 0.95f) return;
        if (crit.get() && !critReady) return;

        mc.interactionManager.attackEntity(p, le);
        p.swingHand(Hand.MAIN_HAND);
    }

    private boolean solid(BlockPos pos) {
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    /** 1x2-Einfassung: alle vier Seiten auf Fuss- und Kopfhoehe plus Decke sind solide. */
    private boolean enclosed(LivingEntity e) {
        BlockPos feet = e.getBlockPos(), head = feet.up();
        for (Direction d : Direction.values()) {
            if (!d.getAxis().isHorizontal()) continue;
            if (!solid(feet.offset(d)) || !solid(head.offset(d))) return false;
        }
        return solid(head.up());
    }
}

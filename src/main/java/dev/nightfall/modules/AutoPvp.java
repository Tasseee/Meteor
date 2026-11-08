package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Central PvP profile: follows a nearby player and optionally coordinates the
 * existing survival/combat assistance modules. Those modules remain usable on
 * their own; AutoPvP only disables modules that it enabled itself.
 */
public class AutoPvp extends Module {
    private final NumberSetting follow = add(new NumberSetting("Verfolgen bis (Bloecke)", 12, 4, 32, 1));
    private final NumberSetting stop   = add(new NumberSetting("Abstand halten", 2.6, 1.5, 5, 0.1));
    private final NumberSetting reach  = add(new NumberSetting("Angriffsreichweite", 3.0, 1.0, 6.0, 0.1));
    private final BoolSetting attack   = add(new BoolSetting("Angreifen", true));
    private final BoolSetting strafe   = add(new BoolSetting("Strafen", true));
    private final BoolSetting jump     = add(new BoolSetting("Springen bei Hindernis", true));

    // Optional integrations. Defaults favour defensive assistance; explosive
    // crystal/anchor automation stays opt-in.
    private final BoolSetting useAutoGap = add(new BoolSetting("AutoGap integrieren", true));
    private final BoolSetting useAutoTotem = add(new BoolSetting("AutoTotem integrieren", true));
    private final BoolSetting useCrystalAura = add(new BoolSetting("CrystalAura integrieren", false));
    private final BoolSetting useAutoAnchor = add(new BoolSetting("AutoAnchor integrieren", false));
    private final BoolSetting useMaceAssist = add(new BoolSetting("MaceAssist integrieren", true));

    private boolean driving;
    private boolean wasForward, wasSprint, wasLeft, wasRight, wasJump;
    private boolean ownsAutoGap, ownsAutoTotem, ownsCrystalAura, ownsAutoAnchor, ownsMaceAssist;
    private int strafeDir = 1, strafeTimer;

    public AutoPvp() {
        super("AutoPvP", "Folgt dem naechsten Spieler und koordiniert optionale Combat-Helfer.", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        syncAssistModules();
    }

    @Override
    protected void onDisable() {
        release();
        disableOwnedAssistModules();
    }

    private void syncAssistModules() {
        ownsAutoGap = syncModule(AutoGap.class, useAutoGap.get(), ownsAutoGap);
        ownsAutoTotem = syncModule(AutoTotem.class, useAutoTotem.get(), ownsAutoTotem);
        ownsCrystalAura = syncModule(CrystalAura.class, useCrystalAura.get(), ownsCrystalAura);
        ownsAutoAnchor = syncModule(AutoAnchor.class, useAutoAnchor.get(), ownsAutoAnchor);
        ownsMaceAssist = syncModule(MaceAssist.class, useMaceAssist.get(), ownsMaceAssist);
    }

    private <T extends Module> boolean syncModule(Class<T> type, boolean requested, boolean owned) {
        T module = ModuleManager.get(type);
        if (requested) {
            if (!module.isEnabled()) {
                module.setEnabled(true);
                return true;
            }
            return owned;
        }
        if (owned && module.isEnabled()) module.setEnabled(false);
        return false;
    }

    private void disableOwnedAssistModules() {
        disableIfOwned(AutoGap.class, ownsAutoGap);
        disableIfOwned(AutoTotem.class, ownsAutoTotem);
        disableIfOwned(CrystalAura.class, ownsCrystalAura);
        disableIfOwned(AutoAnchor.class, ownsAutoAnchor);
        disableIfOwned(MaceAssist.class, ownsMaceAssist);
        ownsAutoGap = ownsAutoTotem = ownsCrystalAura = ownsAutoAnchor = ownsMaceAssist = false;
    }

    private <T extends Module> void disableIfOwned(Class<T> type, boolean owned) {
        if (!owned) return;
        T module = ModuleManager.get(type);
        if (module.isEnabled()) module.setEnabled(false);
    }

    private void release() {
        if (!driving || mc.options == null) return;
        mc.options.forwardKey.setPressed(wasForward);
        mc.options.sprintKey.setPressed(wasSprint);
        mc.options.leftKey.setPressed(wasLeft);
        mc.options.rightKey.setPressed(wasRight);
        mc.options.jumpKey.setPressed(wasJump);
        KeyBinding.updatePressedStates();
        driving = false;
    }

    @Override
    public void onTick() {
        syncAssistModules();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) {
            release();
            return;
        }

        PlayerEntity t = CombatUtil.nearestPlayer(follow.get());
        if (t == null) {
            release();
            return;
        }

        if (!driving) {
            wasForward = mc.options.forwardKey.isPressed();
            wasSprint = mc.options.sprintKey.isPressed();
            wasLeft = mc.options.leftKey.isPressed();
            wasRight = mc.options.rightKey.isPressed();
            wasJump = mc.options.jumpKey.isPressed();
        }

        Vec3d eye = p.getEyePos();
        double dx = t.getX() - eye.x, dy = t.getBodyY(0.5) - eye.y, dz = t.getZ() - eye.z;
        p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

        double dist = p.distanceTo(t);
        mc.options.forwardKey.setPressed(dist > stop.get());
        mc.options.sprintKey.setPressed(dist > stop.get() + 1);
        if (strafe.get() && dist <= reach.get() + 1) {
            if (strafeTimer-- <= 0) {
                strafeDir = -strafeDir;
                strafeTimer = 20 + (int) (dist * 4);
            }
            mc.options.leftKey.setPressed(strafeDir > 0);
            mc.options.rightKey.setPressed(strafeDir < 0);
        } else {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }
        mc.options.jumpKey.setPressed(jump.get() && p.horizontalCollision && p.isOnGround());
        driving = true;

        if (attack.get() && dist <= reach.get() && p.canSee(t)
                && p.getAttackCooldownProgress(0.5f) >= 0.95f) {
            mc.interactionManager.attackEntity(p, t);
            p.swingHand(Hand.MAIN_HAND);
        }
    }
}

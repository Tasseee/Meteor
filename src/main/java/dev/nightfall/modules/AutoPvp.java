package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Central PvP controller. Uses gradual rotations, checks nearby terrain before
 * moving, and coordinates the wind-charge-to-mace launch as a small state machine.
 */
public class AutoPvp extends Module {
    private final NumberSetting follow = add(new NumberSetting("Verfolgen bis (Bloecke)", 14, 4, 32, 1));
    private final NumberSetting stop = add(new NumberSetting("Abstand halten", 0.9, 0.8, 2.5, 0.1));
    private final NumberSetting reach = add(new NumberSetting("Angriffsreichweite", 3.2, 1.0, 6.0, 0.1));
    private final NumberSetting turnSpeed = add(new NumberSetting("Drehgeschwindigkeit", 16, 3, 35, 1));
    private final BoolSetting smoothLook = add(new BoolSetting("Smooth Rotation", true));
    private final BoolSetting attack = add(new BoolSetting("Angreifen", true));
    private final BoolSetting doubleTap = add(new BoolSetting("Double Tap (DTap)", false));
    private final NumberSetting doubleTapWindow = add(new NumberSetting("DTap-Zeitfenster (Ticks)", 18, 6, 40, 1));
    private final BoolSetting strafe = add(new BoolSetting("Strafen", true));
    private final BoolSetting jump = add(new BoolSetting("Springen bei Hindernis", true));
    private final BoolSetting sprintJump = add(new BoolSetting("Sprint-Jumps beim Verfolgen", true));
    private final BoolSetting preferCrits = add(new BoolSetting("Kritische Treffer bevorzugen", true));
    private final BoolSetting useStunSlam = add(new BoolSetting("Stun-Slam (Windcharge -> Mace)", true));
    private final BoolSetting useShieldBreak = add(new BoolSetting("Schild mit Axt kontern", true));
    private final BoolSetting useAutoGap = add(new BoolSetting("AutoGap integrieren", true));
    private final BoolSetting useAutoTotem = add(new BoolSetting("AutoTotem integrieren", true));
    private final BoolSetting useCrystalAura = add(new BoolSetting("CrystalAura integrieren", false));
    private final BoolSetting useAutoAnchor = add(new BoolSetting("AutoAnchor integrieren", false));
    private final BoolSetting useMaceAssist = add(new BoolSetting("MaceAssist integrieren", true));

    private boolean driving;
    private boolean wasForward, wasSprint, wasLeft, wasRight, wasJump;
    private boolean ownsAutoGap, ownsAutoTotem, ownsCrystalAura, ownsAutoAnchor, ownsMaceAssist;
    private int strafeDir = 1, strafeTimer;
    private boolean dtapPending;
    private int dtapTargetId = -1, dtapTicks;
    private int shieldPreviousSlot = -1, shieldAxeSlot = -1, shieldTargetId = -1;

    private enum SlamPhase { NONE, JUMP, AIM_AND_CHARGE, RISING, FALLING }
    private SlamPhase slamPhase = SlamPhase.NONE;
    private PlayerEntity slamTarget;
    private int slamTicks;
    private int originalSlot = -1;
    private int windSlot = -1;
    private int maceSlot = -1;

    public AutoPvp() {
        super("AutoPvP", "Kaempft situationsabhaengig mit sanfter Rotation und Umgebungschecks.", Category.COMBAT);
    }

    @Override protected void onEnable() { syncAssistModules(); }

    @Override protected void onDisable() {
        clearDoubleTap();
        cancelSlam(true);
        releaseShieldAxe();
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
            if (!module.isEnabled()) { module.setEnabled(true); return true; }
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
        if (owned) {
            T module = ModuleManager.get(type);
            if (module.isEnabled()) module.setEnabled(false);
        }
    }

    private void clearDoubleTap() {
        dtapPending = false;
        dtapTargetId = -1;
        dtapTicks = 0;
    }

    private void releaseShieldAxe() {
        if (shieldAxeSlot >= 0 && mc.player != null
                && mc.player.getInventory().getSelectedSlot() == shieldAxeSlot
                && shieldPreviousSlot >= 0) {
            InvUtil.select(shieldPreviousSlot);
        }
        shieldPreviousSlot = shieldAxeSlot = shieldTargetId = -1;
    }

    private void updateShieldAxe(PlayerEntity target) {
        if (!useShieldBreak.get() || target == null || !target.isBlocking()) {
            releaseShieldAxe();
            return;
        }
        if (shieldTargetId != -1 && shieldTargetId != target.getId()) releaseShieldAxe();
        if (shieldAxeSlot >= 0) {
            // Respect a manual hotbar change instead of fighting the user's input.
            if (InvUtil.selected() != shieldAxeSlot) {
                shieldPreviousSlot = shieldAxeSlot = shieldTargetId = -1;
            }
            return;
        }
        int axe = InvUtil.ensureHotbar(Items.NETHERITE_AXE);
        if (axe < 0) axe = InvUtil.ensureHotbar(Items.DIAMOND_AXE);
        if (axe < 0) axe = InvUtil.ensureHotbar(Items.IRON_AXE);
        if (axe < 0) return;
        shieldPreviousSlot = InvUtil.selected();
        shieldAxeSlot = axe;
        shieldTargetId = target.getId();
        InvUtil.select(axe);
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

    private void aimAt(double x, double y, double z) {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        Vec3d eye = p.getEyePos();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        if (!smoothLook.get()) {
            p.setYaw(yaw);
            p.setPitch(pitch);
            return;
        }
        float max = turnSpeed.get().floatValue();
        p.setYaw(p.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yaw - p.getYaw()), -max, max));
        p.setPitch(p.getPitch() + MathHelper.clamp(MathHelper.wrapDegrees(pitch - p.getPitch()), -max, max));
    }

    private boolean clearAbove(ClientPlayerEntity p, int height) {
        BlockPos feet = p.getBlockPos();
        for (int y = 1; y <= height; y++) {
            BlockPos pos = feet.up(y);
            BlockState state = mc.world.getBlockState(pos);
            if (!state.getCollisionShape(mc.world, pos).isEmpty()) return false;
        }
        return !p.isTouchingWater() && !p.isInLava() && !p.isGliding() && !p.hasVehicle();
    }

    /** Refuses to walk into a wall or over an unsupported edge. */
    private boolean safeForward(ClientPlayerEntity p, PlayerEntity target) {
        Vec3d toward = new Vec3d(target.getX() - p.getX(), 0, target.getZ() - p.getZ());
        if (toward.lengthSquared() < 1.0e-5) return true;
        toward = toward.normalize();
        BlockPos feet = BlockPos.ofFloored(p.getX() + toward.x * 0.85, p.getY(), p.getZ() + toward.z * 0.85);
        BlockPos head = feet.up();
        BlockPos floor = feet.down();
        boolean blocked = !mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty()
                || !mc.world.getBlockState(head).getCollisionShape(mc.world, head).isEmpty();
        boolean supported = !mc.world.getBlockState(floor).getCollisionShape(mc.world, floor).isEmpty();
        return !blocked && supported;
    }

    private void cancelSlam(boolean restoreSlot) {
        if (mc.options != null) mc.options.jumpKey.setPressed(wasJump);
        if (restoreSlot && originalSlot >= 0 && mc.player != null) InvUtil.select(originalSlot);
        slamPhase = SlamPhase.NONE;
        slamTarget = null;
        slamTicks = 0;
        originalSlot = windSlot = maceSlot = -1;
    }

    /** Start with a real jump, use a real Wind Charge, then switch to Mace while descending. */
    private boolean tickStunSlam(ClientPlayerEntity p, PlayerEntity target, double distance) {
        if (!useStunSlam.get()) {
            if (slamPhase != SlamPhase.NONE) cancelSlam(true);
            return false;
        }
        if (slamPhase == SlamPhase.NONE) {
            if (!p.isOnGround() || distance < 3.5 || distance > 7.5 || !p.canSee(target) || !clearAbove(p, 4)) return false;
            windSlot = InvUtil.ensureHotbar(Items.WIND_CHARGE);
            maceSlot = InvUtil.ensureHotbar(Items.MACE);
            if (windSlot < 0 || maceSlot < 0) return false;
            originalSlot = InvUtil.selected();
            slamTarget = target;
            slamPhase = SlamPhase.JUMP;
            slamTicks = 0;
        }
        if (slamTarget != target || !target.isAlive() || !clearAbove(p, 3) || slamTicks++ > 45) {
            cancelSlam(true);
            return false;
        }

        switch (slamPhase) {
            case JUMP -> {
                mc.options.jumpKey.setPressed(true);
                if (!p.isOnGround()) {
                    mc.options.jumpKey.setPressed(wasJump);
                    slamPhase = SlamPhase.AIM_AND_CHARGE;
                    slamTicks = 0;
                }
                return true;
            }
            case AIM_AND_CHARGE -> {
                aimAt(target.getX(), p.getEyeY() + 3.0, target.getZ());
                if (p.getPitch() <= -48.0f) {
                    InvUtil.select(windSlot);
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                    slamPhase = SlamPhase.RISING;
                    slamTicks = 0;
                }
                return true;
            }
            case RISING -> {
                if (p.getVelocity().y > 0.12) {
                    slamPhase = SlamPhase.FALLING;
                    InvUtil.select(maceSlot);
                } else if (slamTicks > 8) {
                    cancelSlam(true);
                    return false;
                }
                return true;
            }
            case FALLING -> {
                InvUtil.select(maceSlot);
                aimAt(target.getX(), target.getBodyY(0.55), target.getZ());
                if (p.getVelocity().y <= 0.0 && p.fallDistance >= 1.5
                        && p.distanceTo(target) <= 3.5 && p.canSee(target)
                        && p.getAttackCooldownProgress(0.5f) >= 0.95f) {
                    mc.interactionManager.attackEntity(p, target);
                    p.swingHand(Hand.MAIN_HAND);
                    cancelSlam(true);
                    return true;
                }
                if (p.isOnGround()) cancelSlam(true);
                return true;
            }
            default -> { return false; }
        }
    }

    @Override
    public void onWorldLeave() {
        clearDoubleTap();
        cancelSlam(true);
        releaseShieldAxe();
        release();
        disableOwnedAssistModules();
        driving = false;
        strafeTimer = 0;
    }

    @Override
    public void onTick() {
        syncAssistModules();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null || mc.world == null) {
            cancelSlam(true);
            releaseShieldAxe();
            release();
            return;
        }

        PlayerEntity target = CombatUtil.nearestPlayer(follow.get());
        if (target == null) {
            clearDoubleTap();
            cancelSlam(true);
            releaseShieldAxe();
            release();
            return;
        }

        if (!doubleTap.get()) clearDoubleTap();
        else if (dtapPending && (dtapTargetId != target.getId() || dtapTicks-- <= 0)) clearDoubleTap();

        if (!driving) {
            wasForward = mc.options.forwardKey.isPressed();
            wasSprint = mc.options.sprintKey.isPressed();
            wasLeft = mc.options.leftKey.isPressed();
            wasRight = mc.options.rightKey.isPressed();
            wasJump = mc.options.jumpKey.isPressed();
        }

        aimAt(target.getX(), target.getBodyY(0.5), target.getZ());
        double distance = p.distanceTo(target);
        if (tickStunSlam(p, target, distance)) {
            driving = true;
            return;
        }

        // Stay close instead of orbiting at the old 2.6-block default.
        boolean approach = distance > stop.get() && safeForward(p, target);
        boolean targetAirborne = !target.isOnGround() || target.getVelocity().y > 0.08;
        boolean chasing = distance > reach.get() + 0.35;
        boolean sprintingChase = approach && (distance > stop.get() + 0.5 || targetAirborne);
        mc.options.forwardKey.setPressed(approach);
        mc.options.sprintKey.setPressed(sprintingChase && !(preferCrits.get() && !p.isOnGround() && p.getVelocity().y < 0));
        if (strafe.get() && distance <= reach.get() + 1.5) {
            if (strafeTimer-- <= 0) {
                strafeDir = -strafeDir;
                strafeTimer = 16 + (int) (distance * 3);
            }
            mc.options.leftKey.setPressed(strafeDir > 0);
            mc.options.rightKey.setPressed(strafeDir < 0);
        } else {
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
        }

        boolean obstacle = p.horizontalCollision;
        boolean chaseJump = sprintJump.get() && chasing && p.isOnGround() && clearAbove(p, 2);
        boolean antiAirJump = sprintJump.get() && targetAirborne && distance > stop.get() + 0.25
                && p.isOnGround() && clearAbove(p, 2);
        mc.options.jumpKey.setPressed((jump.get() && obstacle && p.isOnGround() && clearAbove(p, 2))
                || chaseJump || antiAirJump);
        driving = true;

        updateShieldAxe(target);
        if (!attack.get() || distance > reach.get() || !p.canSee(target)
                || p.getAttackCooldownProgress(0.5f) < 0.95f) return;

        // A vanilla critical hit requires a falling, non-sprinting player.
        // Keep chasing with sprint-jumps, then briefly release sprint for the
        // descending hit window. If no crit window is available, do a normal hit.
        boolean critWindow = !p.isOnGround() && p.fallDistance > 0.0f
                && p.getVelocity().y < 0.0 && !p.isSprinting()
                && !p.isClimbing() && !p.isTouchingWater() && !p.isInLava()
                && !p.hasVehicle();
        if (preferCrits.get() && !p.isOnGround() && p.fallDistance <= 0.0f
                && p.getVelocity().y >= 0.0) return;

        // Ordinary hits use a sword rather than punching with an empty hand.
        // Shield-break axe selection above remains the intentional exception.
        if (shieldAxeSlot < 0) {
            int swordSlot = InvUtil.findBestSword();
            if (swordSlot < 0) return;
            InvUtil.select(swordSlot);
        }
        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);

        // DTap queues exactly one follow-up hit on the same target. The second
        // hit still waits for vanilla's full attack cooldown and visible reach.
        if (doubleTap.get()) {
            if (dtapPending) clearDoubleTap();
            else {
                dtapPending = true;
                dtapTargetId = target.getId();
                dtapTicks = doubleTapWindow.getInt();
            }
        } else clearDoubleTap();
    }
}

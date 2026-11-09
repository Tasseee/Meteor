package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Clientseitige freie Bootsbewegung in der Luft und im Wasser. */
public final class BoatFly extends Module {
    private final NumberSetting horizontal = add(new NumberSetting("Geschwindigkeit", 1.3, 0.1, 4.0, 0.1));
    private final NumberSetting vertical = add(new NumberSetting("Vertikal", 0.9, 0.1, 3.0, 0.1));
    private Entity controlled;
    private boolean previousNoGravity;

    public BoatFly() {
        super("BoatFly", "Freie Bootsbewegung in alle Richtungen, auch unter Wasser.", Category.MOVEMENT);
    }

    @Override
    protected void onEnable() {
        captureBoat();
    }

    private void captureBoat() {
        if (mc.player == null || !mc.player.hasVehicle()) return;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null || !vehicle.getType().toString().toLowerCase().contains("boat")) return;
        if (controlled != vehicle) {
            restoreBoat();
            controlled = vehicle;
            previousNoGravity = vehicle.hasNoGravity();
        }
        vehicle.setNoGravity(true);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.hasVehicle()) {
            restoreBoat();
            return;
        }
        Entity boat = mc.player.getVehicle();
        if (boat == null || !boat.getType().toString().toLowerCase().contains("boat")) {
            restoreBoat();
            return;
        }
        captureBoat();

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
        Vec3d right = new Vec3d(Math.cos(yr), 0, Math.sin(yr));
        Vec3d movement = Vec3d.ZERO;

        if (mc.options.forwardKey.isPressed()) movement = movement.add(forward);
        if (mc.options.backKey.isPressed()) movement = movement.subtract(forward);
        if (mc.options.rightKey.isPressed()) movement = movement.add(right);
        if (mc.options.leftKey.isPressed()) movement = movement.subtract(right);
        if (mc.options.jumpKey.isPressed()) movement = movement.add(0, 1, 0);
        if (mc.options.sneakKey.isPressed()) movement = movement.add(0, -1, 0);

        if (movement.lengthSquared() > 0) {
            Vec3d normalized = movement.normalize();
            boat.setVelocity(normalized.x * horizontal.get(), normalized.y * vertical.get(),
                    normalized.z * horizontal.get());
        } else {
            boat.setVelocity(Vec3d.ZERO);
        }
        boat.setNoGravity(true);
    }

    private void restoreBoat() {
        if (controlled != null) {
            controlled.setNoGravity(previousNoGravity);
            controlled = null;
        }
    }

    @Override
    protected void onDisable() {
        restoreBoat();
    }
}

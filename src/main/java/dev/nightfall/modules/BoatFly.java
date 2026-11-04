package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/** Client-side fast boat flight. Collision changes are local and server movement remains authoritative. */
public final class BoatFly extends Module {
    private final NumberSetting speed = add(new NumberSetting("Fluggeschwindigkeit", 3.0, 0.1, 20.0, 0.1));
    private final BoolSetting throughBlocks = add(new BoolSetting("Durch Bloecke", true));
    private final BoolSetting sprintBoost = add(new BoolSetting("Sprint-Boost", true));

    private Entity activeBoat;
    private boolean previousNoClip;
    private boolean previousNoGravity;

    public BoatFly() {
        super("BoatFly", "Lässt dein Boot sehr schnell fliegen und optional lokal durch Bloecke gehen.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        Entity vehicle = mc.player == null ? null : mc.player.getVehicle();
        if (!(vehicle instanceof AbstractBoatEntity boat)) {
            restoreBoat();
            return;
        }

        if (activeBoat != boat) {
            restoreBoat();
            activeBoat = boat;
            previousNoClip = boat.noClip;
            previousNoGravity = boat.hasNoGravity();
        }

        boat.noClip = throughBlocks.get();
        boat.setNoGravity(true);

        double amount = speed.get();
        if (sprintBoost.get() && mc.options.sprintKey.isPressed()) amount *= 1.75;

        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0)
                - (mc.options.backKey.isPressed() ? 1 : 0);
        double sideways = (mc.options.leftKey.isPressed() ? 1 : 0)
                - (mc.options.rightKey.isPressed() ? 1 : 0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1 : 0)
                - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double x = -Math.sin(yaw) * Math.cos(pitch) * forward + Math.cos(yaw) * sideways;
        double y = -Math.sin(pitch) * forward + vertical;
        double z = Math.cos(yaw) * Math.cos(pitch) * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 0) {
            x /= length;
            y /= length;
            z /= length;
        }

        boat.setVelocity(new Vec3d(x * amount, y * amount, z * amount));
        boat.setYaw(mc.player.getYaw());
    }

    private void restoreBoat() {
        if (activeBoat == null) return;
        activeBoat.noClip = previousNoClip;
        activeBoat.setNoGravity(previousNoGravity);
        activeBoat.setVelocity(Vec3d.ZERO);
        activeBoat = null;
    }

    @Override
    protected void onDisable() {
        restoreBoat();
    }
}

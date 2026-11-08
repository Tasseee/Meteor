package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side boat flight. noClip/noGravity are local entity flags; the server
 * remains authoritative and may correct movement in multiplayer.
 */
public final class BoatFly extends Module {
    private final NumberSetting speed = add(new NumberSetting("Fluggeschwindigkeit", 0.7, 0.1, 20.0, 0.1));
    private final BoolSetting throughBlocks = add(new BoolSetting("Durch Bloecke", true));
    private final BoolSetting sprintBoost = add(new BoolSetting("Sprint-Boost", true));

    private AbstractBoatEntity activeBoat;
    private boolean previousNoClip;
    private boolean previousNoGravity;

    public BoatFly() {
        super("BoatFly", "Lässt dein Boot fliegen; lokale Kollisionsfreiheit ist serverabhängig.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) {
            restoreBoat();
            return;
        }

        Entity vehicle = mc.player.getVehicle();
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

        double forward = (mc.options.forwardKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.backKey.isPressed() ? 1.0 : 0.0);
        double sideways = (mc.options.rightKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.leftKey.isPressed() ? 1.0 : 0.0);
        double vertical = (mc.options.jumpKey.isPressed() ? 1.0 : 0.0)
                - (mc.options.sneakKey.isPressed() ? 1.0 : 0.0);

        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double x = -Math.sin(yaw) * Math.cos(pitch) * forward + Math.cos(yaw) * sideways;
        double y = -Math.sin(pitch) * forward + vertical;
        double z = Math.cos(yaw) * Math.cos(pitch) * forward + Math.sin(yaw) * sideways;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0E-6) {
            x /= length;
            y /= length;
            z /= length;
        } else {
            x = 0.0;
            y = 0.0;
            z = 0.0;
        }

        boat.setVelocity(x * amount, y * amount, z * amount);
        boat.setYaw(mc.player.getYaw());
    }

    private void restoreBoat() {
        if (activeBoat == null) return;
        activeBoat.noClip = previousNoClip;
        activeBoat.setNoGravity(previousNoGravity);
        activeBoat = null;
    }

    @Override
    protected void onDisable() {
        restoreBoat();
    }
}

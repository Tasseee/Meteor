package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Client-side Elytra steering and speed control. Server movement validation still applies. */
public final class ElytraFly extends Module {
    private final NumberSetting horizontal = add(new NumberSetting("Horizontal (b/s)", 24, 5, 60, 1));
    private final NumberSetting vertical = add(new NumberSetting("Vertical (b/s)", 8, 1, 25, 1));

    public ElytraFly() {
        super("ElytraFly", "Steuert den Gleitflug: WASD, Space hoch, Shift runter.", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || !player.isGliding() || player.hasVehicle()) return;

        Vec3d direction = MoveUtil.direction();
        Vec3d velocity = player.getVelocity();
        double horizontalSpeed = horizontal.get() / 20.0;
        double verticalSpeed = vertical.get() / 20.0;

        double x = direction.lengthSquared() > 0 ? direction.x * horizontalSpeed : velocity.x;
        double z = direction.lengthSquared() > 0 ? direction.z * horizontalSpeed : velocity.z;
        double y = mc.options.jumpKey.isPressed() ? verticalSpeed
                : mc.options.sneakKey.isPressed() ? -verticalSpeed : velocity.y;

        player.setVelocity(x, y, z);
    }
}

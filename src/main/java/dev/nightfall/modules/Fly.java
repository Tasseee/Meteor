package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Fly extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed (b/s)", 10, 1, 40, 0.5));
    private final NumberSetting vertical = add(new NumberSetting("Vertical (b/s)", 8, 1, 30, 0.5));

    public Fly() { super("Fly", "Clientseitiger Flug: WASD, Space hoch, Shift runter.", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || p.hasVehicle() || p.isGliding()) return;
        Vec3d dir = MoveUtil.direction();
        double h = speed.get() / 20.0;
        double v = vertical.get() / 20.0;
        double vy = mc.options.jumpKey.isPressed() ? v : mc.options.sneakKey.isPressed() ? -v : 0;
        p.setVelocity(dir.x * h, vy, dir.z * h);
    }
}

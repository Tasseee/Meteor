package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Laufgeschwindigkeit im Wasser wie an Land (ohne Wasserwiderstand). Unabhaengig von Jesus. */
public class WaterWalk extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed (b/s)", 4.3, 1, 12, 0.1));

    public WaterWalk() { super("WaterWalk", "Gehen im Wasser mit Landgeschwindigkeit.", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || p.getAbilities().flying || p.hasVehicle() || !p.isTouchingWater()) return;
        Vec3d d = MoveUtil.direction();
        if (d == Vec3d.ZERO) return;
        double s = speed.get() / 20.0;
        Vec3d v = p.getVelocity();
        p.setVelocity(d.x * s, v.y, d.z * s);
    }
}

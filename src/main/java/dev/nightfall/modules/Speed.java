package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    private static final double BASE_SPRINT = 0.2806; // ca. Sprint-Geschwindigkeit in Bloecken/Tick
    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", 1.4, 1.0, 3.0, 0.05));
    private final BoolSetting onlyGround = add(new BoolSetting("Only Ground", false));

    public Speed() { super("Speed", "Erhoeht die Laufgeschwindigkeit (nur bei Bewegung).", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || p.hasVehicle() || p.isTouchingWater() || p.isInLava() || p.isClimbing()
                || p.getAbilities().flying || p.isGliding()) return;
        if (onlyGround.get() && !p.isOnGround()) return;
        Vec3d dir = MoveUtil.direction();
        if (dir == Vec3d.ZERO) return; // im Stillstand nichts veraendern
        double s = BASE_SPRINT * multiplier.get();
        Vec3d vel = p.getVelocity();
        p.setVelocity(dir.x * s, vel.y, dir.z * s);
    }
}

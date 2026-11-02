package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.MoveUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class LongJump extends Module {
    private final NumberSetting boost = add(new NumberSetting("Boost", 0.55, 0.2, 1.5, 0.05));

    public LongJump() { super("LongJump", "Zusaetzlicher Vorwaertsschub beim Absprung.", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || !p.isOnGround() || !mc.options.jumpKey.isPressed() || p.isTouchingWater()) return;
        Vec3d d = MoveUtil.direction();
        if (d == Vec3d.ZERO) return;
        Vec3d v = p.getVelocity();
        p.setVelocity(d.x * boost.get(), Math.max(v.y, 0.42), d.z * boost.get());
    }
}

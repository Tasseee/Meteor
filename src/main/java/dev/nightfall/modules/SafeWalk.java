package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class SafeWalk extends Module {
    private final NumberSetting maxDrop = add(new NumberSetting("Max. Fallhoehe", 1.0, 1.0, 4.0, 0.5));

    public SafeWalk() { super("SafeWalk", "Stoppt an Kanten (clientseitig).", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || !p.isOnGround() || p.isSneaking() || p.getAbilities().flying) return;
        Vec3d v = p.getVelocity();
        if (v.x == 0 && v.z == 0) return;
        Box shifted = p.getBoundingBox().offset(v.x, -maxDrop.get(), v.z);
        if (mc.world.isSpaceEmpty(p, shifted)) p.setVelocity(0, v.y, 0);
    }
}

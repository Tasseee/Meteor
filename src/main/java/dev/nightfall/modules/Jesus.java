package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Haelt den Spieler an der Wasseroberflaeche (Schweben). Shift = absinken, Space = steigen. */
public class Jesus extends Module {
    public Jesus() { super("Jesus", "Schwebt an der Wasseroberflaeche.", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || p.getAbilities().flying || p.hasVehicle()) return;
        if (!p.isTouchingWater()) return;
        if (mc.options.sneakKey.isPressed() || mc.options.jumpKey.isPressed()) return;
        boolean headUnder = mc.world.getFluidState(p.getBlockPos().up()).isIn(net.minecraft.registry.tag.FluidTags.WATER);
        Vec3d v = p.getVelocity();
        p.setVelocity(v.x, headUnder ? 0.18 : 0.0, v.z);
    }
}

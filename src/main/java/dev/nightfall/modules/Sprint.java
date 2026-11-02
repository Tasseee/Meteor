package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;

public class Sprint extends Module {
    public Sprint() { super("Sprint", "Sprintet automatisch beim Vorwaertslaufen.", Category.MOVEMENT); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        boolean ok = mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.horizontalCollision
                && p.getHungerManager().getFoodLevel() > 6 && !p.isUsingItem();
        if (ok) p.setSprinting(true);
    }
}

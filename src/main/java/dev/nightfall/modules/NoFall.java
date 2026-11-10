package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;

/** Reduces local fall-distance accumulation; the server remains authoritative for damage. */
public final class NoFall extends Module {
    private final BoolSetting onlyAbove = add(new BoolSetting("Nur ab 3 Bloecken", true));
    public NoFall() { super("NoFall", "Setzt die lokale Falldistanz vor der Landung zurueck.", Category.MOVEMENT); }
    @Override public void onTick() {
        if (mc.player == null || mc.player.isGliding() || mc.player.isTouchingWater() || mc.player.isInLava()) return;
        if (!mc.player.isOnGround() && mc.player.getVelocity().y < 0
                && (!onlyAbove.get() || mc.player.fallDistance >= 3.0f)) {
            mc.player.fallDistance = 0.0f;
        }
    }
}

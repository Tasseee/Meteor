package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Orbits the nearest non-friend player while movement input is held. */
public final class TargetStrafe extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 5.0, 2.0, 8.0, 0.5));
    private final NumberSetting speed = add(new NumberSetting("Strafe-Speed", 0.22, 0.08, 0.45, 0.01));
    private final BoolSetting clockwise = add(new BoolSetting("Im Uhrzeigersinn", true));
    private final BoolSetting requireJump = add(new BoolSetting("Nur mit Springen", false));
    public TargetStrafe() { super("TargetStrafe", "Kreist um den naechsten Gegner, solange Bewegungstasten gedrueckt sind.", Category.COMBAT); }
    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.player.hasVehicle() || mc.currentScreen != null) return;
        if (!mc.options.forwardKey.isPressed() && !mc.options.backKey.isPressed()
                && !mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) return;
        if (requireJump.get() && !mc.options.jumpKey.isPressed()) return;
        PlayerEntity target = CombatUtil.nearestPlayer(range.get());
        if (target == null) return;
        Vec3d away = mc.player.getEntityPos().subtract(target.getEntityPos());
        double len = Math.sqrt(away.x * away.x + away.z * away.z);
        if (len < 0.001) return;
        double sign = clockwise.get() ? 1.0 : -1.0;
        double tx = -away.z / len * sign, tz = away.x / len * sign;
        double radial = Math.max(-0.08, Math.min(0.08, (len - 2.7) * 0.035));
        mc.player.setVelocity((tx * speed.get()) + away.x / len * radial,
                mc.player.getVelocity().y, (tz * speed.get()) + away.z / len * radial);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** A small local particle ring when the player lands after a jump. */
public final class JumpCircles extends Module {
    private final ModeSetting style = add(new ModeSetting("Effekt", "Cloud", "Cloud", "Enchant", "Soul", "Flamme", "Herzen"));
    private final NumberSetting radius = add(new NumberSetting("Radius", 0.8, 0.3, 1.8, 0.1));
    private boolean airborne;

    public JumpCircles() { super("JumpCircles", "Partikelkreis beim Landen nach einem Sprung (lokal).", Category.VISUALS); }
    @Override protected void onEnable() { airborne = false; }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isOnGround()) { airborne = true; return; }
        if (!airborne) return;
        airborne = false;
        ParticleEffect p = switch (style.get()) {
            case "Enchant" -> ParticleTypes.ENCHANT;
            case "Soul" -> ParticleTypes.SOUL;
            case "Flamme" -> ParticleTypes.FLAME;
            case "Herzen" -> ParticleTypes.HEART;
            default -> ParticleTypes.CLOUD;
        };
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2 * i / 18.0;
            double x = mc.player.getX() + Math.cos(angle) * radius.get();
            double z = mc.player.getZ() + Math.sin(angle) * radius.get();
            mc.particleManager.addParticle(p, x, mc.player.getY() + 0.08, z,
                    Math.cos(angle) * 0.025, 0.025, Math.sin(angle) * 0.025);
        }
    }
}

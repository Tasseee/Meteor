package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Local-only cosmetic particles following the player while moving. */
public final class ParticleTrail extends Module {
    private final ModeSetting style = add(new ModeSetting("Partikel", "Enchant", "Enchant", "Soul", "Portal", "Herzen", "Wolke", "Flamme"));
    private final NumberSetting density = add(new NumberSetting("Dichte", 3, 1, 8, 1));
    private final BoolSetting onlyMoving = add(new BoolSetting("Nur beim Bewegen", true));
    private int ticks;

    public ParticleTrail() { super("ParticleTrail", "Kosmetische Partikelspur hinter dir (nur lokal).", Category.VISUALS); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks % 2 != 0) return;
        if (onlyMoving.get() && mc.player.getVelocity().horizontalLengthSquared() < 0.0025) return;
        ParticleEffect particle = particle();
        for (int i = 0; i < density.getInt(); i++) {
            double x = mc.player.getX() + (Math.random() - 0.5) * 0.65;
            double y = mc.player.getY() + 0.15 + Math.random() * 1.25;
            double z = mc.player.getZ() + (Math.random() - 0.5) * 0.65;
            mc.particleManager.addParticle(particle, x, y, z, 0, 0.015, 0);
        }
    }

    private ParticleEffect particle() {
        return switch (style.get()) {
            case "Soul" -> ParticleTypes.SOUL;
            case "Portal" -> ParticleTypes.PORTAL;
            case "Herzen" -> ParticleTypes.HEART;
            case "Wolke" -> ParticleTypes.CLOUD;
            case "Flamme" -> ParticleTypes.FLAME;
            default -> ParticleTypes.ENCHANT;
        };
    }
}

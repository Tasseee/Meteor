package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Spawns client-side particles when a nearby living entity loses health. */
public final class HitParticles extends Module {
    private final ModeSetting style = add(new ModeSetting("Effekt", "Crit", "Crit", "Herzen", "Flamme", "Witch", "Soul", "Enchant"));
    private final NumberSetting amount = add(new NumberSetting("Partikelmenge", 8, 2, 20, 1));
    private final NumberSetting range = add(new NumberSetting("Reichweite", 8, 3, 16, 1));
    private final Map<UUID, Float> lastHealth = new HashMap<>();

    public HitParticles() { super("HitParticles", "Partikeleffekt, wenn nahe Entities Schaden bekommen (lokal).", Category.VISUALS); }

    @Override protected void onEnable() { lastHealth.clear(); }
    @Override protected void onDisable() { lastHealth.clear(); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null) return;
        for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(range.get()), e -> e != mc.player && e.isAlive())) {
            UUID id = entity.getUuid();
            float health = entity.getHealth();
            Float old = lastHealth.put(id, health);
            if (old != null && health < old) burst(entity);
        }
        if (lastHealth.size() > 512) lastHealth.clear();
    }

    private void burst(LivingEntity entity) {
        ParticleEffect p = switch (style.get()) {
            case "Herzen" -> ParticleTypes.HEART;
            case "Flamme" -> ParticleTypes.FLAME;
            case "Witch" -> ParticleTypes.WITCH;
            case "Soul" -> ParticleTypes.SOUL;
            case "Enchant" -> ParticleTypes.ENCHANT;
            default -> ParticleTypes.CRIT;
        };
        for (int i = 0; i < amount.getInt(); i++) {
            double dx = (Math.random() - 0.5) * 0.8;
            double dy = Math.random() * 1.5;
            double dz = (Math.random() - 0.5) * 0.8;
            mc.world.addParticle(p, entity.getX() + dx, entity.getY() + dy, entity.getZ() + dz,
                    dx * 0.15, 0.08 + Math.random() * 0.12, dz * 0.15);
        }
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;

public class EntityEsp extends EntityScanEsp {
    private final BoolSetting hostile = add(new BoolSetting("Feindlich", true));
    private final BoolSetting passive = add(new BoolSetting("Passiv", false));

    public EntityEsp() { super("EntityESP", "Markiert Mobs.", 128, 512, 0xF59E0B); }

    @Override protected boolean accept(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof PlayerEntity) return false;
        boolean h = e.getType().getSpawnGroup() == SpawnGroup.MONSTER;
        return h ? hostile.get() : passive.get();
    }
}

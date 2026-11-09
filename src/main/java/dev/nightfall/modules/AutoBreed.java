package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.util.Hand;

/** Feeds nearby animals only when the held item is valid breeding food. */
public final class AutoBreed extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 4, 2, 6, 1));
    private final NumberSetting interval = add(new NumberSetting("Intervall (Ticks)", 20, 10, 80, 5));
    private int cooldown;

    public AutoBreed() {
        super("AutoBreed", "Füttert jeweils ein passendes, noch nicht verliebtes Tier in der Nähe.", Category.WORLD);
    }

    @Override protected void onEnable() { cooldown = 0; }
    @Override protected void onDisable() { cooldown = 0; }
    @Override public void onWorldLeave() { cooldown = 0; }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) { cooldown--; return; }
        var food = mc.player.getMainHandStack();
        if (food.isEmpty()) return;
        var animals = mc.world.getEntitiesByClass(AnimalEntity.class,
                mc.player.getBoundingBox().expand(range.get()),
                animal -> animal.isAlive() && !animal.isInLove() && animal.isBreedingItem(food));
        if (animals.isEmpty()) return;
        AnimalEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AnimalEntity animal : animals) {
            double distance = mc.player.squaredDistanceTo(animal);
            if (distance < nearestDistance) {
                nearest = animal;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return;
        mc.interactionManager.interactEntity(mc.player, nearest, Hand.MAIN_HAND);
        cooldown = interval.getInt();
    }
}

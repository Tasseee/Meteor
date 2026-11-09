package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public class Step extends Module {
    private static final double VANILLA = 0.6;
    private final NumberSetting height = add(new NumberSetting("Hoehe", 1.0, 0.6, 2.5, 0.1));

    public Step() { super("Step", "Erhoeht die Stufenhoehe (Attribut step_height).", Category.MOVEMENT); }

    private double previousHeight = VANILLA;
    private boolean captured;

    private EntityAttributeInstance attr() {
        return mc.player == null ? null : mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
    }

    @Override public void onTick() {
        EntityAttributeInstance a = attr();
        if (a == null) return;
        if (!captured) {
            previousHeight = a.getBaseValue();
            captured = true;
        }
        if (a.getBaseValue() != height.get()) a.setBaseValue(height.get());
    }

    @Override protected void onDisable() {
        restore();
    }

    @Override public void onWorldLeave() {
        restore();
    }

    private void restore() {
        EntityAttributeInstance a = attr();
        if (captured && a != null) a.setBaseValue(previousHeight);
        captured = false;
        previousHeight = VANILLA;
    }
}

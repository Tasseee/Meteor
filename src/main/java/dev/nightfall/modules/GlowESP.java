package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;

/** Uses Minecraft's built-in entity outline render pass (the native glow shader), client-side only. */
public final class GlowESP extends Module {
    private final BoolSetting players = add(new BoolSetting("Spieler", true));
    private final BoolSetting hostile = add(new BoolSetting("Feindliche Mobs", true));
    private final BoolSetting passive = add(new BoolSetting("Passive Mobs", false));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", true));
    private final NumberSetting range = add(new NumberSetting("Reichweite", 96, 8, 256, 8));
    private final ColorSetting color = add(new ColorSetting("Glow-Farbe", 0x55EEDD));

    public GlowESP() {
        super("GlowESP", "Echter Minecraft-Outline-Shader für ausgewählte Entities; nur lokale Darstellung.", Category.VISUALS);
    }

    public boolean shouldGlow(Entity entity) {
        if (!isEnabled() || mc.player == null || mc.world == null || entity == null || entity == mc.player || !(entity instanceof LivingEntity)) return false;
        if (entity.squaredDistanceTo(mc.player) > range.get() * range.get()) return false;
        if (!throughWalls.get() && !mc.player.canSee(entity)) return false;
        if (entity instanceof PlayerEntity) return players.get();
        SpawnGroup group = entity.getType().getSpawnGroup();
        return group == SpawnGroup.MONSTER ? hostile.get() : passive.get();
    }

    public int outlineRgb() { return color.rgb(); }

    public static boolean shouldGlowLocal(Entity entity) {
        try { return dev.nightfall.module.ModuleManager.get(GlowESP.class).shouldGlow(entity); }
        catch (Throwable ignored) { return false; }
    }

    public static int colorFor(Entity entity, int fallback) {
        try {
            GlowESP glow = dev.nightfall.module.ModuleManager.get(GlowESP.class);
            return glow.shouldGlow(entity) ? glow.outlineRgb() : fallback;
        } catch (Throwable ignored) { return fallback; }
    }
}

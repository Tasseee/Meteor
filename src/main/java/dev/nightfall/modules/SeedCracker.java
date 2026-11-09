package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.util.Theme;
import kaptainwutax.seedcrackerX.config.Config;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Toggles SeedCrackerX background collection and displays completed results prominently. */
public final class SeedCracker extends Module {
    private static long foundSeed;
    private static long foundAt;
    private static boolean hasResult;

    public SeedCracker() {
        super("SeedCracker", "Sammelt Welt-Daten im Hintergrund und meldet gefundene Seeds.", Category.WORLD);
    }

    @Override
    protected void onEnable() {
        setCrackerActive(true);
        hasResult = false;
    }

    @Override
    protected void onDisable() {
        setCrackerActive(false);
        hasResult = false;
    }

    /** Synchronize the bundled mod with the saved Nightfall module state after startup. */
    public static void syncState() {
        try {
            setCrackerActive(dev.nightfall.module.ModuleManager.get(SeedCracker.class).isEnabled());
        } catch (Throwable ignored) {
            // Module initialization may not be complete during early client startup.
        }
    }

    private static void setCrackerActive(boolean active) {
        try {
            Config.get().active = active;
            Config.save();
        } catch (Throwable t) {
            dev.nightfall.Nightfall.LOGGER.error("SeedCrackerX konnte nicht umgeschaltet werden", t);
        }
    }

    /** Called on the Minecraft client thread after SeedCrackerX completes a seed. */
    public static void reportSeed(long seed) {
        SeedCracker module;
        try {
            module = dev.nightfall.module.ModuleManager.get(SeedCracker.class);
        } catch (Throwable ignored) {
            return;
        }
        if (!module.isEnabled()) return;
        foundSeed = seed;
        foundAt = System.currentTimeMillis();
        hasResult = true;
        dev.nightfall.util.NotificationCenter.push("SEED GEFUNDEN", Long.toString(seed), 12_000L, 0xFF65E6A5);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!hasResult) return;
        long elapsed = System.currentTimeMillis() - foundAt;
        if (elapsed >= 12_000L) {
            hasResult = false;
            return;
        }

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int width = Math.min(420, sw - 24);
        int height = 92;
        int x = (sw - width) / 2;
        int y = Math.max(12, sh / 2 - height / 2);
        float fade = elapsed > 10_500L ? (12_000L - elapsed) / 1_500f : 1f;
        int alpha = Math.max(0, Math.min(255, Math.round(235 * fade)));
        int panel = (alpha << 24) | 0x10131D;
        int accent = (alpha << 24) | 0x65E6A5;
        int white = (alpha << 24) | 0xFFFFFF;

        ctx.fill(x, y, x + width, y + height, panel);
        ctx.fill(x, y, x + width, y + 4, accent);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("SEED GEFUNDEN"), sw / 2, y + 17, accent);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(Long.toString(foundSeed)), sw / 2, y + 39, white);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Klicken/Kopieren über Chat: Seed markieren"), sw / 2, y + 66, white);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

/**
 * Historical view of the shared StaffWatch samples.
 * This module does not independently infer vanish or staff permissions.
 */
public final class AntiVanishHints extends Module {
    public AntiVanishHints() {
        super("AntiVanishHints", "Zeigt wiederholte Tab-/Entity-Lücken aus StaffWatch; keine Vanish-Bestätigung.", Category.MISC);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        ModuleManager.get(StaffWatch.class).sampleObservations();
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;
        StaffWatch watch = ModuleManager.get(StaffWatch.class);
        if (watch.staffNames.get().isEmpty()) return;
        watch.sampleObservations();

        int x = 8, y = 90;
        ctx.drawText(mc.textRenderer, "AntiVanish · Verlaufsdaten", x, y, Theme.ACCENT, true);
        y += 12;
        ctx.drawText(mc.textRenderer, "Wiederholte Lücken sind nur Beobachtungen, kein Vanish-Beweis.",
                x, y, 0xFFFFC857, false);
        y += 12;

        int shown = 0;
        for (String name : watch.staffNames.get()) {
            if (shown++ >= 6) break;
            StaffWatch.ObservationView o = watch.observation(name);
            String line;
            if (o == null) {
                line = name + " · noch keine Stichproben";
            } else {
                line = name + " · Tab fehlt " + o.tabAbsentSamples + "/" + o.samples
                        + " · Entity fehlt " + o.entityAbsentSamples + "/" + o.samples
                        + " · aktuelle Tab-Lücke " + o.tabAbsentStreak;
                if ("SPECTATOR".equalsIgnoreCase(o.gameMode)) line += " · Spectator";
                if (o.invisible) line += " · invis-Status am Entity";
            }
            ctx.drawText(mc.textRenderer, line, x, y, 0xFFB7B7C8, false);
            y += 11;
        }
        ctx.drawText(mc.textRenderer, "Serverrechte und serverseitiger Vanish-Zustand sind nicht einsehbar.",
                x, y + 2, 0xFFFFC857, false);
    }
}

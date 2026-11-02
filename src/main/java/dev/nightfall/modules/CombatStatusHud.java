package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

public class CombatStatusHud extends HudModule {
    public CombatStatusHud() { super("CombatStatus", "Ziel, Distanz, Cooldown und Crit-Status."); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw / 2 + 12, sh / 2 + 12}; }

    @Override
    protected void draw(DrawContext ctx) {
        TrapCrit tc = ModuleManager.get(TrapCrit.class);
        LivingEntity t = tc.target;
        double mace = ModuleManager.get(MaceAssist.class).estimate;
        w = 110; h = mace > 0 ? 46 : 34;
        ctx.fill(0, 0, w, h, 0xA0101014);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        if (t == null || mc.player == null) {
            ctx.drawText(mc.textRenderer, tc.isEnabled() ? "Kein Ziel" : "TrapCrit aus", 6, 3, Theme.TEXT_DIM, false);
            return;
        }
        ctx.drawText(mc.textRenderer, t.getName().getString(), 6, 3, Theme.TEXT, false);
        ctx.drawText(mc.textRenderer, String.format("%.1f m  %s  %s", mc.player.distanceTo(t),
                tc.trapped ? "TRAP" : "frei", tc.critReady ? "CRIT" : "-"), 6, 13, Theme.TEXT_DIM, false);
        int bw = (int) ((w - 12) * Math.min(1f, tc.cooldownProgress));
        ctx.fill(6, 25, w - 6, 29, 0xFF2A2A34);
        ctx.fill(6, 25, 6 + bw, 29, Theme.ACCENT);
        if (mace > 0) ctx.drawText(mc.textRenderer, String.format("Mace ~%.0f Schaden", mace), 6, 33, 0xFFFFD27A, false);
    }
}

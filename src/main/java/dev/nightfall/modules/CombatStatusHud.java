package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import dev.nightfall.modules.KillAura;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

public class CombatStatusHud extends HudModule {
    public CombatStatusHud() { super("CombatStatus", "Ziel, Distanz, Cooldown und Crit-Status."); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw / 2 + 12, sh / 2 + 12}; }

    @Override
    protected void draw(DrawContext ctx) {
        TrapCrit trapCrit = ModuleManager.get(TrapCrit.class);
        KillAura killAura = ModuleManager.get(KillAura.class);
        LivingEntity target = trapCrit.target != null ? trapCrit.target : killAura.target;
        double mace = ModuleManager.get(MaceAssist.class).estimate;
        String idle = trapCrit.isEnabled() ? "Kein TrapCrit-Ziel"
                : killAura.isEnabled() ? "Kein KillAura-Ziel" : "Kein Ziel";
        String name = target == null ? idle : target.getName().getString();
        String detail = target == null || mc.player == null ? ""
                : String.format(java.util.Locale.ROOT, "%.1f m  %s  %s", mc.player.distanceTo(target),
                trapCrit.target == target ? (trapCrit.trapped ? "TRAP" : "frei") : "AURA",
                trapCrit.target == target && trapCrit.critReady ? "CRIT" : "-");
        w = Math.max(126, Math.min(180, Math.max(mc.textRenderer.getWidth(name) + 12, mc.textRenderer.getWidth(detail) + 12)));
        h = target == null ? (mace > 0 ? 30 : 18) : (mace > 0 ? 46 : 34);
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, name, 6, 3, target == null ? Theme.TEXT_DIM : Theme.TEXT, false);
        if (target == null) {
            if (mace > 0) ctx.drawText(mc.textRenderer, String.format(java.util.Locale.ROOT, "Mace ~%.0f Schaden", mace), 6, 15, 0xFFFFD27A, false);
            return;
        }
        ctx.drawText(mc.textRenderer, detail, 6, 13, Theme.TEXT_DIM, false);
        float cooldown = trapCrit.target == target ? trapCrit.cooldownProgress : mc.player.getAttackCooldownProgress(0.5f);
        int bw = (int) ((w - 12) * Math.min(1f, cooldown));
        ctx.fill(6, 25, w - 6, 29, 0xFF2A2A34);
        ctx.fill(6, 25, 6 + bw, 29, Theme.ACCENT);
        if (mace > 0) ctx.drawText(mc.textRenderer, String.format(java.util.Locale.ROOT, "Mace ~%.0f Schaden", mace), 6, 33, 0xFFFFD27A, false);
    }
}

package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class ModuleList extends HudModule {
    private final List<Module> sorted = new ArrayList<>();

    public ModuleList() { super("ModuleList", "Liste aktiver Module."); setEnabled(true); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw - w - 4, 4}; }

    @Override
    protected void draw(DrawContext ctx) {
        sorted.clear();
        for (Module m : ModuleManager.all())
            if (m.isEnabled() && m.getCategory() != Category.HUD) sorted.add(m);
        sorted.sort((a, b) -> mc.textRenderer.getWidth(b.getName()) - mc.textRenderer.getWidth(a.getName()));
        int maxW = 40;
        for (Module m : sorted) maxW = Math.max(maxW, mc.textRenderer.getWidth(m.getName()) + 8);
        w = maxW; h = Math.max(12, sorted.size() * 11);
        int y = 0;
        for (Module m : sorted) {
            int mw = mc.textRenderer.getWidth(m.getName()) + 8;
            ctx.fill(w - mw, y, w, y + 11, 0xA0101014);
            ctx.fill(w - 2, y, w, y + 11, Theme.ACCENT);
            ctx.drawText(mc.textRenderer, m.getName(), w - mw + 3, y + 2, Theme.TEXT, true);
            y += 11;
        }
    }
}

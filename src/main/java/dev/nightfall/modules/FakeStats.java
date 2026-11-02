package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

/** Local cosmetic demo overlay; never changes server/player-list data. */
public final class FakeStats extends HudModule {
    public final StringSetting displayName = add(new StringSetting("Anzeigename", "Nightfall", 32));
    public final NumberSetting kills = add(new NumberSetting("Fake Kills", 42, 0, 999999, 1));
    public final NumberSetting deaths = add(new NumberSetting("Fake Tode", 3, 0, 999999, 1));
    public final NumberSetting wins = add(new NumberSetting("Fake Siege", 17, 0, 999999, 1));

    public FakeStats() { super("FakeStats", "Nur lokales, deutlich markiertes Fake-Stats-HUD; keine Serverdaten werden veraendert."); }
    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw - 148, sh / 2 - 30}; }

    @Override protected void draw(DrawContext ctx) {
        String[] lines = {
                "FAKE STATS · LOKAL",
                displayName.get(),
                "Kills  " + kills.getInt(),
                "Tode   " + deaths.getInt(),
                "Siege  " + wins.getInt()
        };
        int mw = 0;
        for (String line : lines) mw = Math.max(mw, mc.textRenderer.getWidth(line));
        w = mw + 14;
        h = lines.length * 12 + 8;
        ctx.fill(0, 0, w, h, 0xD0101018);
        ctx.fill(0, 0, 3, h, 0xFFFFB454);
        ctx.drawText(mc.textRenderer, lines[0], 7, 4, 0xFFFFB454, true);
        for (int i = 1; i < lines.length; i++)
            ctx.drawText(mc.textRenderer, lines[i], 7, 4 + i * 12, i == 1 ? Theme.TEXT : Theme.TEXT_DIM, false);
    }
}

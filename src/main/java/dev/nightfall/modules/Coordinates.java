package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;

public class Coordinates extends HudModule {
    private final BoolSetting dimension = add(new BoolSetting("Nether/Overworld Umrechnung", true));

    public Coordinates() { super("Coordinates", "Zeigt XYZ."); setEnabled(true); }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{4, sh - 16}; }

    @Override
    protected void draw(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;
        int x = mc.player.getBlockX(), y = mc.player.getBlockY(), z = mc.player.getBlockZ();
        String line = "XYZ " + x + " " + y + " " + z;
        String key = mc.world.getRegistryKey().getValue().getPath();
        if (dimension.get()) {
            if (key.equals("the_nether")) line += "  (OW " + (x * 8) + " " + (z * 8) + ")";
            else if (key.equals("overworld")) line += "  (N " + (x / 8) + " " + (z / 8) + ")";
        }
        w = mc.textRenderer.getWidth(line) + 10; h = 12;
        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, line, 6, 2, Theme.TEXT, true);
    }
}

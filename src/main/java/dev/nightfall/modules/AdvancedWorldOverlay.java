package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact world-information panel. It keeps the World category while gaining
 * the common HUD editor positioning and scaling controls.
 */
public final class AdvancedWorldOverlay extends HudModule {
    private final BoolSetting coordinates = add(new BoolSetting("Koordinaten", true));
    private final BoolSetting chunk = add(new BoolSetting("Chunk und Region", true));
    private final BoolSetting dimension = add(new BoolSetting("Dimension", true));
    private final BoolSetting light = add(new BoolSetting("Lichtlevel", true));
    private final BoolSetting facing = add(new BoolSetting("Blickrichtung", true));

    public AdvancedWorldOverlay() {
        super("AdvancedWorldOverlay", "Zeigt Welt-, Chunk- und Positionsdaten als verschiebbares HUD-Panel.", Category.WORLD);
    }

    @Override
    protected int[] defaultPos(int sw, int sh) {
        // Leave a small gap under the Watermark's default top-left position.
        return new int[]{8, 22};
    }

    @Override
    protected void draw(DrawContext ctx) {
        if (mc.player == null || mc.world == null) {
            w = 0;
            h = 0;
            return;
        }

        BlockPos pos = mc.player.getBlockPos();
        ChunkPos cp = new ChunkPos(pos);
        List<String> rows = new ArrayList<>(5);

        if (coordinates.get()) {
            rows.add("XYZ  " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ());
        }
        if (chunk.get()) {
            rows.add("Chunk  " + cp.x + " / " + cp.z + "   Region  " + (cp.x >> 5) + " / " + (cp.z >> 5));
        }
        if (dimension.get()) {
            rows.add("Dimension  " + mc.world.getRegistryKey().getValue());
        }
        if (light.get()) {
            rows.add("Lichtlevel  " + mc.world.getLightLevel(pos));
        }
        if (facing.get()) {
            rows.add("Richtung  " + mc.player.getHorizontalFacing().asString().toUpperCase(java.util.Locale.ROOT));
        }

        int contentWidth = 0;
        for (String row : rows) contentWidth = Math.max(contentWidth, mc.textRenderer.getWidth(row));
        w = Math.max(142, contentWidth + 18);
        h = 20 + rows.size() * 12;

        ctx.fill(0, 0, w, h, Theme.HUD_PANEL);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        ctx.fill(2, 0, w, 1, Theme.withAlpha(Theme.ACCENT, 160));
        ctx.drawText(mc.textRenderer, "WORLD  /  INSPECTOR", 9, 5, Theme.ACCENT, true);

        int y = 17;
        for (String row : rows) {
            ctx.drawText(mc.textRenderer, row, 9, y, Theme.TEXT, false);
            y += 12;
        }
    }
}

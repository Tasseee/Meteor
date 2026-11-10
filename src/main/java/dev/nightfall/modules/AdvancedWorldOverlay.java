package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Lightweight client-side world information overlay.
 * Displays only information already available to the local client.
 */
public final class AdvancedWorldOverlay extends Module {
    private final BoolSetting coordinates = add(new BoolSetting("Koordinaten", true));
    private final BoolSetting chunk = add(new BoolSetting("Chunk und Region", true));
    private final BoolSetting dimension = add(new BoolSetting("Dimension", true));
    private final BoolSetting light = add(new BoolSetting("Lichtlevel", true));
    private final BoolSetting facing = add(new BoolSetting("Blickrichtung", true));

    public AdvancedWorldOverlay() {
        super("AdvancedWorldOverlay", "Zeigt erweiterte Welt-, Chunk- und Positionsdaten an.", Category.WORLD);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null) return;

        BlockPos pos = mc.player.getBlockPos();
        ChunkPos cp = new ChunkPos(pos);
        int x = 8;
        int y = 8;
        int color = 0xFFE8EAF2;

        ctx.drawText(mc.textRenderer, "Nightfall World Overlay", x, y, 0xFF9B7BFF, true);
        y += 12;

        if (coordinates.get()) {
            ctx.drawText(mc.textRenderer,
                    "XYZ: " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ(),
                    x, y, color, true);
            y += 11;
        }
        if (chunk.get()) {
            ctx.drawText(mc.textRenderer,
                    "Chunk: " + cp.x + " / " + cp.z + "  |  Region: " + (cp.x >> 5) + " / " + (cp.z >> 5),
                    x, y, color, true);
            y += 11;
        }
        if (dimension.get()) {
            ctx.drawText(mc.textRenderer,
                    "Dimension: " + mc.world.getRegistryKey().getValue(),
                    x, y, color, true);
            y += 11;
        }
        if (light.get()) {
            ctx.drawText(mc.textRenderer,
                    "Lichtlevel: " + mc.world.getLightLevel(pos),
                    x, y, color, true);
            y += 11;
        }
        if (facing.get()) {
            ctx.drawText(mc.textRenderer,
                    "Richtung: " + mc.player.getHorizontalFacing().asString().toUpperCase(),
                    x, y, color, true);
        }
    }
}

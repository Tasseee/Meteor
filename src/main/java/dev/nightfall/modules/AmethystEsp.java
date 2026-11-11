package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Highlights amethyst geode blocks in already-loaded chunks only. */
public final class AmethystEsp extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 48, 8, 96, 4));
    private final NumberSetting maxBlocks = add(new NumberSetting("Max Markierungen", 500, 20, 1000, 20));
    private final BoolSetting budding = add(new BoolSetting("Budding Amethyst", true));
    private final BoolSetting clusters = add(new BoolSetting("Amethyst-Cluster", true));
    private final BoolSetting buds = add(new BoolSetting("Amethyst-Buds", true));
    private final List<BlockPos> found = new ArrayList<>();
    private int timer;

    public AmethystEsp() {
        super("AmethystESP", "Markiert Amethyst-Geoden-Bloecke in bereits geladenen Chunks.", Category.VISUALS);
    }

    private boolean selected(Block block) {
        return (budding.get() && block == Blocks.BUDDING_AMETHYST)
                || (clusters.get() && block == Blocks.AMETHYST_CLUSTER)
                || (buds.get() && (block == Blocks.LARGE_AMETHYST_BUD
                    || block == Blocks.MEDIUM_AMETHYST_BUD || block == Blocks.SMALL_AMETHYST_BUD));
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || timer-- > 0) return;
        timer = 10;
        found.clear();

        BlockPos player = mc.player.getBlockPos();
        int r = range.getInt();
        int cap = maxBlocks.getInt();
        int minX = player.getX() - r, maxX = player.getX() + r;
        int minZ = player.getZ() - r, maxZ = player.getZ() + r;
        int minY = Math.max(mc.world.getBottomY(), player.getY() - r);
        int maxY = Math.min(mc.world.getTopYInclusive(), player.getY() + r);
        double r2 = (double) r * r;

        int minChunkX = minX >> 4, maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4, maxChunkZ = maxZ >> 4;
        for (int cx = minChunkX; cx <= maxChunkX && found.size() < cap; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ && found.size() < cap; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                int baseX = cx << 4, baseZ = cz << 4;
                int x0 = Math.max(minX, baseX), x1 = Math.min(maxX, baseX + 15);
                int z0 = Math.max(minZ, baseZ), z1 = Math.min(maxZ, baseZ + 15);

                ChunkSection[] sections = chunk.getSectionArray();
                for (int sectionIndex = 0; sectionIndex < sections.length && found.size() < cap; sectionIndex++) {
                    ChunkSection section = sections[sectionIndex];
                    if (section == null || section.isEmpty()) continue;
                    int sectionY = mc.world.getBottomY() + (sectionIndex << 4);
                    int y0 = Math.max(0, minY - sectionY);
                    int y1 = Math.min(15, maxY - sectionY);
                    if (y0 > y1) continue;
                    for (int x = x0; x <= x1 && found.size() < cap; x++) {
                        for (int z = z0; z <= z1 && found.size() < cap; z++) {
                            for (int y = y0; y <= y1 && found.size() < cap; y++) {
                                Block block = section.getBlockState(x & 15, y, z & 15).getBlock();
                                if (!selected(block)) continue;
                                BlockPos pos = new BlockPos(x, sectionY + y, z);
                                if (pos.getSquaredDistance(player) <= r2) found.add(pos.toImmutable());
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!Projection.valid()) return;
        for (BlockPos pos : found) {
            Draw.box(ctx, new Box(pos), 0xDDAA55FF, 2, true);
        }
    }

    @Override protected void onDisable() { found.clear(); }
    @Override public void onWorldLeave() { found.clear(); timer = 0; }
}

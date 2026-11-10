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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

/** Selectable block highlighter. Only scans already-loaded blocks around the player. */
public final class BlockESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 24, 8, 64, 4));
    private final NumberSetting maxBlocks = add(new NumberSetting("Max Markierungen", 180, 20, 600, 20));
    private final BoolSetting diamonds = add(new BoolSetting("Diamanterz", true));
    private final BoolSetting ancient = add(new BoolSetting("Antiker Schrott", true));
    private final BoolSetting chests = add(new BoolSetting("Kisten", true));
    private final BoolSetting spawners = add(new BoolSetting("Spawner", true));
    private final BoolSetting obsidian = add(new BoolSetting("Obsidian", false));
    private final BoolSetting gold = add(new BoolSetting("Golderz", false));
    private final BoolSetting emerald = add(new BoolSetting("Smaragderz", false));
    private final List<BlockPos> found = new ArrayList<>();
    private int timer;
    public BlockESP() { super("BlockESP", "Waehlt Blocktypen einzeln aus und markiert sie im geladenen Gebiet.", Category.VISUALS); }
    private boolean selected(Block b) {
        return (diamonds.get() && (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE))
            || (ancient.get() && b == Blocks.ANCIENT_DEBRIS)
            || (chests.get() && (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL))
            || (spawners.get() && b == Blocks.SPAWNER)
            || (obsidian.get() && b == Blocks.OBSIDIAN)
            || (gold.get() && (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE))
            || (emerald.get() && (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE));
    }
    @Override public void onTick() {
        if (mc.player == null || mc.world == null || timer-- > 0) return;
        timer = 12; found.clear();
        BlockPos p = mc.player.getBlockPos(); int r = range.getInt(); int cap = maxBlocks.getInt();
        int minY = Math.max(mc.world.getBottomY(), p.getY()-r), maxY = Math.min(mc.world.getTopYInclusive(), p.getY()+r);
        int minX=p.getX()-r,maxX=p.getX()+r,minZ=p.getZ()-r,maxZ=p.getZ()+r;
        for(int cx=(minX>>4);cx<=(maxX>>4)&&found.size()<cap;cx++) for(int cz=(minZ>>4);cz<=(maxZ>>4)&&found.size()<cap;cz++){
            if(mc.world.getChunkManager().getWorldChunk(cx,cz)==null) continue;
            int sx=Math.max(minX,cx<<4),ex=Math.min(maxX,(cx<<4)+15),sz=Math.max(minZ,cz<<4),ez=Math.min(maxZ,(cz<<4)+15);
            for(int x=sx;x<=ex&&found.size()<cap;x++) for(int z=sz;z<=ez&&found.size()<cap;z++) for(int y=minY;y<=maxY&&found.size()<cap;y++){
                BlockPos bp=new BlockPos(x,y,z);
                if(selected(mc.world.getBlockState(bp).getBlock())) found.add(bp.toImmutable());
            }
        }
    }
    @Override public void renderHud(DrawContext ctx) {
        if(!Projection.valid()) return;
        for(BlockPos p:found) Draw.box(ctx,new Box(p),0xDDAA65FF,1,true);
    }
    @Override protected void onDisable(){found.clear();}
    @Override public void onWorldLeave(){found.clear();timer=0;}
}

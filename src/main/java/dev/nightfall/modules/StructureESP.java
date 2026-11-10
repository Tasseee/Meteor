package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import java.util.ArrayList;
import java.util.List;

/** Highlights recognizable structure markers in loaded chunks without forcing chunk loads. */
public final class StructureESP extends Module {
    private final NumberSetting range=add(new NumberSetting("Reichweite",48,8,96,8));
    private final BoolSetting spawners=add(new BoolSetting("Spawner",true));
    private final BoolSetting portals=add(new BoolSetting("Portalrahmen",true));
    private final BoolSetting trial=add(new BoolSetting("Trial-Spawner",true));
    private final BoolSetting chests=add(new BoolSetting("Kisten",true));
    private final BoolSetting suspicious=add(new BoolSetting("Verdächtige Bloecke",true));
    private final List<BlockPos> positions=new ArrayList<>();
    private int timer;
    public StructureESP(){super("StructureESP","Markiert Struktur-Hinweise in bereits geladenen Chunks.",Category.VISUALS);}
    private boolean match(net.minecraft.block.Block b){
        return (spawners.get()&&b==Blocks.SPAWNER)||(portals.get()&&b==Blocks.END_PORTAL_FRAME)
            ||(trial.get()&&(b==Blocks.TRIAL_SPAWNER||b==Blocks.VAULT))
            ||(chests.get()&&(b==Blocks.CHEST||b==Blocks.TRAPPED_CHEST))
            ||(suspicious.get()&&(b==Blocks.SUSPICIOUS_SAND||b==Blocks.SUSPICIOUS_GRAVEL));
    }
    @Override public void onTick(){
        if(mc.player==null||mc.world==null||timer-->0)return;
        timer=30;positions.clear();BlockPos p=mc.player.getBlockPos();int r=range.getInt(),cap=160;
        for(int cx=(p.getX()-r)>>4;cx<=((p.getX()+r)>>4)&&positions.size()<cap;cx++)
            for(int cz=(p.getZ()-r)>>4;cz<=((p.getZ()+r)>>4)&&positions.size()<cap;cz++){
                if(mc.world.getChunkManager().getWorldChunk(cx,cz)==null)continue;
                int x0=Math.max(p.getX()-r,cx<<4),x1=Math.min(p.getX()+r,(cx<<4)+15);
                int z0=Math.max(p.getZ()-r,cz<<4),z1=Math.min(p.getZ()+r,(cz<<4)+15);
                for(int x=x0;x<=x1&&positions.size()<cap;x++)for(int z=z0;z<=z1&&positions.size()<cap;z++)
                    for(int y=Math.max(mc.world.getBottomY(),p.getY()-r);y<=Math.min(mc.world.getTopYInclusive(),p.getY()+r)&&positions.size()<cap;y++){
                        BlockPos bp=new BlockPos(x,y,z);if(match(mc.world.getBlockState(bp).getBlock()))positions.add(bp.toImmutable());
                    }
            }
    }
    @Override public void renderHud(DrawContext ctx){if(!Projection.valid())return;for(BlockPos p:positions)Draw.box(ctx,new Box(p),0xDDF4B942,1,true);}
    @Override protected void onDisable(){positions.clear();}
    @Override public void onWorldLeave(){positions.clear();timer=0;}
}

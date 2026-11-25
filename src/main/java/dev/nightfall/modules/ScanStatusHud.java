package dev.nightfall.modules;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;

/** Status panel for the background scan. */
public final class ScanStatusHud extends HudModule {
   private static final int AMBER=-14249;
   public ScanStatusHud(){super("ScanStatus","Status von BackgroundFreecam und ChunkMemory.");setEnabled(true);}
   @Override protected int[] defaultPos(int sw,int sh){return new int[]{4,60};}
   @Override protected void draw(class_332 ctx){
      boolean memory=ModuleManager.get(ChunkMemory.class).isEnabled(),camera=ModuleManager.get(BackgroundFreecam.class).isEnabled();
      if(!memory&&!camera){w=0;h=0;return;}
      ChunkMemory.ScanStatus ms=ChunkMemory.scanStatus();BackgroundFreecam.State fs=BackgroundFreecam.currentState();
      String state;int color;
      if(!memory){state="ChunkMemory aus";color=AMBER;}
      else if(fs==BackgroundFreecam.State.PAUSED||ms==ChunkMemory.ScanStatus.PAUSED){state="Pausiert";color=AMBER;}
      else if(ms==ChunkMemory.ScanStatus.WAITING||fs==BackgroundFreecam.State.WAITING){state="Wartet auf Chunks";color=AMBER;}
      else if(ms==ChunkMemory.ScanStatus.ACTIVE){state="Aktiv";color=Theme.ACCENT;}else{state="Aktuell";color=Theme.TEXT;}
      w=176;h=54;ctx.method_25294(0,0,w,h,Theme.HUD_PANEL);ctx.method_25294(0,0,2,h,Theme.ACCENT);
      ctx.method_51433(mc.field_1772,"Hintergrund-Scan  "+state,6,2,color,false);
      String pos=camera?"Pos "+(int)BackgroundFreecam.centerX()+", "+(int)BackgroundFreecam.centerY()+", "+(int)BackgroundFreecam.centerZ()+"  R"+BackgroundFreecam.scanRadiusChunks():"Zentrum: Spieler (passiv)";
      ctx.method_51433(mc.field_1772,pos,6,12,Theme.TEXT_DIM,false);
      ctx.method_51433(mc.field_1772,"Geladen "+ChunkMemory.loadedCount()+"  Gedaechtnis "+ChunkMemory.rememberedCount()+"/"+ChunkMemory.memoryLimit(),6,22,Theme.TEXT,false);
      ctx.method_51433(mc.field_1772,"Geprueft "+compact(ChunkMemory.checkedTotal())+"  Neu "+compact(ChunkMemory.newTotal())+"  Akt. "+compact(ChunkMemory.refreshedTotal()),6,32,Theme.TEXT,false);
      String mode=camera?BackgroundFreecam.modeName()+"  "+String.format("%.1f b/t",BackgroundFreecam.speedBlocksPerTick()):"passiv";
      ctx.method_51433(mc.field_1772,mode+"  "+ChunkMemory.surveyRate()+"/s  "+ChunkMemory.lastTickMicros()+"us",6,42,Theme.TEXT_DIM,false);
   }
   private static String compact(long n){return n>=10000?n/1000+"k":Long.toString(n);}
}
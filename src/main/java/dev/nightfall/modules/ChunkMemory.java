package dev.nightfall.modules;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkStore;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
import net.minecraft.class_2826;
import net.minecraft.class_638;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Shared, bounded memory for chunks already received by the client. No chunk requests or force-loads. */
public final class ChunkMemory extends Module {
   public enum ChunkState{UNAVAILABLE,LOADED,STALE,REMEMBERED}
   public enum ScanStatus{OFF,ACTIVE,IDLE,WAITING,PAUSED}
   public interface RememberedVisitor{void visit(int cx,int cz,class_2818 chunk,long ageTicks);}
   public record CachedChunk(int cx,int cz,class_2818 chunk,long capturedAtTick,long lastAccessTick){}
   private static final int PRIORITY_NEAREST=0,PRIORITY_NEW_FIRST=1,PRIORITY_STALE_FIRST=2;
   private static final int VALIDATE_PER_TICK=24,MAINTENANCE_INTERVAL=100;
   private static ChunkMemory instance;
   private final NumberSetting limit=add(new NumberSetting("Max. Gedaechtnis-Chunks",256,0,2048,32));
   private final NumberSetting budget=add(new NumberSetting("Scan-Budget (Chunks/Tick)",32,4,256,4));
   private final NumberSetting ttl=add(new NumberSetting("Aktualitaet (Sek.)",30,5,300,5));
   private final NumberSetting forget=add(new NumberSetting("Vergessen nach (Min.)",10,0,60,1));
   private final NumberSetting passiveRadius=add(new NumberSetting("Passiver Radius (Chunks)",10,2,32,1));
   private final BoolSetting debug=add(new BoolSetting("Debug-Log",false));
   private final ChunkStore<class_2818> store=new ChunkStore<>();
   private final ChunkStore.Live<class_2818> liveLookup=this::liveChunk;
   private class_638 world;private long tick,worldEpoch;
   private boolean publishedPending,pubPaused,driven;private int pubPriority,pubRadius,curCenterCx,curCenterCz,curRadius;
   private double pubX,pubZ;private boolean curPaused;
   private int[] disc=new int[0];private int discSize,discRadius=-1,cursor,pass;
   private int anchorCx=Integer.MIN_VALUE,anchorCz=Integer.MIN_VALUE,lastRadius=-1;
   private int unavailableThisSweep,lastSweepUnavailable;private long lastActiveTick=-1000L;
   private long checkedTotal,newTotal,refreshedTotal,surveyedTotal,skippedFreshTotal,sweepsDone;
   private int surveyedLastTick,surveyedRateWindow,ratePerSec;private long lastTickNanos;
   private ScanStatus status=ScanStatus.OFF;
   public ChunkMemory(){super("ChunkMemory","Session-Cache fuer bereits empfangene Chunks; begrenztes Scan-Budget, keine Chunk-Anfragen.",Category.WORLD);instance=this;}
   @Override protected void onEnable(){resetAll();}
   @Override protected void onDisable(){resetAll();status=ScanStatus.OFF;}
   @Override public void onWorldLeave(){resetAll();}
   private void resetAll(){
      store.clear();world=null;worldEpoch++;cursor=pass=0;anchorCx=anchorCz=Integer.MIN_VALUE;lastRadius=-1;
      unavailableThisSweep=lastSweepUnavailable=0;lastActiveTick=-1000L;
      checkedTotal=newTotal=refreshedTotal=surveyedTotal=skippedFreshTotal=sweepsDone=0L;
      surveyedLastTick=surveyedRateWindow=ratePerSec=0;publishedPending=driven=false;status=ScanStatus.IDLE;
   }
   @Override public void onTick(){
      class_638 w=mc.field_1687;if(w==null||mc.field_1724==null)return;long started=System.nanoTime();
      if(w!=world){resetAll();world=w;}tick++;store.setLimit(limit.getInt());
      driven=publishedPending;publishedPending=false;
      if(driven){curCenterCx=(int)Math.floor(pubX)>>4;curCenterCz=(int)Math.floor(pubZ)>>4;curRadius=pubRadius;curPaused=pubPaused;}
      else{curCenterCx=(int)Math.floor(mc.field_1724.method_23317())>>4;curCenterCz=(int)Math.floor(mc.field_1724.method_23321())>>4;curRadius=passiveRadius.getInt();curPaused=false;}
      store.validate(liveLookup,VALIDATE_PER_TICK,tick);
      if(tick%MAINTENANCE_INTERVAL==0L){int m=forget.getInt();if(m>0)store.expire(tick,m*60L*20L);if(debug.get())logStats();}
      surveyedLastTick=0;
      if(curPaused)status=ScanStatus.PAUSED;
      else{ sweep(w,driven?pubPriority:PRIORITY_NEAREST);surveyedRateWindow+=surveyedLastTick;
         if(surveyedLastTick>0)lastActiveTick=tick;
         if(tick-lastActiveTick<=10L)status=ScanStatus.ACTIVE;
         else if(lastSweepUnavailable>0&&store.loadedCount()==0)status=ScanStatus.WAITING;else status=ScanStatus.IDLE;
      }
      if(tick%20L==0L){ratePerSec=surveyedRateWindow;surveyedRateWindow=0;}
      lastTickNanos=System.nanoTime()-started;
   }
   private class_2818 liveChunk(int x,int z){class_638 w=world;return w==null?null:w.method_2935().method_21730(x,z);}
   private void ensureDisc(int r){
      if(r==discRadius)return;int[] temp=new int[(2*r+1)*(2*r+1)];long[] keys=new long[temp.length];int n=0;
      for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++){int d=dx*dx+dz*dz;if(d<=r*r)keys[n++]=((long)d<<32)|((long)((dx+64)&255)<<8)|((dz+64)&255);}
      java.util.Arrays.sort(keys,0,n);for(int i=0;i<n;i++){int dx=(int)(keys[i]>>8&255)-64,dz=(int)(keys[i]&255)-64;temp[i]=(dx&65535)|(dz<<16);}
      disc=temp;discSize=n;discRadius=r;cursor=pass=0;
   }
   /** Keep a stable anchor through a full pass; moving between chunks cannot starve the outer ring. */
   private void sweep(class_638 w,int priority){
      int r=Math.max(1,Math.min(32,curRadius));ensureDisc(r);
      if(anchorCx==Integer.MIN_VALUE||anchorCz==Integer.MIN_VALUE||r!=lastRadius){anchorCx=curCenterCx;anchorCz=curCenterCz;lastRadius=r;cursor=pass=0;}
      int passes=priority==PRIORITY_NEAREST?1:2,b=budget.getInt(),maxChecks=Math.min(1024,b*8+64);
      long ttlTicks=ttl.getInt()*20L;int checks=0,surveyed=0;
      while(checks<maxChecks&&surveyed<b){
         if(cursor>=discSize){cursor=0;pass++;if(pass>=passes){pass=0;sweepsDone++;lastSweepUnavailable=unavailableThisSweep;unavailableThisSweep=0;anchorCx=curCenterCx;anchorCz=curCenterCz;break;}}
         int p=disc[cursor++],cx=anchorCx+(short)(p&65535),cz=anchorCz+(p>>16);checks++;checkedTotal++;
         long k=ChunkStore.key(cx,cz);class_2818 live=w.method_2935().method_21730(cx,cz);ChunkStore.Entry<class_2818> e=store.loadedEntry(k);
         if(live==null){if(e!=null)store.unload(e,tick);unavailableThisSweep++;continue;}
         boolean freshNew=e==null;if(!freshNew&&e.chunk!=live)e=store.adopt(cx,cz,live);
         if(!freshNew&&e.surveyTick>=0L&&tick-e.surveyTick<ttlTicks){skippedFreshTotal++;continue;}
         boolean accept=switch(priority){case PRIORITY_NEW_FIRST->pass==0?freshNew:!freshNew;case PRIORITY_STALE_FIRST->pass==0?!freshNew:freshNew;default->true;};
         if(!accept)continue;if(freshNew){e=store.adopt(cx,cz,live);newTotal++;}else refreshedTotal++;
         survey(e,live);surveyed++;
      }surveyedLastTick=surveyed;
   }
   private void survey(ChunkStore.Entry<class_2818> e,class_2818 c){long m=occupiedMask(c);if(m!=e.mask){e.mask=m;e.version++;}e.surveyTick=tick;e.surveys++;surveyedTotal++;}
   private void logStats(){
      Nightfall.LOGGER.info("[ChunkMemory] geladen={} gemerkt={}/{} geprueft={} neu={} aktualisiert={} frisch-uebersprungen={} verdraengt={} verworfen={} ungueltig={} sweeps={} tick={}us",
         store.loadedCount(),store.rememberedCount(),store.limit(),checkedTotal,newTotal,refreshedTotal,skippedFreshTotal,
         store.evicted,store.unloaded,store.invalidated,sweepsDone,lastTickNanos/1000L);
   }
   static void publishScan(double x,double z,int radius,int priority,boolean paused){
      ChunkMemory m=instance;if(m==null)return;m.pubX=x;m.pubZ=z;m.pubRadius=radius;m.pubPriority=priority;m.pubPaused=paused;m.publishedPending=true;
   }
   public static boolean isRunning(){return instance!=null&&instance.isEnabled()&&instance.world!=null;}
   public static long worldEpoch(){return instance==null?0L:instance.worldEpoch;}
   /** Live client chunk lookup; no load request. */
   public static class_2818 loaded(int x,int z){
      class_638 w=mc.field_1687;class_2818 c=w==null?null:w.method_2935().method_21730(x,z);
      ChunkMemory m=instance;if(c!=null&&m!=null&&m.isEnabled()&&m.world==w)m.store.adopt(x,z,c);return c;
   }
   public static class_2818 chunk(int x,int z,boolean remembered){
      class_2818 c=loaded(x,z);if(c!=null||!remembered)return c;
      ChunkMemory m=instance;if(m==null||!m.isEnabled()||m.world==null||m.world!=mc.field_1687)return null;
      ChunkStore.Entry<class_2818> e=m.store.rememberedEntry(ChunkStore.key(x,z),true);return e==null?null:e.chunk;
   }
   public static ChunkState stateOf(int x,int z){
      class_2818 c=loaded(x,z);ChunkMemory m=instance;
      if(c!=null){if(m!=null&&m.isEnabled()&&m.world!=null){ChunkStore.Entry<class_2818> e=m.store.loadedEntry(ChunkStore.key(x,z));
         if(e!=null&&e.surveyTick>=0L&&m.tick-e.surveyTick<m.ttl.getInt()*20L&&e.chunk==c)return ChunkState.LOADED;}return ChunkState.STALE;}
      return m!=null&&m.isEnabled()&&m.world!=null&&m.store.rememberedEntry(ChunkStore.key(x,z),false)!=null?ChunkState.REMEMBERED:ChunkState.UNAVAILABLE;
   }
   public static int version(int x,int z){
      ChunkMemory m=instance;if(m==null||!m.isEnabled()||m.world==null)return 0;long k=ChunkStore.key(x,z);
      ChunkStore.Entry<class_2818> e=m.store.loadedEntry(k);if(e==null)e=m.store.rememberedEntry(k,false);return e==null?0:e.version;
   }
   public static long ageTicks(int x,int z){
      ChunkMemory m=instance;if(m==null||m.world==null)return Long.MAX_VALUE;ChunkStore.Entry<class_2818> e=m.store.loadedEntry(ChunkStore.key(x,z));
      return e==null||e.surveyTick<0L?Long.MAX_VALUE:m.tick-e.surveyTick;
   }
   public static int interest(int x,int z,int minY,int maxY){
      ChunkMemory m=instance;if(m==null||m.world==null)return -1;ChunkStore.Entry<class_2818> e=m.store.loadedEntry(ChunkStore.key(x,z));
      if(e==null||e.surveyTick<0L)return -1;int b=m.world.method_31607(),lo=Math.max(0,(minY-b)>>4),hi=Math.min(63,(maxY-b)>>4);if(hi<lo)return 0;
      long bits=(hi>=63?-1L:(1L<<(hi+1))-1L)&-(1L<<lo);return Long.bitCount(e.mask&bits);
   }
   public static void forEachRemembered(RememberedVisitor v){
      ChunkMemory m=instance;if(m==null||!m.isEnabled()||m.world==null)return;long now=m.tick;
      m.store.forEachRemembered(e->{if(e.chunk!=null)v.visit(e.cx,e.cz,e.chunk,now-e.unloadTick);});
   }
   public static long occupiedMask(class_2818 c){
      if(c==null)return 0L;class_2826[] s=c.method_12006();int n=Math.min(s.length,64);long m=0L;
      for(int i=0;i<n;i++)if(s[i]!=null&&!s[i].method_38292())m|=1L<<i;return m;
   }
   public static boolean sectionOccupied(long mask,int bottomY,int y){int i=(y-bottomY)>>4;return i>=0&&(i>=64||((mask>>>i)&1L)!=0L);}
   public static boolean anyOccupied(long mask,int bottomY,int minY,int maxY){
      int lo=Math.max(0,(minY-bottomY)>>4),hi=(maxY-bottomY)>>4;if(hi<lo)return false;if(hi>=63)return true;
      long bits=((1L<<(hi+1))-1L)&-(1L<<lo);return(mask&bits)!=0L;
   }

   // Preserve the current Nightfall API for BaseFinder, StructureESP, BlockEntityESP, etc.
   public static class_243 scanPosition(){
      if(BackgroundFreecam.isActive()){class_243 p=BackgroundFreecam.cameraPosition();if(p!=null)return p;}
      if(FreecamController.isActive())return FreecamController.cameraPosition();return mc.field_1724==null?null:mc.field_1724.method_33571();
   }
   public static String scanSource(){if(BackgroundFreecam.isActive())return BackgroundFreecam.isPaused()?"BackgroundFreecam (pausiert)":"BackgroundFreecam";return FreecamController.isActive()?"Manuelle Freecam":"Spieler";}
   public static class_2818 getCachedChunk(int x,int z){
      ChunkMemory m=instance;if(m==null||!m.isEnabled()||m.world==null||m.world!=mc.field_1687)return null;
      ChunkStore.Entry<class_2818> e=m.store.rememberedEntry(ChunkStore.key(x,z),true);return e==null?null:e.chunk;
   }
   public static Collection<CachedChunk> cachedChunks(){
      List<CachedChunk> out=new ArrayList<>();forEachRemembered((x,z,c,age)->{long n=instance==null?0:instance.tick;out.add(new CachedChunk(x,z,c,Math.max(0,n-age),n));});
      return java.util.Collections.unmodifiableList(out);
   }
   public static int cachedChunkCount(){return rememberedCount();}
   public static long cachedChunkAgeTicks(int x,int z){
      ChunkMemory m=instance;if(m==null||m.world==null)return -1L;ChunkStore.Entry<class_2818> e=m.store.rememberedEntry(ChunkStore.key(x,z),false);
      return e==null?-1L:Math.max(0L,m.tick-e.unloadTick);
   }
   public static class_2818 getAvailableChunk(int x,int z){return chunk(x,z,true);}
   public static class_2680 getBlockState(int x,int y,int z){return getBlockState(getAvailableChunk(x>>4,z>>4),x,y,z);}
   public static class_2680 getBlockState(class_2818 c,int x,int y,int z){
      class_638 w=mc.field_1687;if(c==null||w==null||y<w.method_31607()||y>=w.method_31600())return null;
      class_2826[] s=c.method_12006();int i=(y>>4)-(w.method_31607()>>4);if(i<0||i>=s.length)return null;
      return s[i]==null?null:s[i].method_12254(x&15,y&15,z&15);
   }
   public static void clearSessionCache(){if(instance!=null)instance.resetAll();}
   public static ScanStatus scanStatus(){return instance==null||!instance.isEnabled()?ScanStatus.OFF:instance.status;}
   public static int loadedCount(){return instance==null?0:instance.store.loadedCount();}
   public static int rememberedCount(){return instance==null?0:instance.store.rememberedCount();}
   public static int memoryLimit(){return instance==null?0:instance.store.limit();}
   public static long checkedTotal(){return instance==null?0L:instance.checkedTotal;}
   public static long newTotal(){return instance==null?0L:instance.newTotal;}
   public static long refreshedTotal(){return instance==null?0L:instance.refreshedTotal;}
   public static long evictedTotal(){return instance==null?0L:instance.store.evicted;}
   public static int surveyRate(){return instance==null?0:instance.ratePerSec;}
   public static long lastTickMicros(){return instance==null?0L:instance.lastTickNanos/1000L;}
}
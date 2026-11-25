package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ScanPlanner;
import net.minecraft.class_243;

/** Virtual scan center; no entity, camera manipulation, packets or player movement. */
public final class BackgroundFreecam extends Module {
   public enum State{OFF,NO_MEMORY,PAUSED,MOVING,FOLLOWING,WAITING}
   private static final long TARGET_TIMEOUT_TICKS=200L;
   private static BackgroundFreecam instance;
   private final ModeSetting mode=add(new ModeSetting("Modus","Intelligent","Intelligent","Spirale","Raster"));
   private final ModeSetting priority=add(new ModeSetting("Scan-Prioritaet","Naechste zuerst","Naechste zuerst","Neue zuerst","Veraltete zuerst"));
   private final NumberSetting scanRadius=add(new NumberSetting("Scan-Radius (Chunks)",8,2,32,1));
   private final NumberSetting moveRange=add(new NumberSetting("Bewegungsbereich (Chunks)",8,1,24,1));
   private final NumberSetting speed=add(new NumberSetting("Geschwindigkeit (Bloecke/Tick)",2,.25,16,.25));
   private final NumberSetting heightOffset=add(new NumberSetting("Scan-Hoehe (Offset)",0,-64,64,4));
   private final NumberSetting vertical=add(new NumberSetting("Vertikaler Bereich",64,16,384,16));
   private final BoolSetting auto=add(new BoolSetting("Automatische Erkundung",true));
   private final BoolSetting paused=add(new BoolSetting("Pausiert",false));
   private final ScanPlanner planner=new ScanPlanner();
   private final PlannerInfo info=new PlannerInfo();
   private boolean init;private long lastEpoch=Long.MIN_VALUE,tick;
   private double x,y,z,targetX,targetZ;private long targetSince;private State state=State.OFF;
   public BackgroundFreecam(){super("BackgroundFreecam","Virtuelles Scan-Zentrum ueber bereits geladenen Chunks; bewegt weder Spieler noch normale Kamera.",Category.WORLD);instance=this;}
   @Override protected void onEnable(){init=false;state=State.MOVING;ChunkMemory cm=ModuleManager.get(ChunkMemory.class);if(!cm.isEnabled())cm.setEnabled(true);}
   @Override protected void onDisable(){init=false;planner.hasTarget=false;state=State.OFF;}
   @Override public void onWorldLeave(){init=false;planner.hasTarget=false;state=State.WAITING;}
   @Override public void onTick(){
      if(mc.field_1724==null||mc.field_1687==null)return;tick++;
      if(!ModuleManager.get(ChunkMemory.class).isEnabled()){state=State.NO_MEMORY;return;}
      long epoch=ChunkMemory.worldEpoch();if(epoch!=lastEpoch){lastEpoch=epoch;init=false;}
      double px=mc.field_1724.method_23317(),pz=mc.field_1724.method_23321(),py=mc.field_1724.method_33571().field_1351;
      int pcx=(int)Math.floor(px)>>4,pcz=(int)Math.floor(pz)>>4,range=moveRange.getInt();
      double wantedY=Math.max(mc.field_1687.method_31607(),Math.min(mc.field_1687.method_31600()-1,py+heightOffset.get()));
      if(!init){x=px;y=wantedY;z=pz;planner.reset(pcx,pcz,range);init=true;}planner.setRange(range);
      double mx=x-px,mz=z-pz,max=(range+1)*16.0;
      if(mx*mx+mz*mz>max*max){x=px;z=pz;planner.reset(pcx,pcz,range);}
      if(paused.get()){state=State.PAUSED;publish(true);return;}
      double step=speed.get();
      if(!auto.get()){state=State.FOLLOWING;moveToward(px,pz,step);planner.hasTarget=false;}
      else{
         info.update(y,vertical.getInt());
         boolean arrived=planner.hasTarget&&dist2(targetX,targetZ)<=Math.max(4.0,step)*Math.max(4.0,step);
         boolean timeout=planner.hasTarget&&tick-targetSince>TARGET_TIMEOUT_TICKS;
         if(!planner.hasTarget||arrived||timeout)if(planner.pick(plannerMode(),pcx,pcz,(int)Math.floor(x)>>4,(int)Math.floor(z)>>4,info)){
            targetX=planner.targetCx*16.0+8.0;targetZ=planner.targetCz*16.0+8.0;targetSince=tick;
         }
         if(planner.hasTarget){state=State.MOVING;moveToward(targetX,targetZ,step);}else{state=State.WAITING;moveToward(px,pz,step);}
      }
      double dy=wantedY-y;y+=Math.max(-step,Math.min(step,dy));
      mx=x-px;mz=z-pz;double maxMove=range*16.0,d2=mx*mx+mz*mz;
      if(d2>maxMove*maxMove){double scale=maxMove/Math.sqrt(d2);x=px+mx*scale;z=pz+mz*scale;}
      publish(false);
   }
   private void publish(boolean p){ChunkMemory.publishScan(x,z,scanRadius.getInt(),priority.is("Neue zuerst")?1:priority.is("Veraltete zuerst")?2:0,p);}
   private ScanPlanner.Mode plannerMode(){return mode.is("Spirale")?ScanPlanner.Mode.SPIRAL:mode.is("Raster")?ScanPlanner.Mode.RASTER:ScanPlanner.Mode.INTELLIGENT;}
   private double dist2(double tx,double tz){double dx=tx-x,dz=tz-z;return dx*dx+dz*dz;}
   private void moveToward(double tx,double tz,double amount){double dx=tx-x,dz=tz-z,d=Math.sqrt(dx*dx+dz*dz);if(d<=amount||d<1e-6){x=tx;z=tz;}else{x+=dx/d*amount;z+=dz/d*amount;}}
   private final class PlannerInfo implements ScanPlanner.Info{
      int minY,maxY;void update(double cy,int r){minY=(int)Math.floor(cy)-r;maxY=(int)Math.floor(cy)+r;}
      public boolean available(int cx,int cz){return ChunkMemory.loaded(cx,cz)!=null;}
      public long age(int cx,int cz){return ChunkMemory.ageTicks(cx,cz);}
      public int interest(int cx,int cz){return ChunkMemory.interest(cx,cz,minY,maxY);}
   }
   public static boolean isActive(){return instance!=null&&instance.isEnabled()&&instance.init&&mc.field_1724!=null&&mc.field_1687!=null;}
   public static boolean isPaused(){return isActive()&&instance.paused.get();}
   public static class_243 cameraPosition(){return isActive()?new class_243(instance.x,instance.y,instance.z):null;}
   public static String movementModeName(){return instance==null?"-":instance.modeName();}
   public static State currentState(){return instance==null||!instance.isEnabled()?State.OFF:instance.state;}
   public static double centerX(){return instance==null?0:instance.x;}
   public static double centerY(){return instance==null?0:instance.y;}
   public static double centerZ(){return instance==null?0:instance.z;}
   public static int scanRadiusChunks(){return instance==null?0:instance.scanRadius.getInt();}
   public static double speedBlocksPerTick(){return instance==null?0:instance.speed.get();}
   public static String modeName(){return instance==null?"-":instance.displayModeName();}
   private String displayModeName(){return auto.get()?mode.get():"Folgt Spieler";}
}
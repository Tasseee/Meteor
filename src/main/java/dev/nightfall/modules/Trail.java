package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Deque;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/** Geometrischer Retro-Neon-Trail mit Lichtwand und blockigen Nachbildern. */
public final class Trail extends Module {
   private static final double STEP = 0.12;
   private static final int MAX_POINTS = 600;
   private static final int MAX_PHANTOMS = 14;
   private static Trail instance;
   private static boolean registered;
   private final ModeSetting style = this.add(new ModeSetting("Stil", "Beides", "Beides", "Lichtwand", "Phantome"));
   private final NumberSetting minSpeed = this.add(new NumberSetting("Mindesttempo (Blöcke/s)", 4.8, 1.0, 6.0, 0.1));
   private final BoolSetting groundOnly = this.add(new BoolSetting("Nur am Boden", false));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer (Sek.)", 1.6, 0.5, 4.0, 0.1));
   private final NumberSetting brightness = this.add(new NumberSetting("Helligkeit", 1.0, 0.2, 1.0, 0.01));
   private final NumberSetting wallHeight = this.add(new NumberSetting("Wandhöhe", 1.9, 0.5, 2.5, 0.1));
   private final BoolSetting scanlines = this.add(new BoolSetting("Scanline-Streifen", true));
   private final BoolSetting gridLines = this.add(new BoolSetting("Gitterlinien", true));
   private final BoolSetting glitch = this.add(new BoolSetting("Glitch", true));
   private final NumberSetting phantomSpacing = this.add(new NumberSetting("Phantom-Abstand", 1.3, 0.8, 3.0, 0.1));
   private final ModeSetting palette = this.add(new ModeSetting("Farbschema", "Synthwave", "Synthwave", "Sonnenuntergang", "Eis", "Giftgrün", "Regenbogen", "Eigene"));
   private final ColorSetting customStart = this.add(new ColorSetting("Eigene Farbe Start", 0xFF2D95));
   private final ColorSetting customEnd = this.add(new ColorSetting("Eigene Farbe Ende", 0x2DE2FF));
   private final Deque<Point> points = new ArrayDeque<>();
   private final Deque<Phantom> phantoms = new ArrayDeque<>();
   private Point last;
   private double headX, headY, headZ, phantomDistance, swingPhase;
   private boolean headValid, started, rainbow;
   private long lastNanos, lastTick, ticks;
   private float lastHud = -1;
   private double ownDelta;
   private int startColor = 0xFF2D95, endColor = 0x2DE2FF;
   private static class_4588 vertices;
   private static Matrix4f matrix;

   private record Point(double x, double y, double z, long time, boolean strip) {}
   private record Phantom(double x, double y, double z, double yaw, double swing, long time, double seed) {}

   public Trail() {
      super("Trail", "Geometrischer Neon-Trail mit Lichtwand, Scanlines, Glitch und Phantom-Nachbildern.", Category.VISUALS);
      instance = this;
      if (!registered) {
         registered = true;
         WorldRenderEvents.AFTER_ENTITIES.register(Trail::render);
      }
   }

   @Override protected void onEnable() { reset(); }
   @Override protected void onDisable() { reset(); }
   @Override public void onWorldLeave() { reset(); }
   @Override public void onTick() { ticks++; }

   private void reset() {
      points.clear(); phantoms.clear(); last = null; headValid = false;
      phantomDistance = 0; started = false;
   }

   private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
   private static double frac(double v) { return v - Math.floor(v); }

   private double delta(long now) {
      float hud = Projection.tickDelta();
      double d;
      if (!started) d = hud;
      else {
         double dt = (now - lastNanos) / 5.0E7;
         long ran = ticks - lastTick;
         d = (hud != lastHud ? hud : ownDelta) + dt - ran;
      }
      ownDelta = clamp(d, 0, 1); lastHud = hud; lastNanos = now; lastTick = ticks; started = true;
      return ownDelta;
   }

   private void colors() {
      rainbow = palette.is("Regenbogen");
      if (palette.is("Eigene")) { startColor = customStart.rgb(); endColor = customEnd.rgb(); }
      else if (palette.is("Sonnenuntergang")) { startColor = 0xFF3A7D; endColor = 0xFFC247; }
      else if (palette.is("Eis")) { startColor = 0xFFFFFF; endColor = 0x62C8FF; }
      else if (palette.is("Giftgrün")) { startColor = 0xB6FF2E; endColor = 0x00B36B; }
      else if (!rainbow) { startColor = 0xFF2D95; endColor = 0x2DE2FF; }
   }

   private int color(double p, double time) {
      p = clamp(p, 0, 1);
      if (rainbow) return Color.HSBtoRGB((float)((time * .15 + p * .5) % 1), .85F, 1F) & 0xFFFFFF;
      int r=(int)Math.round((startColor>>16&255)+((endColor>>16&255)-(startColor>>16&255))*p);
      int g=(int)Math.round((startColor>>8&255)+((endColor>>8&255)-(startColor>>8&255))*p);
      int b=(int)Math.round((startColor&255)+((endColor&255)-(startColor&255))*p);
      return r<<16|g<<8|b;
   }

   private void record(double x,double y,double z,double dx,double dz,boolean moving,long now) {
      if (moving) {
         if (last == null) { last = new Point(x,y,z,now,true); points.addLast(last); phantomDistance=0; }
         else {
            double ox=x-last.x(), oy=y-last.y(), oz=z-last.z();
            double dist=Math.sqrt(ox*ox+oy*oy+oz*oz);
            if (dist>=STEP) { last=new Point(x,y,z,now,false); points.addLast(last); phantomDistance+=dist; swingPhase+=dist*4.5; }
         }
         headX=x; headY=y; headZ=z; headValid=true;
         if (phantomDistance>=phantomSpacing.get()) {
            phantomDistance=0;
            while(phantoms.size()>=MAX_PHANTOMS) phantoms.removeFirst();
            phantoms.addLast(new Phantom(x,y,z,Math.atan2(-dx,dz),Math.sin(swingPhase)*.2,now,(now%100000L)*.001));
         }
      } else { last=null; headValid=false; phantomDistance=0; }
      long life=(long)(duration.get()*1.0E9);
      while(!points.isEmpty() && now-points.peekFirst().time()>life) points.removeFirst();
      while(points.size()>MAX_POINTS) points.removeFirst();
      while(!phantoms.isEmpty() && now-phantoms.peekFirst().time()>life) phantoms.removeFirst();
   }

   private static void render(WorldRenderContext context) {
      Trail t=instance;
      if(t==null||!t.isEnabled()) return;
      if(mc.field_1687==null||mc.field_1724==null||context.matrices()==null||context.consumers()==null) { t.started=false; return; }
      long now=System.nanoTime();
      double d=t.delta(now);
      var p=mc.field_1724;
      double x=p.field_6038+(p.method_23317()-p.field_6038)*d;
      double y=p.field_5971+(p.method_23318()-p.field_5971)*d;
      double z=p.field_5989+(p.method_23321()-p.field_5989)*d;
      double dx=p.method_23317()-p.field_6038, dz=p.method_23321()-p.field_5989;
      double len=Math.sqrt(dx*dx+dz*dz), speed=len*20;
      boolean moving=len>1E-4&&speed>=t.minSpeed.get()&&(!t.groundOnly.get()||p.method_24828());
      t.record(x,y,z,len>1E-4?dx/len:0,len>1E-4?dz/len:1,moving,now);
      boolean wall=!t.style.is("Phantome"), ghosts=!t.style.is("Lichtwand");
      if((!wall||t.points.size()<2)&&(!ghosts||t.phantoms.isEmpty())) return;
      t.colors();
      double time=(now%3_600_000_000_000L)/1E9;
      class_4184 camera=mc.field_1773.method_19418();
      if(camera==null) return;
      class_243 pos=camera.method_71156();
      class_4587 matrices=context.matrices();
      vertices=context.consumers().method_73477(class_12249.method_76019());
      matrices.method_22903(); matrices.method_22904(-pos.field_1352,-pos.field_1351,-pos.field_1350);
      try {
         matrix=matrices.method_23760().method_23761();
         if(ghosts)t.drawPhantoms(now,time);
         if(wall)t.drawWall(now,time);
      } finally { matrices.method_22909(); vertices=null; }
   }

   private void drawWall(long now,double time) {
      Point prev=null; int index=0; double life=duration.get(), h=wallHeight.get();
      long bucket=(long)Math.floor(time*6);
      for(Point p:points) {
         if(prev!=null&&!p.strip()) {
            double fa=clamp(1-(now-prev.time())/1E9/life,0,1), fb=clamp(1-(now-p.time())/1E9/life,0,1);
            double dx=p.x()-prev.x(), dz=p.z()-prev.z(), len=Math.sqrt(dx*dx+dz*dz);
            if(len>1E-5&&(fa>0||fb>0)) {
               double tx=dx/len,tz=dz/len, ya=prev.y()+.04,yb=p.y()+.04;
               double ha=h*(.25+.75*fa), hb=h*(.25+.75*fb);
               int ca=color(1-fa,time), cb=color(1-fb,time);
               int bands=scanlines.get()?10:1;
               for(int j=0;j<bands;j++) {
                  double v0=(double)j/bands,v1=(double)(j+1)/bands;
                  double shift=0;
                  if(glitch.get()&&fb>.35) { double g=frac(Math.sin(index*7.13+j*3.31+bucket*1.77)*43758.5453); if(g>.93)shift=(frac(g*97)-.5)*.5; }
                  double sx=tx*shift,sz=tz*shift;
                  float a0=(float)(brightness.get()*fa*(j%2==0?.85:.30)),a1=(float)(brightness.get()*fb*(j%2==0?.85:.30));
                  quad(prev.x()+sx,ya+ha*v0,prev.z()+sz,ca,a0,p.x()+sx,yb+hb*v0,p.z()+sz,cb,a1,p.x()+sx,yb+hb*v1,p.z()+sz,cb,a1,prev.x()+sx,ya+ha*v1,prev.z()+sz,ca,a0);
               }
               if(gridLines.get()&&index%4==0) {
                  double w=.014;
                  quad(prev.x()-tx*w,ya,prev.z()-tz*w,ca,(float)(fa*brightness.get()),prev.x()+tx*w,ya,prev.z()+tz*w,ca,(float)(fa*brightness.get()),prev.x()+tx*w,ya+ha,prev.z()+tz*w,ca,(float)(fa*brightness.get()),prev.x()-tx*w,ya+ha,prev.z()-tz*w,ca,(float)(fa*brightness.get()));
               }
            }
         }
         prev=p; index++;
      }
      if(headValid&&prev!=null) {
         double dx=headX-prev.x(),dz=headZ-prev.z(),len=Math.sqrt(dx*dx+dz*dz);
         if(len>1E-5) {
            double tx=dx/len,tz=dz/len; int c=color(0,time);
            quad(prev.x(),prev.y()+.04,prev.z(),c,brightness.get().floatValue(),headX,headY+.04,headZ,c,brightness.get().floatValue(),headX,headY+.04+h,headZ,c,brightness.get().floatValue(),prev.x(),prev.y()+.04+h,prev.z(),c,brightness.get().floatValue());
         }
      }
   }

   private void drawPhantoms(long now,double time) {
      double life=duration.get(); long bucket=(long)Math.floor(time*8);
      for(Phantom ph:phantoms) {
         double f=1-(now-ph.time())/1E9/life; if(f<=0)continue;
         double cs=Math.cos(ph.yaw()),sn=Math.sin(ph.yaw());
         int base=color(1-f,time);
         for(int copy=0;copy<3;copy++) {
            int rgb=copy==0?base:copy==1?color(0,time):color(1,time);
            double chroma=copy==0?0:copy==1?.05:-.05;
            double a=(copy==0?.50:.28)*f*brightness.get();
            double shift=0;
            if(glitch.get()) { double g=frac(Math.sin(ph.seed()+bucket*2.7+copy*5.1)*43758.5453); if(g>.8)shift=(frac(g*71)-.5)*.3; }
            part(ph,cs,sn,chroma+shift,.375,ph.swing(),.125,.375,.125,rgb,a);
            part(ph,cs,sn,-.125+chroma+shift,.375,-ph.swing(),.125,.375,.125,rgb,a);
            part(ph,cs,sn,chroma+shift,1.125,0,.25,.375,.125,rgb,a);
            part(ph,cs,sn,-.375+chroma+shift,1.125,-ph.swing()*.9,.125,.375,.125,rgb,a);
            part(ph,cs,sn,.375+chroma+shift,1.125,ph.swing()*.9,.125,.375,.125,rgb,a);
            part(ph,cs,sn,chroma+shift,1.75,0,.25,.25,.25,rgb,a);
         }
      }
   }

   private static void part(Phantom p,double cs,double sn,double lx,double ly,double lz,double hx,double hy,double hz,int rgb,double a) {
      double x=p.x()+lx*cs-lz*sn,z=p.z()+lx*sn+lz*cs;
      box(x,p.y()+ly,z,hx,hy,hz,cs,sn,rgb,a);
   }

   private static final int[][] FACES={{0,0,0,0,0,1,0,1,1,0,1,0},{1,0,1,1,0,0,1,1,0,1,1,1},{0,0,0,1,0,0,1,0,1,0,0,1},{0,1,1,1,1,1,1,1,0,0,1,0},{0,0,0,0,1,0,1,1,0,1,0,0},{1,0,1,1,1,1,0,1,1,0,0,1}};
   private static final double[] SHADE={.8,.8,.6,1,.9,.9};
   private static void box(double x,double y,double z,double hx,double hy,double hz,double cs,double sn,int rgb,double a) {
      for(int f=0;f<6;f++) {
         int[] face=FACES[f]; int c=shade(rgb,SHADE[f]);
         double[] q=new double[12];
         for(int v=0;v<4;v++) {
            double ox=(face[v*3]==1?hx:-hx), oy=(face[v*3+1]==1?hy:-hy), oz=(face[v*3+2]==1?hz:-hz);
            q[v*3]=x+ox*cs-oz*sn; q[v*3+1]=y+oy; q[v*3+2]=z+ox*sn+oz*cs;
         }
         quad(q[0],q[1],q[2],c,(float)a,q[3],q[4],q[5],c,(float)a,q[6],q[7],q[8],c,(float)a,q[9],q[10],q[11],c,(float)a);
      }
   }
   private static int shade(int c,double f) { return (int)((c>>16&255)*f)<<16|(int)((c>>8&255)*f)<<8|(int)((c&255)*f); }
   private static void quad(double x0,double y0,double z0,int c0,float a0,double x1,double y1,double z1,int c1,float a1,double x2,double y2,double z2,int c2,float a2,double x3,double y3,double z3,int c3,float a3) {
      put(x0,y0,z0,c0,a0);put(x1,y1,z1,c1,a1);put(x2,y2,z2,c2,a2);put(x3,y3,z3,c3,a3);
      put(x3,y3,z3,c3,a3);put(x2,y2,z2,c2,a2);put(x1,y1,z1,c1,a1);put(x0,y0,z0,c0,a0);
   }
   private static void put(double x,double y,double z,int c,float a) {
      vertices.method_22918(matrix,(float)x,(float)y,(float)z).method_22915((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,(float)clamp(a,0,1));
   }
}

package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.KillEffect;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/** Filled-geometry renderer for KillEffect; no line pipeline is used. */
public final class KillEffectRenderer {
   private static final int SEGMENTS = 48;
   private KillEffectRenderer() {}
   public static void register() { WorldRenderEvents.AFTER_ENTITIES.register(KillEffectRenderer::render); }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;
      KillEffect module;
      try { module = ModuleManager.get(KillEffect.class); } catch (Throwable ignored) { return; }
      if (!module.isEnabled() || module.effects().isEmpty()) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cam = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 out = context.consumers().method_73477(class_12249.method_76019());
      long now = System.nanoTime();
      matrices.method_22903();
      matrices.method_22904(-cam.field_1352, -cam.field_1351, -cam.field_1350);
      try {
         Matrix4f matrix = matrices.method_23760().method_23761();
         for (KillEffect.Effect fx : module.effects()) {
            double progress = (now - fx.startNanos()) / (double)module.durationNanos();
            if (progress < 0.0 || progress >= 1.0) continue;
            double s = module.scale();
            if (module.ringEnabled()) drawRing(out, matrix, module, fx, progress, now, s);
            if (module.pillarEnabled()) drawPillar(out, matrix, module, fx, progress, now, s);
            if (module.sparkCount() > 0) drawSparks(out, matrix, module, fx, progress, now, s);
            if (module.soulsEnabled()) drawSouls(out, matrix, module, fx, progress, now, s);
         }
      } finally { matrices.method_22909(); }
   }

   private static void drawRing(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx, double p, long now, double scale) {
      double t = Math.min(1.0, p / 0.55);
      if (t >= 1.0) return;
      double eased = 1.0 - Math.pow(1.0 - t, 3.0);
      double outer = eased * 6.0 * scale + 0.35;
      double inner = Math.max(0.0, outer - (0.9 - 0.5 * t) * scale);
      float alpha = (float)(0.82 * Math.pow(1.0 - t, 1.3));
      double y = fx.pos().field_1351 + 0.035;
      for (int i = 0; i < SEGMENTS; i++) {
         double a0 = i * Math.PI * 2.0 / SEGMENTS, a1 = (i + 1) * Math.PI * 2.0 / SEGMENTS;
         int rgb = mod.color(i / (double)SEGMENTS, fx.seed(), now);
         float[] c = rgba(rgb, alpha);
         quad(out,m,
            (float)(fx.pos().field_1352+Math.cos(a0)*inner),(float)y,(float)(fx.pos().field_1350+Math.sin(a0)*inner),
            (float)(fx.pos().field_1352+Math.cos(a0)*outer),(float)y,(float)(fx.pos().field_1350+Math.sin(a0)*outer),
            (float)(fx.pos().field_1352+Math.cos(a1)*outer),(float)y,(float)(fx.pos().field_1350+Math.sin(a1)*outer),
            (float)(fx.pos().field_1352+Math.cos(a1)*inner),(float)y,(float)(fx.pos().field_1350+Math.sin(a1)*inner),c);
      }
   }

   private static void drawPillar(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx, double p, long now, double scale) {
      double rise = Math.min(1.0, p / 0.30);
      double height = (1.0 - Math.pow(1.0-rise,3.0))*8.0*scale;
      double fade = Math.min(1.0,p*14.0)*Math.pow(1.0-p,1.5);
      for (int layer=0; layer<2; layer++) {
         double r = (layer==0 ? 0.16 : 0.48)*scale*(1.0-0.4*p);
         int slices=16;
         for(int j=0;j<slices;j++) {
            double y0=fx.pos().field_1351+height*j/slices, y1=fx.pos().field_1351+height*(j+1)/slices;
            float a=(float)((layer==0?0.78:0.22)*fade*Math.pow(1.0-(j+0.5)/slices,1.1));
            if(a<0.01F) continue;
            float[] c=rgba(mod.color((j+0.5)/slices,fx.seed(),now),a);
            for(int k=0;k<SEGMENTS;k++) {
               double a0=k*Math.PI*2/SEGMENTS,a1=(k+1)*Math.PI*2/SEGMENTS;
               quad(out,m,
                  (float)(fx.pos().field_1352+Math.cos(a0)*r),(float)y0,(float)(fx.pos().field_1350+Math.sin(a0)*r),
                  (float)(fx.pos().field_1352+Math.cos(a1)*r),(float)y0,(float)(fx.pos().field_1350+Math.sin(a1)*r),
                  (float)(fx.pos().field_1352+Math.cos(a1)*r),(float)y1,(float)(fx.pos().field_1350+Math.sin(a1)*r),
                  (float)(fx.pos().field_1352+Math.cos(a0)*r),(float)y1,(float)(fx.pos().field_1350+Math.sin(a0)*r),c);
            }
         }
      }
   }

   private static void drawSparks(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx, double p, long now, double scale) {
      double seconds=(now-fx.startNanos())/1.0E9;
      for(int i=0;i<mod.sparkCount();i++) {
         double seed=i*12.9898+(fx.seed()%1000)*0.137;
         double angle=frac(Math.sin(seed)*43758.5453)*Math.PI*2;
         double speed=(2.0+4.0*frac(Math.sin(seed*1.7)*12345.678))*scale;
         double up=(3.0+5.0*frac(Math.sin(seed*2.3)*9876.543))*scale;
         double life=0.55+0.45*frac(Math.sin(seed*3.1)*777.77);
         double lp=p/life; if(lp>=1.0) continue;
         double x=fx.pos().field_1352+Math.cos(angle)*speed*seconds;
         double z=fx.pos().field_1350+Math.sin(angle)*speed*seconds;
         double y=Math.max(fx.pos().field_1351+0.03,fx.pos().field_1351+0.7+up*seconds-7.0*seconds*seconds);
         float a=(float)(Math.min(1.0,lp*12.0)*Math.pow(1.0-lp,1.2));
         if(a<0.02F) continue;
         float[] c=rgba(mod.color(frac(Math.sin(seed*5.3)*321.9),fx.seed(),now),a);
         double size=(0.035+0.035*frac(Math.sin(seed*2.9)*777.7))*scale;
         cube(out,m,x,y,z,size,c);
      }
   }

   private static void drawSouls(class_4588 out, Matrix4f m, KillEffect mod, KillEffect.Effect fx, double p, long now, double scale) {
      double fade=Math.min(1.0,p*8.0)*Math.pow(1.0-p,1.1);
      for(int w=0;w<3;w++) for(int k=0;k<24;k++) {
         double tail=k/24.0, t=p-tail*0.30; if(t<0.0) continue;
         double y=fx.pos().field_1351+0.2+t*9.0*scale, angle=t*9.0+w*Math.PI*2/3;
         double radius=(0.4+0.85*t)*scale, x=fx.pos().field_1352+Math.cos(angle)*radius, z=fx.pos().field_1350+Math.sin(angle)*radius;
         float a=(float)(fade*Math.pow(1.0-tail,1.4)); if(a<0.02F) continue;
         float[] c=rgba(mod.color(t,fx.seed(),now),a);
         double size=(0.08-0.045*tail)*scale;
         cube(out,m,x,y,z,size,c);
      }
   }

   private static void cube(class_4588 out, Matrix4f m, double x,double y,double z,double s,float[] c) {
      float x0=(float)(x-s),x1=(float)(x+s),y0=(float)(y-s),y1=(float)(y+s),z0=(float)(z-s),z1=(float)(z+s);
      quad(out,m,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,c);
      quad(out,m,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,c);
      quad(out,m,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,c);
      quad(out,m,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,c);
      quad(out,m,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1,c);
      quad(out,m,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0,c);
   }

   private static void quad(class_4588 out, Matrix4f m,
      float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,float[] c) {
      vertex(out,m,x0,y0,z0,c); vertex(out,m,x1,y1,z1,c); vertex(out,m,x2,y2,z2,c); vertex(out,m,x3,y3,z3,c);
   }
   private static void vertex(class_4588 out, Matrix4f m,float x,float y,float z,float[] c) {
      out.method_22918(m,x,y,z).method_22915(c[0],c[1],c[2],c[3]);
   }
   private static float[] rgba(int rgb,float a) { return new float[]{(rgb>>16&255)/255F,(rgb>>8&255)/255F,(rgb&255)/255F,Math.max(0F,Math.min(1F,a))}; }
   private static double frac(double x) { return x-Math.floor(x); }
}

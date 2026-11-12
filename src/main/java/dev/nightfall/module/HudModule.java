package dev.nightfall.module;

import dev.nightfall.config.Config;
import net.minecraft.class_332;
import org.joml.Matrix3x2fStack;

public abstract class HudModule extends Module {
   protected int w = 60;
   protected int h = 12;
   protected boolean scalable = true;

   protected HudModule(String name, String desc) {
      super(name, desc, Category.HUD);
   }

   protected abstract int[] defaultPos(int var1, int var2);

   protected abstract void draw(class_332 var1);

   public boolean isScalable() {
      return this.scalable;
   }

   public float scale() {
      int[] s = Config.hud.get(this.getName());
      return s == null ? 1.0F : s[2] / 100.0F;
   }

   public boolean isLocked() {
      int[] s = Config.hud.get(this.getName());
      return s != null && s[3] == 1;
   }

   public int width() {
      return Math.round(this.w * this.scale());
   }

   public int height() {
      return Math.round(this.h * this.scale());
   }

   public int[] pos(int sw, int sh) {
      int[] s = Config.hud.get(this.getName());
      return s != null ? new int[]{s[0], s[1]} : this.defaultPos(sw, sh);
   }

   public int[] ensureState(int sw, int sh) {
      return Config.hud.computeIfAbsent(this.getName(), k -> {
         int[] p = this.defaultPos(sw, sh);
         return new int[]{p[0], p[1], 100, 0};
      });
   }

   public void resetState() {
      Config.hud.remove(this.getName());
   }

   @Override
   public void renderHud(class_332 ctx) {
      int[] p = this.pos(ctx.method_51421(), ctx.method_51443());
      float sc = this.scalable ? this.scale() : 1.0F;
      Matrix3x2fStack m = ctx.method_51448();
      m.pushMatrix();
      m.translate(p[0], p[1]);
      m.scale(sc, sc);
      this.draw(ctx);
      m.popMatrix();
   }

   public void drawForEditor(class_332 ctx) {
      if (this.isEnabled()) {
         this.renderHud(ctx);
      }
   }
}

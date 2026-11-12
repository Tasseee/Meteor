package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;
import org.joml.Matrix3x2fStack;

public class Minimap extends HudModule {
   private final NumberSetting size = this.add(new NumberSetting("Groesse", 96.0, 50.0, 220.0, 2.0));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite (Chunks)", 8.0, 2.0, 24.0, 1.0));
   private final NumberSetting opacity = this.add(new NumberSetting("Transparenz", 0.75, 0.2, 1.0, 0.05));
   private final BoolSetting rotate = this.add(new BoolSetting("Rotieren", true));
   private final BoolSetting coords = this.add(new BoolSetting("Koordinaten", true));

   public Minimap() {
      super("Minimap", "Chunk-Radar mit SUS-/SUS-WEG-Markierungen.");
      this.scalable = false;
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      int s = this.size.getInt();
      return new int[]{sw - s - 6, sh - s - 6};
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         BaseFinder bf = ModuleManager.get(BaseFinder.class);
         int s = this.size.getInt();
         this.w = s;
         this.h = s;
         int[] p = this.pos(ctx.method_51421(), ctx.method_51443());
         int x = p[0];
         int y = p[1];
         int a = (int)(this.opacity.get() * 255.0);
         float cx = x + s / 2.0F;
         float cy = y + s / 2.0F;
         int r = this.range.getInt();
         float cell = s / (2.0F * r + 1.0F);
         double px = mc.field_1724.method_23317();
         double pz = mc.field_1724.method_23321();
         int pcx = mc.field_1724.method_31477() >> 4;
         int pcz = mc.field_1724.method_31479() >> 4;
         float fx = (float)((px - pcx * 16.0) / 16.0);
         float fz = (float)((pz - pcz * 16.0) / 16.0);
         float yaw = mc.field_1724.method_36454();
         double theta = this.rotate.get() ? Math.toRadians(180.0 - yaw) : 0.0;
         ctx.method_25294(x, y, x + s, y + s, Theme.withAlpha(Theme.MAP_BG, a));
         ctx.method_44379(x, y, x + s, y + s);
         Matrix3x2fStack m = ctx.method_51448();
         m.pushMatrix();
         m.translate(cx, cy);
         if (this.rotate.get()) {
            m.rotate((float)theta);
         }

         m.translate(-fx * cell, -fz * cell);
         int ext = this.rotate.get() ? (int)Math.ceil(r * 1.5) : r;

         for (int dz = -ext; dz <= ext; dz++) {
            for (int dx = -ext; dx <= ext; dx++) {
               ChunkResult res = bf.get(pcx + dx, pcz + dz);
               int rgb;
               if (res == null) {
                  rgb = 1710626;
               } else {
                  switch (res.status) {
                     case SUS:
                        rgb = Theme.lerp(10181878, 16726876, (float)(res.score / (bf.thresholdValue() * 3.0)));
                        break;
                     case GONE:
                        rgb = 3108827;
                        break;
                     default:
                        rgb = 2894904;
                  }
               }

               int x0 = Math.round(dx * cell);
               int x1 = Math.round((dx + 1) * cell);
               int z0 = Math.round(dz * cell);
               int z1 = Math.round((dz + 1) * cell);
               ctx.method_25294(x0, z0, x1 - 1, z1 - 1, Theme.withAlpha(rgb, Math.min(255, a + 30)));
            }
         }

         m.popMatrix();
         ctx.method_44380();
         ctx.method_25294((int)cx - 1, (int)cy - 1, (int)cx + 2, (int)cy + 2, -1);
         int fxm;
         int fym;
         if (this.rotate.get()) {
            fxm = (int)cx;
            fym = (int)cy - 8;
         } else {
            double yr = Math.toRadians(yaw);
            fxm = (int)(cx - Math.sin(yr) * 8.0);
            fym = (int)(cy + Math.cos(yr) * 8.0);
         }

         ctx.method_25294(fxm, fym, fxm + 2, fym + 2, Theme.ACCENT);
         double rad = s / 2.0 - 7.0;
         int nx = (int)(cx + Math.sin(theta) * rad) - 2;
         int ny = (int)(cy - Math.cos(theta) * rad) - 3;
         ctx.method_51433(mc.field_1772, "N", nx, ny, -41862, true);
         ctx.method_25294(x - 1, y - 1, x + s + 1, y, Theme.ACCENT);
         ctx.method_25294(x - 1, y + s, x + s + 1, y + s + 1, Theme.ACCENT);
         ctx.method_25294(x - 1, y, x, y + s, Theme.ACCENT);
         ctx.method_25294(x + s, y, x + s + 1, y + s, Theme.ACCENT);
         if (this.coords.get()) {
            String t = mc.field_1724.method_31477() + ", " + mc.field_1724.method_31479();
            ctx.method_51433(mc.field_1772, t, x + s - mc.field_1772.method_1727(t), y - 11, Theme.TEXT, true);
         }
      }
   }

   @Override
   protected void draw(class_332 ctx) {
   }
}

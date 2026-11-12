package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

public class MapScreen extends class_437 {
   private static double cx;
   private static double cz;
   private static double zoom = 12.0;
   private static int filter = 1;
   private static boolean init;
   private boolean prevL;
   private boolean prevR;
   private boolean prevM;
   private boolean prevC;
   private boolean prev1;
   private boolean prev2;
   private boolean prev3;
   private double lastMx;
   private double lastMy;

   public MapScreen() {
      super(class_2561.method_43470("Map"));
      class_310 mc = class_310.method_1551();
      if (!init && mc.field_1724 != null) {
         cx = mc.field_1724.method_23317();
         cz = mc.field_1724.method_23321();
         init = true;
      }

      long h = mc.method_22683().method_4490();
      this.prevL = GLFW.glfwGetMouseButton(h, 0) == 1;
      this.prevR = GLFW.glfwGetMouseButton(h, 1) == 1;
      this.prevM = GLFW.glfwGetKey(h, Config.mapKey) == 1;
      this.prevC = GLFW.glfwGetKey(h, 67) == 1;
      this.prev1 = this.prev2 = this.prev3 = true;
      this.prev1 = GLFW.glfwGetKey(h, 49) == 1;
      this.prev2 = GLFW.glfwGetKey(h, 50) == 1;
      this.prev3 = GLFW.glfwGetKey(h, 51) == 1;
   }

   public boolean method_25421() {
      return false;
   }

   public boolean method_25401(double mx, double my, double hAmount, double vAmount) {
      double old = zoom;
      zoom = Math.max(2.0, Math.min(64.0, zoom * (vAmount > 0.0 ? 1.2 : 0.8333333333333334)));
      double bx = cx + (mx - this.field_22789 / 2.0) / old * 16.0;
      double bz = cz + (my - this.field_22790 / 2.0) / old * 16.0;
      cx = bx - (mx - this.field_22789 / 2.0) / zoom * 16.0;
      cz = bz - (my - this.field_22790 / 2.0) / zoom * 16.0;
      return true;
   }

   private double sx(double bx) {
      return this.field_22789 / 2.0 + (bx - cx) / 16.0 * zoom;
   }

   private double sy(double bz) {
      return this.field_22790 / 2.0 + (bz - cz) / 16.0 * zoom;
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      class_310 mc = class_310.method_1551();
      long h = mc.method_22683().method_4490();
      boolean l = GLFW.glfwGetMouseButton(h, 0) == 1;
      boolean r = GLFW.glfwGetMouseButton(h, 1) == 1;
      boolean mk = GLFW.glfwGetKey(h, Config.mapKey) == 1;
      boolean ck = GLFW.glfwGetKey(h, 67) == 1;
      boolean k1 = GLFW.glfwGetKey(h, 49) == 1;
      boolean k2 = GLFW.glfwGetKey(h, 50) == 1;
      boolean k3 = GLFW.glfwGetKey(h, 51) == 1;
      boolean clickR = r && !this.prevR;
      if (mk && !this.prevM) {
         this.method_25419();
      } else {
         if (k1 && !this.prev1) {
            filter = 1;
         }

         if (k2 && !this.prev2) {
            filter = 2;
         }

         if (k3 && !this.prev3) {
            filter = 3;
         }

         if (ck && !this.prevC && mc.field_1724 != null) {
            cx = mc.field_1724.method_23317();
            cz = mc.field_1724.method_23321();
         }

         if (l && this.prevL) {
            cx = cx - (mx - this.lastMx) / zoom * 16.0;
            cz = cz - (my - this.lastMy) / zoom * 16.0;
         }

         this.prevL = l;
         this.prevR = r;
         this.prevM = mk;
         this.prevC = ck;
         this.prev1 = k1;
         this.prev2 = k2;
         this.prev3 = k3;
         this.lastMx = mx;
         this.lastMy = my;
         BaseFinder bf = ModuleManager.get(BaseFinder.class);
         ctx.method_25294(0, 0, this.field_22789, this.field_22790, -267908334);
         ctx.method_25294(0, 0, this.field_22789, 2, Theme.ACCENT);
         int cell = Math.max(1, (int)Math.round(zoom));

         for (ChunkResult res : bf.results()) {
            boolean show = switch (filter) {
               case 2 -> res.status == ChunkResult.Status.SUS;
               case 3 -> res.status == ChunkResult.Status.GONE;
               default -> true;
            };
            if (show) {
               int x0 = (int)Math.round(this.sx(res.cx * 16.0));
               int y0 = (int)Math.round(this.sy(res.cz * 16.0));
               if (x0 + cell >= 0 && y0 + cell >= 0 && x0 <= this.field_22789 && y0 <= this.field_22790) {
                  int rgb = switch (res.status) {
                     case SUS -> Theme.lerp(10181878, 16726876, (float)(res.score / (bf.thresholdValue() * 3.0)));
                     case GONE -> 3108827;
                     default -> 2368558;
                  };
                  ctx.method_25294(x0, y0, x0 + cell, y0 + cell, 0xFF000000 | rgb);
               }
            }
         }

         if (zoom >= 8.0) {
            int c0 = (int)Math.floor((cx - this.field_22789 / 2.0 / zoom * 16.0) / 16.0);
            int c1 = (int)Math.ceil((cx + this.field_22789 / 2.0 / zoom * 16.0) / 16.0);
            int z0 = (int)Math.floor((cz - this.field_22790 / 2.0 / zoom * 16.0) / 16.0);
            int z1 = (int)Math.ceil((cz + this.field_22790 / 2.0 / zoom * 16.0) / 16.0);

            for (int c = c0; c <= c1; c++) {
               int x = (int)Math.round(this.sx(c * 16.0));
               ctx.method_25294(x, 0, x + 1, this.field_22790, c % 8 == 0 ? 822083583 : 318767103);
            }

            for (int z = z0; z <= z1; z++) {
               int y = (int)Math.round(this.sy(z * 16.0));
               ctx.method_25294(0, y, this.field_22789, y + 1, z % 8 == 0 ? 822083583 : 318767103);
            }
         }

         String dim = Storage.dim();
         Waypoint hoverWp = null;

         for (Waypoint w : WaypointManager.all()) {
            if (w.dim.equals(dim)) {
               int x = (int)this.sx(w.x + 0.5);
               int y = (int)this.sy(w.z + 0.5);
               if (x >= -10 && y >= -10 && x <= this.field_22789 + 10 && y <= this.field_22790 + 10) {
                  boolean fake = "FakeBase".equalsIgnoreCase(w.category);
                  int markerColor = fake ? -41099 : 0xFF000000 | w.color;
                  ctx.method_25294(x - 4, y - 4, x + 5, y + 5, markerColor);
                  ctx.method_25294(x - 2, y - 2, x + 3, y + 3, -1);
                  if (zoom >= 6.0) {
                     ctx.method_51433(mc.field_1772, (fake ? "[TEST/DECOY] " : "") + w.name, x + 6, y - 4, fake ? -30825 : Theme.TEXT, true);
                  }

                  if (Math.abs(mx - x) <= 5 && Math.abs(my - y) <= 5) {
                     hoverWp = w;
                  }
               }
            }
         }

         if (mc.field_1724 != null) {
            int px = (int)this.sx(mc.field_1724.method_23317());
            int py = (int)this.sy(mc.field_1724.method_23321());
            ctx.method_25294(px - 2, py - 2, px + 3, py + 3, -1);
            double yr = Math.toRadians(mc.field_1724.method_36454());
            int fx = (int)(px - Math.sin(yr) * 8.0);
            int fy = (int)(py + Math.cos(yr) * 8.0);
            ctx.method_25294(fx - 1, fy - 1, fx + 2, fy + 2, Theme.ACCENT);
         }

         double bx = cx + (mx - this.field_22789 / 2.0) / zoom * 16.0;
         double bz = cz + (my - this.field_22790 / 2.0) / zoom * 16.0;
         int hcx = (int)Math.floor(bx / 16.0);
         int hcz = (int)Math.floor(bz / 16.0);
         ChunkResult hr = bf.get(hcx, hcz);
         String coords = "Block " + (int)Math.floor(bx) + ", " + (int)Math.floor(bz) + "   Chunk " + hcx + ", " + hcz;
         ctx.method_25294(6, 5, Math.min(this.field_22789 - 6, mc.field_1772.method_1727(coords) + 22), 24, Theme.PANEL);
         ctx.method_25294(6, 5, 8, 24, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, coords, 13, 10, Theme.TEXT, true);
         if (hr != null && hr.status != ChunkResult.Status.CLEAN) {
            String t1 = (hr.status == ChunkResult.Status.SUS ? "SUS" : "SUS WEG") + "  Score " + String.format("%.1f", hr.score);
            String t2 = String.join(", ", hr.reasons);
            String t3 = hr.foundAtMillis > 0L ? "erkannt vor " + ago(System.currentTimeMillis() - hr.foundAtMillis) : "";
            int tw = Math.max(mc.field_1772.method_1727(t1), Math.max(mc.field_1772.method_1727(t2), mc.field_1772.method_1727(t3))) + 10;
            int tx = Math.min(mx + 10, this.field_22789 - tw - 4);
            int ty = Math.min(my + 10, this.field_22790 - 40);
            ctx.method_25294(tx, ty, tx + tw, ty + 34, Theme.HUD_PANEL);
            ctx.method_25294(tx, ty, tx + 2, ty + 34, Theme.ACCENT);
            ctx.method_51433(mc.field_1772, t1, tx + 6, ty + 3, Theme.TEXT, false);
            ctx.method_51433(mc.field_1772, t2, tx + 6, ty + 13, Theme.TEXT_DIM, false);
            ctx.method_51433(mc.field_1772, t3, tx + 6, ty + 23, Theme.TEXT_DIM, false);
         }

         if (clickR) {
            if (hoverWp != null) {
               WaypointManager.remove(hoverWp);
            } else if (mc.field_1724 != null) {
               WaypointManager.add("WP" + (WaypointManager.all().size() + 1), "Default", (int)Math.floor(bx), mc.field_1724.method_31478(), (int)Math.floor(bz));
            }
         }

         String[] names = new String[]{"Alle", "Verdaechtig", "Verschwunden"};
         ctx.method_25294(6, this.field_22790 - 52, 190, this.field_22790 - 6, Theme.PANEL);
         ctx.method_25294(6, this.field_22790 - 52, 8, this.field_22790 - 6, Theme.ACCENT);
         ctx.method_25294(6, this.field_22790 - 54, 190, this.field_22790 - 52, Theme.ACCENT_DIM);
         legend(ctx, mc, 14, this.field_22790 - 46, 10181878, "SUS (Score steigt: rot)");
         legend(ctx, mc, 14, this.field_22790 - 34, 3108827, "SUS WEG");
         legend(ctx, mc, 14, this.field_22790 - 22, 2368558, "gescannt, unauffaellig");
         String f = "Filter [1/2/3]: " + names[filter - 1] + "   C: zentrieren   RMB: Waypoint";
         ctx.method_51433(mc.field_1772, f, this.field_22789 - mc.field_1772.method_1727(f) - 8, 8, Theme.TEXT_DIM, true);
      }
   }

   private static void legend(class_332 ctx, class_310 mc, int x, int y, int rgb, String t) {
      ctx.method_25294(x, y, x + 8, y + 8, 0xFF000000 | rgb);
      ctx.method_51433(mc.field_1772, t, x + 12, y, Theme.TEXT, false);
   }

   private static String ago(long ms) {
      long s = ms / 1000L;
      if (s < 60L) {
         return s + "s";
      } else {
         return s < 3600L ? s / 60L + "min" : s / 3600L + "h";
      }
   }
}

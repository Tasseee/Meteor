package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

public class HudEditorScreen extends class_437 {
   private final List<HudModule> elements = ModuleManager.hudElements();
   private boolean prevL;
   private boolean prevR;
   private boolean prevM;
   private boolean prevR2;
   private boolean prevG;
   private boolean prevKey;
   private HudModule dragging;
   private int dragDx;
   private int dragDy;
   private HudModule hovered;

   public HudEditorScreen() {
      super(class_2561.method_43470("HUD Editor"));
      long h = class_310.method_1551().method_22683().method_4490();
      this.prevL = GLFW.glfwGetMouseButton(h, 0) == 1;
      this.prevR = GLFW.glfwGetMouseButton(h, 1) == 1;
      this.prevM = GLFW.glfwGetMouseButton(h, 2) == 1;
      this.prevR2 = GLFW.glfwGetKey(h, 82) == 1;
      this.prevG = GLFW.glfwGetKey(h, 71) == 1;
      this.prevKey = GLFW.glfwGetKey(h, Config.hudKey) == 1;
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25432() {
      Config.save();
   }

   private static boolean hit(HudModule e, int mx, int my, int sw, int sh) {
      int[] p = e.pos(sw, sh);
      return mx >= p[0] && mx < p[0] + Math.max(8, e.width()) && my >= p[1] && my < p[1] + Math.max(8, e.height());
   }

   public boolean method_25401(double mouseX, double mouseY, double horizontal, double vertical) {
      if (this.hovered != null && this.hovered.isScalable() && !this.hovered.isLocked()) {
         int[] st = this.hovered.ensureState(this.field_22789, this.field_22790);
         st[2] = Math.max(50, Math.min(300, st[2] + (vertical > 0.0 ? 10 : -10)));
         return true;
      } else {
         return false;
      }
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      class_310 mc = class_310.method_1551();
      long h = mc.method_22683().method_4490();
      boolean l = GLFW.glfwGetMouseButton(h, 0) == 1;
      boolean r = GLFW.glfwGetMouseButton(h, 1) == 1;
      boolean md = GLFW.glfwGetMouseButton(h, 2) == 1;
      boolean rk = GLFW.glfwGetKey(h, 82) == 1;
      boolean gk = GLFW.glfwGetKey(h, 71) == 1;
      boolean hk = GLFW.glfwGetKey(h, Config.hudKey) == 1;
      boolean clickL = l && !this.prevL;
      boolean clickR = r && !this.prevR;
      boolean clickM = md && !this.prevM;
      boolean keyR = rk && !this.prevR2;
      boolean keyG = gk && !this.prevG;
      boolean keyH = hk && !this.prevKey;
      this.prevL = l;
      this.prevR = r;
      this.prevM = md;
      this.prevR2 = rk;
      this.prevG = gk;
      this.prevKey = hk;
      if (keyH) {
         this.method_25419();
      } else {
         if (keyG) {
            Config.snapGrid = !Config.snapGrid;
         }

         ctx.method_25294(0, 0, this.field_22789, this.field_22790, Theme.BG_DIM);
         ctx.method_25294(0, 0, this.field_22789, 3, Theme.ACCENT);
         ctx.method_25294(0, this.field_22790 - 3, this.field_22789, this.field_22790, Theme.ACCENT_DIM);
         ctx.method_25294(12, 10, 238, 43, Theme.PANEL);
         ctx.method_25294(12, 10, 15, 43, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, "NIGHTFALL  /  HUD STUDIO", 23, 17, Theme.TEXT, true);
         ctx.method_51433(mc.field_1772, "LAYOUT EDITOR", 23, 29, Theme.TEXT_DIM, false);
         ctx.method_25294(0, 0, this.field_22789, 3, Theme.ACCENT);
         ctx.method_25294(0, this.field_22790 - 3, this.field_22789, this.field_22790, Theme.ACCENT_DIM);
         ctx.method_25294(12, 10, 238, 43, Theme.PANEL);
         ctx.method_25294(12, 10, 15, 43, Theme.ACCENT);
         ctx.method_25294(0, 0, this.field_22789, 2, Theme.withAlpha(Theme.ACCENT, 110));
         if (Config.snapGrid) {
            int g = Math.max(2, Config.gridSize * 4);

            for (int x = 0; x < this.field_22789; x += g) {
               ctx.method_25294(x, 0, x + 1, this.field_22790, 352321535);
            }

            for (int y = 0; y < this.field_22790; y += g) {
               ctx.method_25294(0, y, this.field_22789, y + 1, 352321535);
            }
         }

         ctx.method_25294(this.field_22789 / 2, 0, this.field_22789 / 2 + 1, this.field_22790, 587202559);
         ctx.method_25294(0, this.field_22790 / 2, this.field_22789, this.field_22790 / 2 + 1, 587202559);
         this.hovered = null;

         for (HudModule e : this.elements) {
            if ((e.isEnabled() || e.width() > 0) && hit(e, mx, my, this.field_22789, this.field_22790)) {
               this.hovered = e;
            }
         }

         if (!l) {
            this.dragging = null;
         }

         if (clickL && this.hovered != null && !this.hovered.isLocked()) {
            int[] p = this.hovered.pos(this.field_22789, this.field_22790);
            this.hovered.ensureState(this.field_22789, this.field_22790);
            this.dragging = this.hovered;
            this.dragDx = mx - p[0];
            this.dragDy = my - p[1];
         }

         if (this.dragging != null) {
            int[] st = this.dragging.ensureState(this.field_22789, this.field_22790);
            int nx = mx - this.dragDx;
            int ny = my - this.dragDy;
            if (Config.snapGrid) {
               int g = Math.max(1, Config.gridSize);
               nx = Math.round((float)nx / g) * g;
               ny = Math.round((float)ny / g) * g;
            }

            st[0] = Math.max(0, Math.min(this.field_22789 - 8, nx));
            st[1] = Math.max(0, Math.min(this.field_22790 - 8, ny));
         }

         if (clickR && this.hovered != null) {
            this.hovered.toggle();
         }

         if (clickM && this.hovered != null) {
            int[] st = this.hovered.ensureState(this.field_22789, this.field_22790);
            st[3] = st[3] == 1 ? 0 : 1;
         }

         if (keyR) {
            if (this.hovered != null) {
               this.hovered.resetState();
            } else {
               for (HudModule ex : this.elements) {
                  ex.resetState();
               }
            }
         }

         for (HudModule ex : this.elements) {
            int[] p = ex.pos(this.field_22789, this.field_22790);
            int ew = Math.max(24, ex.width());
            int eh = Math.max(10, ex.height());
            if (ex.isEnabled()) {
               ex.drawForEditor(ctx);
            }

            int baseCol = ex.isEnabled() ? -1996488705 : 1157627903;
            int col = ex == this.hovered ? Theme.ACCENT : baseCol;
            ctx.method_25294(p[0] - 1, p[1] - 1, p[0] + ew + 1, p[1], col);
            ctx.method_25294(p[0] - 1, p[1] + eh, p[0] + ew + 1, p[1] + eh + 1, col);
            ctx.method_25294(p[0] - 1, p[1], p[0], p[1] + eh, col);
            ctx.method_25294(p[0] + ew, p[1], p[0] + ew + 1, p[1] + eh, col);
            if (!ex.isEnabled()) {
               ctx.method_51433(mc.field_1772, ex.getName() + " (aus)", p[0] + 2, p[1] + 2, -1996488705, false);
            }

            if (ex.isLocked()) {
               ctx.method_51433(mc.field_1772, "L", p[0] + ew - 7, p[1] + 1, -11654, false);
            }
         }

         String help = "Ziehen: verschieben | Rechtsklick: ein/aus | Mausrad: Groesse | Mittelklick: sperren | R: reset | G: Raster "
            + (Config.snapGrid ? "an" : "aus");
         int helpW = Math.min(this.field_22789 - 24, mc.field_1772.method_1727(help) + 22);
         int helpX = (this.field_22789 - helpW) / 2;
         ctx.method_25294(helpX, this.field_22790 - 28, helpX + helpW, this.field_22790 - 8, Theme.PANEL);
         ctx.method_25294(helpX, this.field_22790 - 28, helpX + 2, this.field_22790 - 8, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, help, helpX + 10, this.field_22790 - 21, Theme.TEXT_DIM, false);
         if (this.hovered != null) {
            ctx.method_51433(
               mc.field_1772,
               this.hovered.getName() + (this.hovered.isScalable() ? "  x" + String.format("%.1f", this.hovered.scale()) : ""),
               mx + 8,
               my + 8,
               Theme.TEXT,
               true
            );
         }
      }
   }
}

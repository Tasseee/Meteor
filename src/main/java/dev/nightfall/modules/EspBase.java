package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_332;

public abstract class EspBase extends Module {
   public static int drawn;
   protected final NumberSetting range;
   protected final NumberSetting maxObjects = this.add(new NumberSetting("Max Objekte", 512.0, 4.0, 512.0, 4.0));
   protected final NumberSetting thickness = this.add(new NumberSetting("Linienstaerke", 4.0, 1.0, 4.0, 1.0));
   protected final NumberSetting opacity = this.add(new NumberSetting("Transparenz", 1.0, 0.2, 1.0, 0.05));
   protected final BoolSetting smoothing = this.add(new BoolSetting("Linien-Antialiasing", true));
   protected final BoolSetting distanceFade = this.add(new BoolSetting("Distanz-Fade", false));
   protected final NumberSetting fadeStart = this.add(new NumberSetting("Fade ab (Anteil)", 0.9, 0.1, 0.9, 0.05));
   protected final ModeSetting style = this.add(new ModeSetting("Box", "3D", "3D", "2D", "Aus"));
   protected final BoolSetting tracers = this.add(new BoolSetting("Tracer", false));
   protected final ModeSetting origin = this.add(new ModeSetting("Tracer-Start", "Mitte", "Mitte", "Unten"));
   protected final BoolSetting names = this.add(new BoolSetting("Name", true));
   protected final BoolSetting distance = this.add(new BoolSetting("Distanz", true));
   protected final ColorSetting color;
   private final double[] pt = new double[2];

   protected EspBase(String name, String desc, int defRange, int maxRange, int rgb) {
      super(name, desc, Category.VISUALS);
      this.range = this.add(new NumberSetting("Reichweite", maxRange, 8.0, maxRange, 8.0));
      this.color = this.add(new ColorSetting("Farbe", rgb));
   }

   protected int argb(int rgb) {
      return this.argb(rgb, 1.0);
   }

   private int argb(int rgb, double fade) {
      int alpha = (int)Math.max(0L, Math.min(255L, Math.round(this.opacity.get() * 255.0 * fade)));
      return Theme.withAlpha(rgb, alpha);
   }

   protected boolean capReached() {
      return drawn >= 2048;
   }

   protected void drawTarget(class_332 ctx, class_238 box, String label, int rgb) {
      this.drawTarget(ctx, box, label, rgb, 0.0, 0.0, 0.0);
   }

   protected void drawTarget(class_332 ctx, class_238 box, String label, int rgb, double offsetX, double offsetY, double offsetZ) {
      if (Projection.valid()) {
         BaseFinder baseFinder = ModuleManager.get(BaseFinder.class);
         if (baseFinder == null
            || !baseFinder.isFakeChunk(
               (int)Math.floor((box.field_1323 + box.field_1320) / 2.0 + offsetX) >> 4,
               (int)Math.floor((box.field_1321 + box.field_1324) / 2.0 + offsetZ) >> 4
            )) {
            double cx = (box.field_1323 + box.field_1320) / 2.0 + offsetX;
            double cy = (box.field_1322 + box.field_1325) / 2.0 + offsetY;
            double cz = (box.field_1321 + box.field_1324) / 2.0 + offsetZ;
            double fade = 1.0;
            if (this.distanceFade.get() && mc.field_1724 != null && this.range.get() > 0.0) {
               double dx = cx - Projection.eyeX();
               double dy = cy - Projection.eyeY();
               double dz = cz - Projection.eyeZ();
               double distanceToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);
               double start = this.fadeStart.get();
               double progress = (distanceToTarget / this.range.get() - start) / Math.max(0.01, 1.0 - start);
               fade = Math.max(0.0, Math.min(1.0, 1.0 - progress));
            }

            int col = this.argb(rgb, fade);
            boolean shown = false;
            if (!this.style.is("Aus") && fade > 0.005) {
               shown = Draw.box(ctx,
                  box.field_1323 + offsetX, box.field_1322 + offsetY, box.field_1321 + offsetZ,
                  box.field_1320 + offsetX, box.field_1325 + offsetY, box.field_1324 + offsetZ,
                  col, this.thickness.getInt(), this.style.is("3D"), this.smoothing.get());
            }

            boolean centerOk = Projection.project(cx, cy, cz, this.pt);
            if (shown && label != null && !label.isEmpty()) {
               this.drawCleanLabel(ctx, label, Draw.topX, Draw.topY - 11.0F);
            } else if (!shown && centerOk && label != null && !label.isEmpty()) {
               this.drawCleanLabel(ctx, label, (float)this.pt[0], (float)this.pt[1] - 5.0F);
            }

            if (this.tracers.get()) {
               float sx = Projection.width() / 2.0F;
               float sy = this.origin.is("Mitte") ? Projection.height() / 2.0F : Projection.height();
               if (centerOk) {
                  Draw.line(ctx, sx, sy, (float)this.pt[0], (float)this.pt[1], col, this.thickness.getInt(), this.smoothing.get());
                  shown = true;
               } else if (Projection.projectToEdge(cx, cy, cz, this.pt)) {
                  Draw.line(ctx, sx, sy, (float)this.pt[0], (float)this.pt[1], col, this.thickness.getInt());
                  shown = true;
               }
            }

            if (shown) {
               drawn++;
            }
         }
      }
   }

   private void drawCleanLabel(class_332 ctx, String label, float centerX, float topY) {
      int w = mc.field_1772.method_1727(label);
      int x = Math.round(centerX - w / 2.0F);
      int y = Math.round(topY);
      ctx.method_25294(x - 3, y - 2, x + w + 3, y + 10, -1190129125);
      ctx.method_51433(mc.field_1772, label, x, y, -854790, true);
   }

   protected String label(String name, double dist) {
      StringBuilder sb = new StringBuilder();
      if (this.names.get() && name != null) {
         sb.append(name);
      }

      if (this.distance.get()) {
         if (sb.length() > 0) {
            sb.append(' ');
         }

         sb.append((int)dist).append('m');
      }

      return sb.toString();
   }
}

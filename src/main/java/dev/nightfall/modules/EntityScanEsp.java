package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_332;

public abstract class EntityScanEsp extends EspBase {
   protected final BoolSetting health = this.add(new BoolSetting("Health", true));
   protected final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbare", false));
   private final List<class_1297> buf = new ArrayList<>();
   private static double sx;
   private static double sy;
   private static double sz;
   private static final Comparator<class_1297> BY_DIST = Comparator.comparingDouble(e -> e.method_5649(sx, sy, sz));

   protected EntityScanEsp(String n, String d, int r, int mr, int rgb) {
      super(n, d, r, mr, rgb);
   }

   protected abstract boolean accept(class_1297 var1);

   protected String nameOf(class_1297 e) {
      return e.method_5477().getString();
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 != null && mc.field_1724 != null && Projection.valid()) {
         sx = Projection.eyeX();
         sy = Projection.eyeY();
         sz = Projection.eyeZ();
         double r2 = this.range.get() * this.range.get();
         this.buf.clear();

         for (class_1297 e : mc.field_1687.method_18112()) {
            if (e != mc.field_1724 && this.accept(e) && !(e.method_5649(sx, sy, sz) > r2) && (!this.visibleOnly.get() || Projection.canSeeFromCamera(mc, e))) {
               this.buf.add(e);
            }
         }

         if (this.buf.size() > this.maxObjects.getInt()) {
            this.buf.sort(BY_DIST);
         }

         int n = Math.min(this.buf.size(), this.maxObjects.getInt());

         for (int i = 0; i < n && !this.capReached(); i++) {
            class_1297 ex = this.buf.get(i);
            double d = Math.sqrt(ex.method_5649(sx, sy, sz));
            String lb = this.label(this.nameOf(ex), d);
            if (this.health.get() && ex instanceof class_1309 le) {
               lb = lb + " " + Math.round(le.method_6032()) + "hp";
            }

            class_243 renderedPos = ex.method_30950(Projection.tickDelta());
            double offsetX = renderedPos.field_1352 - ex.method_23317();
            double offsetY = renderedPos.field_1351 - ex.method_23318();
            double offsetZ = renderedPos.field_1350 - ex.method_23321();
            this.drawTarget(ctx, ex.method_5829(), lb, this.color.rgb(), offsetX, offsetY, offsetZ);
         }
      }
   }
}

package dev.nightfall.modules;

import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2586;
import net.minecraft.class_2818;
import net.minecraft.class_332;
import net.minecraft.class_4184;

public abstract class BlockEntityEsp extends EspBase {
   private final List<class_2338> cache = new ArrayList<>();
   private final List<class_238> boxes = new ArrayList<>();
   private int timer;

   protected BlockEntityEsp(String n, String d, int r, int mr, int rgb) {
      super(n, d, r, mr, rgb);
   }

   protected abstract boolean accept(class_2586 var1);

   protected abstract String nameOf(class_2586 var1);

   protected boolean acceptChunk(class_2818 chunk) {
      return true;
   }

   @Override
   protected void onDisable() {
      this.cache.clear();
      this.boxes.clear();
   }

   @Override
   public void onWorldLeave() {
      this.cache.clear();
      this.boxes.clear();
      this.timer = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         if (this.timer-- <= 0) {
            this.timer = 10;
            this.cache.clear();
            this.boxes.clear();
            int r = this.range.getInt();
            class_4184 camera = mc.field_1773.method_19418();
            class_243 origin = camera != null && camera.method_19332() ? camera.method_71156() : mc.field_1724.method_33571();
            double px = origin.field_1352;
            double py = origin.field_1351;
            double pz = origin.field_1350;
            int pcx = (int)Math.floor(px) >> 4;
            int pcz = (int)Math.floor(pz) >> 4;
            int cr = (r >> 4) + 1;
            double r2 = (double)r * r;

            for (int dz = -cr; dz <= cr; dz++) {
               for (int dx = -cr; dx <= cr; dx++) {
                  class_2818 ch = mc.field_1687.method_2935().method_21730(pcx + dx, pcz + dz);
                  if (ch != null && this.acceptChunk(ch)) {
                     for (class_2586 be : ch.method_12214().values()) {
                        if (this.accept(be)) {
                           class_2338 bp = be.method_11016();
                           if (!(bp.method_40081(px, py, pz) > r2)) {
                              this.cache.add(bp.method_10062());
                              this.boxes.add(new class_238(bp));
                           }
                        }
                     }
                  }
               }
            }

            List<Integer> order = new ArrayList<>(this.cache.size());

            for (int i = 0; i < this.cache.size(); i++) {
               order.add(i);
            }

            order.sort(Comparator.comparingDouble(ix -> this.cache.get(ix).method_40081(px, py, pz)));
            if (!order.isEmpty()) {
               List<class_2338> sortedPositions = new ArrayList<>(this.cache.size());
               List<class_238> sortedBoxes = new ArrayList<>(this.boxes.size());

               for (int i : order) {
                  sortedPositions.add(this.cache.get(i));
                  sortedBoxes.add(this.boxes.get(i));
               }

               this.cache.clear();
               this.cache.addAll(sortedPositions);
               this.boxes.clear();
               this.boxes.addAll(sortedBoxes);
            }
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 != null && mc.field_1724 != null && Projection.valid()) {
         int n = this.cache.size();
         int shown = 0;

         for (int i = 0; i < n && shown < this.maxObjects.getInt() && !this.capReached(); i++) {
            class_2338 bp = this.cache.get(i);
            class_2586 be = mc.field_1687.method_8321(bp);
            if (be != null && this.accept(be)) {
               double d = Math.sqrt(bp.method_40081(Projection.eyeX(), Projection.eyeY(), Projection.eyeZ()));
               this.drawTarget(ctx, this.boxes.get(i), this.label(this.nameOf(be), d), this.color.rgb());
               shown++;
            }
         }
      }
   }
}

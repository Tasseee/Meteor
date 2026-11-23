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
   private final List<Target> targets = new ArrayList<>();
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
      this.targets.clear();
   }

   @Override
   public void onWorldLeave() {
      this.targets.clear();
      this.timer = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 == null || mc.field_1724 == null || this.timer-- > 0) {
         return;
      }

      this.timer = 10;
      this.targets.clear();
      int r = this.range.getInt();
      class_4184 camera = mc.field_1773.method_19418();
      class_243 origin = camera != null && camera.method_19332()
         ? camera.method_71156() : mc.field_1724.method_33571();
      double px = origin.field_1352;
      double py = origin.field_1351;
      double pz = origin.field_1350;
      int pcx = (int)Math.floor(px) >> 4;
      int pcz = (int)Math.floor(pz) >> 4;
      int cr = (r >> 4) + 1;
      double r2 = (double)r * r;

      for (int dz = -cr; dz <= cr; dz++) {
         for (int dx = -cr; dx <= cr; dx++) {
            class_2818 chunk = ChunkMemory.getAvailableChunk(pcx + dx, pcz + dz);
            if (chunk == null || !this.acceptChunk(chunk)) {
               continue;
            }

            for (class_2586 entity : chunk.method_12214().values()) {
               if (!this.accept(entity)) {
                  continue;
               }

               class_2338 pos = entity.method_11016();
               double distanceSquared = pos.method_40081(px, py, pz);
               if (distanceSquared <= r2) {
                  class_2338 stablePos = pos.method_10062();
                  this.targets.add(new Target(stablePos, new class_238(stablePos), entity, distanceSquared));
               }
            }
         }
      }

      this.targets.sort(Comparator.comparingDouble(Target::distanceSquared));
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 == null || mc.field_1724 == null || !Projection.valid()) {
         return;
      }

      int shown = 0;
      int max = this.maxObjects.getInt();
      for (Target target : this.targets) {
         if (shown >= max || this.capReached()) {
            break;
         }

         class_2586 entity = target.entity();
         if (entity != null && this.accept(entity)) {
            double distance = Math.sqrt(target.distanceSquared());
            this.drawTarget(ctx, target.box(), this.label(this.nameOf(entity), distance), this.color.rgb());
            shown++;
         }
      }
   }

   private record Target(class_2338 pos, class_238 box, class_2586 entity, double distanceSquared) {
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_238;
import net.minecraft.class_332;

public abstract class EntityScanEsp extends EspBase {
   protected final BoolSetting health = this.add(new BoolSetting("Health", true));
   protected final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbare", false));
   private final List<RenderTarget> buf = new ArrayList<>();
   private static final Comparator<RenderTarget> BY_DIST = Comparator.comparingDouble(RenderTarget::distanceSquared);

   protected EntityScanEsp(String n, String d, int r, int mr, int rgb) {
      super(n, d, r, mr, rgb);
   }

   protected abstract boolean accept(class_1297 var1);

   protected String nameOf(class_1297 e) {
      return e.method_5477().getString();
   }

   public void renderWorld(WorldRenderContext context) {
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) {
         return;
      }

      float tickDelta = Projection.tickDelta();
      class_243 camera = mc.field_1773.method_19418().method_71156();
      double cameraX = camera.field_1352;
      double cameraY = camera.field_1351;
      double cameraZ = camera.field_1350;
      double rangeSquared = this.range.get() * this.range.get();
      this.buf.clear();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity == mc.field_1724 || !this.accept(entity)) {
            continue;
         }

         double currentX = entity.method_23317();
         double currentY = entity.method_23318();
         double currentZ = entity.method_23321();
         double ix = entity.field_6038 + (currentX - entity.field_6038) * tickDelta;
         double iy = entity.field_5971 + (currentY - entity.field_5971) * tickDelta;
         double iz = entity.field_5989 + (currentZ - entity.field_5989) * tickDelta;

         class_238 currentBox = entity.method_5829();
         double dx = ix - currentX;
         double dy = iy - currentY;
         double dz = iz - currentZ;
         class_238 box = new class_238(
            currentBox.field_1323 + dx, currentBox.field_1322 + dy, currentBox.field_1321 + dz,
            currentBox.field_1320 + dx, currentBox.field_1325 + dy, currentBox.field_1324 + dz
         );

         double rx = ix - cameraX;
         double ry = iy - cameraY;
         double rz = iz - cameraZ;
         double distanceSquared = rx * rx + ry * ry + rz * rz;
         if (distanceSquared > rangeSquared) {
            continue;
         }

         if (this.visibleOnly.get() && !Projection.canSeeFromCamera(mc, entity, box)) {
            continue;
         }

         this.buf.add(new RenderTarget(entity, box, distanceSquared));
      }

      if (this.buf.size() > this.maxObjects.getInt()) {
         this.buf.sort(BY_DIST);
      }

      int count = Math.min(this.buf.size(), this.maxObjects.getInt());
      for (int i = 0; i < count && !this.capReached(); i++) {
         RenderTarget target = this.buf.get(i);
         if (this.style.is("3D")) {
            this.drawWorldBox(context, target.box(), this.color.rgb());
         }

         if (this.tracers.get()) {
            double tx = (target.box().field_1323 + target.box().field_1320) * 0.5;
            double ty = this.origin.is("Mitte")
               ? (target.box().field_1322 + target.box().field_1325) * 0.5
               : target.box().field_1322;
            double tz = (target.box().field_1321 + target.box().field_1324) * 0.5;
            this.drawWorldLine(context, cameraX, cameraY, cameraZ, tx, ty, tz, this.color.rgb());
         }
      }
   }

   private void drawWorldBox(WorldRenderContext context, class_238 box, int rgb) {
      this.drawWorldLine(context, box.field_1323, box.field_1322, box.field_1321, box.field_1320, box.field_1322, box.field_1321, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1325, box.field_1321, box.field_1320, box.field_1325, box.field_1321, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1322, box.field_1324, box.field_1320, box.field_1322, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1325, box.field_1324, box.field_1320, box.field_1325, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1322, box.field_1321, box.field_1323, box.field_1325, box.field_1321, rgb);
      this.drawWorldLine(context, box.field_1320, box.field_1322, box.field_1321, box.field_1320, box.field_1325, box.field_1321, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1322, box.field_1324, box.field_1323, box.field_1325, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1320, box.field_1322, box.field_1324, box.field_1320, box.field_1325, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1322, box.field_1321, box.field_1323, box.field_1322, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1320, box.field_1322, box.field_1321, box.field_1320, box.field_1322, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1323, box.field_1325, box.field_1321, box.field_1323, box.field_1325, box.field_1324, rgb);
      this.drawWorldLine(context, box.field_1320, box.field_1325, box.field_1321, box.field_1320, box.field_1325, box.field_1324, rgb);
   }

   private void drawWorldLine(WorldRenderContext context, double x0, double y0, double z0, double x1, double y1, double z1, int rgb) {
      class_4587 matrices = context.matrices();
      class_4588 vertices = context.consumers().method_73477(class_12249.method_76015());
      float r = (rgb >> 16 & 255) / 255.0F;
      float g = (rgb >> 8 & 255) / 255.0F;
      float b = (rgb & 255) / 255.0F;
      float a = this.opacity.get().floatValue();
      var matrix = matrices.method_23760().method_23761();
      vertices.method_22918(matrix, (float)x0, (float)y0, (float)z0).method_22915(r, g, b, a);
      vertices.method_22918(matrix, (float)x1, (float)y1, (float)z1).method_22915(r, g, b, a);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 != null && mc.field_1724 != null && Projection.valid()) {
         double cameraX = Projection.eyeX();
         double cameraY = Projection.eyeY();
         double cameraZ = Projection.eyeZ();
         double rangeSquared = this.range.get() * this.range.get();
         float tickDelta = Projection.tickDelta();
         this.buf.clear();

         for (class_1297 entity : mc.field_1687.method_18112()) {
            if (entity == mc.field_1724 || !this.accept(entity)) {
               continue;
            }

            // Interpolate directly from the render-frame positions. Never keep or reuse
            // screen coordinates from an earlier frame; world positions are projected anew
            // against the current camera every frame.
            double currentX = entity.method_23317();
            double currentY = entity.method_23318();
            double currentZ = entity.method_23321();
            double interpolatedX = entity.field_6038 + (currentX - entity.field_6038) * tickDelta;
            double interpolatedY = entity.field_5971 + (currentY - entity.field_5971) * tickDelta;
            double interpolatedZ = entity.field_5989 + (currentZ - entity.field_5989) * tickDelta;

            // Keep the hitbox in exactly the same interpolated world position as the ESP
            // center. This prevents boxes/lines from being calculated from different frames.
            class_238 currentBox = entity.method_5829();
            double dx = interpolatedX - currentX;
            double dy = interpolatedY - currentY;
            double dz = interpolatedZ - currentZ;
            class_238 interpolatedBox = new class_238(
               currentBox.field_1323 + dx,
               currentBox.field_1322 + dy,
               currentBox.field_1321 + dz,
               currentBox.field_1320 + dx,
               currentBox.field_1325 + dy,
               currentBox.field_1324 + dz
            );

            double rx = interpolatedX - cameraX;
            double ry = interpolatedY - cameraY;
            double rz = interpolatedZ - cameraZ;
            double distanceSquared = rx * rx + ry * ry + rz * rz;
            if (distanceSquared > rangeSquared) {
               continue;
            }

            if (this.visibleOnly.get() && !Projection.canSeeFromCamera(mc, entity, interpolatedBox)) {
               continue;
            }

            this.buf.add(new RenderTarget(entity, interpolatedBox, distanceSquared));
         }

         if (this.buf.size() > this.maxObjects.getInt()) {
            this.buf.sort(BY_DIST);
         }

         int count = Math.min(this.buf.size(), this.maxObjects.getInt());
         for (int i = 0; i < count && !this.capReached(); i++) {
            RenderTarget target = this.buf.get(i);
            class_1297 entity = target.entity();
            String label = this.label(this.nameOf(entity), Math.sqrt(target.distanceSquared()));
            if (this.health.get() && entity instanceof class_1309 living) {
               label = label + " " + Math.round(living.method_6032()) + "hp";
            }

            if (this.style.is("3D")) {
               this.drawLabelOnly(ctx, target.box(), label);
            } else {
               this.drawTarget(ctx, target.box(), label, this.color.rgb());
            }
         }
      }
   }

   private record RenderTarget(class_1297 entity, class_238 box, double distanceSquared) {
   }
}

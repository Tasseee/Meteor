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
   private final List<RenderTarget> buf = new ArrayList<>();
   private static final Comparator<RenderTarget> BY_DIST = Comparator.comparingDouble(RenderTarget::distanceSquared);

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

            this.buf.add(new RenderTarget(entity, interpolatedBox, distanceSquared, interpolatedX, interpolatedY, interpolatedZ));
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

            // The box and all other ESP primitives are drawn from this same render-frame
            // world position. Projection converts them to screen space during this frame.
            this.drawTarget(ctx, target.box(), label, this.color.rgb(), 0.0, 0.0, 0.0);
         }
      }
   }

   private record RenderTarget(class_1297 entity, class_238 box, double distanceSquared, double renderX, double renderY, double renderZ) {
   }
}

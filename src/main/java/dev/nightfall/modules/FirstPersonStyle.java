package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

public final class FirstPersonStyle extends Module {
   private final BoolSetting swapHands = this.add(new BoolSetting("Items linke/rechte Hand tauschen", false));
   private final ModeSetting pose = this.add(new ModeSetting("Handhaltung", "Anubis", "Vanilla", "Katana", "Heavy", "Floating", "Compact", "Anubis"));
   private final NumberSetting x = this.add(new NumberSetting("X-Position", 0.0, -0.8, 0.8, 0.01));
   private final NumberSetting y = this.add(new NumberSetting("Y-Position", -0.08, -0.8, 0.8, 0.01));
   private final NumberSetting z = this.add(new NumberSetting("Z-Position", -0.12, -0.8, 0.8, 0.01));
   private final NumberSetting pitch = this.add(new NumberSetting("X-Rotation", 0.0, -45.0, 45.0, 1.0));
   private final NumberSetting yaw = this.add(new NumberSetting("Y-Rotation", 0.0, -45.0, 45.0, 1.0));
   private final NumberSetting roll = this.add(new NumberSetting("Z-Rotation", 0.0, -90.0, 90.0, 1.0));
   private final NumberSetting scale = this.add(new NumberSetting("3D-Skalierung", 1.0, 0.65, 1.35, 0.01));

   public FirstPersonStyle() {
      super("ViewModel", "Arme und gehaltenes Item frei auf dem Bildschirm positionieren, drehen und skalieren.", Category.VISUALS);
   }

   public class_1799 swapRenderedStack(class_1799 original) {
      if (!this.swapHands.get() || mc.field_1724 == null || original == null) {
         return original;
      } else if (original == mc.field_1724.method_6047()) {
         return mc.field_1724.method_6079();
      } else {
         return original == mc.field_1724.method_6079() ? mc.field_1724.method_6047() : original;
      }
   }

   public void transform(class_4587 matrices, boolean leftHand) {
      float side = leftHand ? -1.0F : 1.0F;
      double tx = this.x.get();
      double ty = this.y.get();
      double tz = this.z.get();
      float rx = this.pitch.get().floatValue();
      float ry = this.yaw.get().floatValue();
      float rz = this.roll.get().floatValue();
      String poseName = this.pose.get();
      switch (poseName) {
         case "Katana":
            tx += 0.06 * side;
            ty -= 0.035;
            rz -= 22.0F * side;
            ry += 12.0F * side;
            break;
         case "Heavy":
            tx -= 0.025 * side;
            ty += 0.035;
            rz += 15.0F * side;
            rx += 12.0F;
            break;
         case "Floating":
            ty -= 0.07;
            rz += 8.0F * side;
            break;
         case "Compact":
            tx += 0.045 * side;
            ty += 0.025;
            break;
         case "Anubis":
            tx += 0.035 * side;
            ty -= 0.045;
            tz -= 0.075;
            rx -= 12.0F;
            ry += 18.0F * side;
            rz += 10.0F * side;
      }

      matrices.method_22904(tx * side, ty, tz);
      matrices.method_22907(
         new Quaternionf().rotateX((float)Math.toRadians(rx)).rotateY((float)Math.toRadians(ry * side)).rotateZ((float)Math.toRadians(rz * side))
      );
      float itemScale = this.scale.get().floatValue();
      matrices.method_22905(itemScale, itemScale, itemScale);
   }
}

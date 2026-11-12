package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_4587;
import org.joml.Quaternionf;

public final class WeaponAnimations extends Module {
   private final ModeSetting swing = this.add(new ModeSetting("Swing-Stil", "Vanilla+", "Vanilla+", "Punch", "Sweep", "Spin", "Snap"));
   private final BoolSetting inspect = this.add(new BoolSetting("Inspektion aktiv", false));
   private final NumberSetting inspectSpeed = this.add(new NumberSetting("Inspektionsgeschwindigkeit", 1.0, 0.25, 2.5, 0.05));
   private final NumberSetting swingPower = this.add(new NumberSetting("Swing-Stärke", 1.0, 0.25, 2.0, 0.05));

   public WeaponAnimations() {
      super("WeaponAnimations", "Waffeninspektion und lokale First-Person-Swing-Animationen.", Category.VISUALS);
   }

   public void transform(class_4587 matrices, float swingProgress, boolean leftHand) {
      float side = leftHand ? -1.0F : 1.0F;
      if (this.inspect.get()) {
         double phase = System.currentTimeMillis() * 0.0015 * this.inspectSpeed.get() % (Math.PI * 2);
         float lift = (float)(Math.sin(phase) * 0.045);
         matrices.method_22904(0.0, -0.11 + lift, -0.08);
         matrices.method_22907(
            new Quaternionf()
               .rotateX((float)Math.toRadians(-28.0 + Math.sin(phase) * 8.0))
               .rotateY((float)Math.toRadians(side * (32.0 + Math.cos(phase) * 14.0)))
               .rotateZ((float)Math.toRadians(side * 24.0F))
         );
      } else if (!(swingProgress <= 0.0F)) {
         float p = Math.max(0.0F, Math.min(1.0F, swingProgress));
         float wave = (float)Math.sin(Math.PI * p) * this.swingPower.get().floatValue();
         String lift = this.swing.get();
         switch (lift) {
            case "Punch":
               matrices.method_22904(side * -0.08 * wave, 0.045 * wave, -0.06 * wave);
               matrices.method_22907(new Quaternionf().rotateX((float)Math.toRadians(-22.0F * wave)).rotateZ((float)Math.toRadians(side * -13.0F * wave)));
               break;
            case "Sweep":
               matrices.method_22904(side * -0.13 * wave, -0.025 * wave, 0.02 * wave);
               matrices.method_22907(new Quaternionf().rotateY((float)Math.toRadians(side * 48.0F * wave)).rotateZ((float)Math.toRadians(side * 18.0F * wave)));
               break;
            case "Spin":
               matrices.method_22907(new Quaternionf().rotateZ((float)Math.toRadians(side * 360.0F * p * this.swingPower.get().floatValue())));
               break;
            case "Snap":
               float snap = (float)Math.pow(p, 0.35);
               matrices.method_22904(side * -0.04 * snap, 0.025 * wave, -0.09 * snap);
               matrices.method_22907(new Quaternionf().rotateX((float)Math.toRadians(-38.0F * snap)).rotateY((float)Math.toRadians(side * 20.0F * snap)));
               break;
            default:
               matrices.method_22904(0.0, 0.012 * wave, 0.0);
         }
      }
   }
}

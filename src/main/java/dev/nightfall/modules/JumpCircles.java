package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_2394;
import net.minecraft.class_2398;

public final class JumpCircles extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Effekt", "Cloud", "Cloud", "Enchant", "Soul", "Flamme", "Herzen"));
   private final NumberSetting radius = this.add(new NumberSetting("Radius", 0.8, 0.3, 1.8, 0.1));
   private boolean airborne;

   public JumpCircles() {
      super("JumpCircles", "Partikelkreis beim Landen nach einem Sprung (lokal).", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.airborne = false;
   }

   @Override
   public void onWorldLeave() {
      this.airborne = false;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (!mc.field_1724.method_24828()) {
            this.airborne = true;
         } else if (this.airborne) {
            this.airborne = false;
            String styleName = this.style.get();

            class_2394 p = switch (styleName) {
               case "Enchant" -> class_2398.field_11215;
               case "Soul" -> class_2398.field_23114;
               case "Flamme" -> class_2398.field_11240;
               case "Herzen" -> class_2398.field_11201;
               default -> class_2398.field_11204;
            };

            for (int particleIndex = 0; particleIndex < 18; particleIndex++) {
               double angle = (Math.PI * 2) * particleIndex / 18.0;
               double x = mc.field_1724.method_23317() + Math.cos(angle) * this.radius.get();
               double z = mc.field_1724.method_23321() + Math.sin(angle) * this.radius.get();
               mc.field_1713.method_3056(p, x, mc.field_1724.method_23318() + 0.08, z, Math.cos(angle) * 0.025, 0.025, Math.sin(angle) * 0.025);
            }
         }
      }
   }
}

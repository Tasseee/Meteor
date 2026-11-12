package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_2394;
import net.minecraft.class_2398;

public final class ParticleTrail extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Partikel", "Enchant", "Enchant", "Soul", "Portal", "Herzen", "Wolke", "Flamme"));
   private final NumberSetting density = this.add(new NumberSetting("Dichte", 3.0, 1.0, 8.0, 1.0));
   private final BoolSetting onlyMoving = this.add(new BoolSetting("Nur beim Bewegen", true));
   private int ticks;

   public ParticleTrail() {
      super("ParticleTrail", "Kosmetische Partikelspur hinter dir (nur lokal).", Category.VISUALS);
   }

   @Override
   public void onWorldLeave() {
      this.ticks = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (++this.ticks % 2 == 0) {
            if (!this.onlyMoving.get() || !(mc.field_1724.method_18798().method_37268() < 0.0025)) {
               class_2394 particle = this.particle();

               for (int i = 0; i < this.density.getInt(); i++) {
                  double x = mc.field_1724.method_23317() + (Math.random() - 0.5) * 0.65;
                  double y = mc.field_1724.method_23318() + 0.15 + Math.random() * 1.25;
                  double z = mc.field_1724.method_23321() + (Math.random() - 0.5) * 0.65;
                  mc.field_1713.method_3056(particle, x, y, z, 0.0, 0.015, 0.0);
               }
            }
         }
      }
   }

   private class_2394 particle() {
      String var1 = this.style.get();

      return switch (var1) {
         case "Soul" -> class_2398.field_23114;
         case "Portal" -> class_2398.field_11214;
         case "Herzen" -> class_2398.field_11201;
         case "Wolke" -> class_2398.field_11204;
         case "Flamme" -> class_2398.field_11240;
         default -> class_2398.field_11215;
      };
   }
}

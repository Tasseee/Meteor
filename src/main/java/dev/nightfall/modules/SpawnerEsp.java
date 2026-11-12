package dev.nightfall.modules;

import net.minecraft.class_2586;
import net.minecraft.class_2636;

public class SpawnerEsp extends BlockEntityEsp {
   public SpawnerEsp() {
      super("SpawnerESP", "Markiert Spawner.", 128, 256, 15485081);
   }

   @Override
   protected boolean accept(class_2586 be) {
      return be instanceof class_2636;
   }

   @Override
   protected String nameOf(class_2586 be) {
      return "Spawner";
   }
}

package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_2586;
import net.minecraft.class_2595;
import net.minecraft.class_2609;
import net.minecraft.class_2611;
import net.minecraft.class_2614;
import net.minecraft.class_2627;
import net.minecraft.class_3719;

public class StorageEsp extends BlockEntityEsp {
   private final BoolSetting chests = this.add(new BoolSetting("Chests", true));
   private final BoolSetting barrels = this.add(new BoolSetting("Barrels", true));
   private final BoolSetting shulkers = this.add(new BoolSetting("Shulker", true));
   private final BoolSetting ender = this.add(new BoolSetting("Ender Chest", false));
   private final BoolSetting hoppers = this.add(new BoolSetting("Hopper", false));
   private final BoolSetting furnaces = this.add(new BoolSetting("Furnace", false));

   public StorageEsp() {
      super("StorageESP", "Markiert Kisten, Fasser, Shulker u. a.", 96, 256, 16096779);
   }

   @Override
   protected boolean accept(class_2586 be) {
      if (be instanceof class_2595) {
         return this.chests.get();
      } else if (be instanceof class_3719) {
         return this.barrels.get();
      } else if (be instanceof class_2627) {
         return this.shulkers.get();
      } else if (be instanceof class_2611) {
         return this.ender.get();
      } else if (be instanceof class_2614) {
         return this.hoppers.get();
      } else {
         return be instanceof class_2609 ? this.furnaces.get() : false;
      }
   }

   @Override
   protected String nameOf(class_2586 be) {
      if (be instanceof class_2595) {
         return "Chest";
      } else if (be instanceof class_3719) {
         return "Barrel";
      } else if (be instanceof class_2627) {
         return "Shulker";
      } else if (be instanceof class_2611) {
         return "Ender";
      } else {
         return be instanceof class_2614 ? "Hopper" : "Furnace";
      }
   }
}

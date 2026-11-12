package dev.nightfall.modules;

import net.minecraft.class_1297;
import net.minecraft.class_1542;

public class ItemEsp extends EntityScanEsp {
   public ItemEsp() {
      super("ItemESP", "Zeigt geladene gedroppte Items mit Name, Anzahl und ESP-Markierung.", 512, 512, 2278750);
   }

   @Override
   protected boolean accept(class_1297 e) {
      return e instanceof class_1542;
   }

   @Override
   protected String nameOf(class_1297 e) {
      class_1542 ie = (class_1542)e;
      return ie.method_6983().method_7964().getString() + " x" + ie.method_6983().method_7947();
   }
}

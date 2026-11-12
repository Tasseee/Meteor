package dev.nightfall.util;

import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;
import net.minecraft.class_746;

public final class InvUtil {
   private InvUtil() {
   }

   public static int findHotbar(class_1792 item) {
      class_746 p = class_310.method_1551().field_1724;
      if (p == null) {
         return -1;
      } else {
         for (int i = 0; i < 9; i++) {
            if (p.method_31548().method_5438(i).method_31574(item)) {
               return i;
            }
         }

         return -1;
      }
   }

   public static int findInventory(class_1792 item) {
      class_746 p = class_310.method_1551().field_1724;
      if (p == null) {
         return -1;
      } else {
         for (int i = 0; i < 36; i++) {
            if (p.method_31548().method_5438(i).method_31574(item)) {
               return i;
            }
         }

         return -1;
      }
   }

   public static int ensureHotbar(class_1792 item) {
      class_310 client = class_310.method_1551();
      class_746 p = client.field_1724;
      if (p != null && client.field_1761 != null && p.field_7512 == p.field_7498) {
         int existing = findHotbar(item);
         if (existing >= 0) {
            return existing;
         } else {
            int source = findInventory(item);
            if (source < 9) {
               return -1;
            } else {
               int destination = -1;

               for (int i = 0; i < 9; i++) {
                  if (p.method_31548().method_5438(i).method_7960()) {
                     destination = i;
                     break;
                  }
               }

               if (destination < 0) {
                  int selected = p.method_31548().method_67532();

                  for (int ix = 0; ix < 9; ix++) {
                     if (ix != selected) {
                        destination = ix;
                        break;
                     }
                  }
               }

               if (destination < 0) {
                  destination = p.method_31548().method_67532();
               }

               client.field_1761.method_2906(p.field_7498.field_7763, source, destination, class_1713.field_7791, p);
               return destination;
            }
         }
      } else {
         return -1;
      }
   }

   public static int findBestSword() {
      class_1792[] priority = new class_1792[]{
         class_1802.field_22022, class_1802.field_8802, class_1802.field_8371, class_1802.field_8528, class_1802.field_8845, class_1802.field_8091
      };

      for (class_1792 item : priority) {
         int slot = ensureHotbar(item);
         if (slot >= 0) {
            return slot;
         }
      }

      return -1;
   }

   public static class_1799 hotbarStack(int slot) {
      class_746 p = class_310.method_1551().field_1724;
      return p != null && slot >= 0 && slot <= 8 ? p.method_31548().method_5438(slot) : class_1799.field_8037;
   }

   public static int selected() {
      class_746 p = class_310.method_1551().field_1724;
      return p == null ? 0 : p.method_31548().method_67532();
   }

   public static boolean select(int slot) {
      class_746 p = class_310.method_1551().field_1724;
      if (p != null && slot >= 0 && slot <= 8) {
         if (p.method_31548().method_67532() != slot) {
            p.method_31548().method_61496(slot);
         }

         return true;
      } else {
         return false;
      }
   }

   public static boolean swapWithOffhand(int index) {
      class_310 client = class_310.method_1551();
      class_746 player = client.field_1724;
      if (player != null && client.field_1761 != null && player.field_7512 == player.field_7498 && index >= 0 && index <= 35) {
         int slotId = index < 9 ? 36 + index : index;
         client.field_1761.method_2906(player.field_7498.field_7763, slotId, 40, class_1713.field_7791, player);
         return true;
      } else {
         return false;
      }
   }
}

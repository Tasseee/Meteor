package dev.nightfall.util;

import java.util.Locale;
import java.util.function.IntConsumer;
import org.lwjgl.glfw.GLFW;

public final class Keys {
   private Keys() {
   }

   public static boolean down(long h, int key) {
      return key >= 0 && GLFW.glfwGetKey(h, key) == 1;
   }

   public static String name(int k) {
      if (k < 0) {
         return "-";
      } else if (k >= 290 && k <= 301) {
         return "F" + (k - 290 + 1);
      } else {
         return switch (k) {
            case 32 -> "SPACE";
            case 258 -> "TAB";
            case 262 -> "RIGHT";
            case 263 -> "LEFT";
            case 264 -> "DOWN";
            case 265 -> "UP";
            case 340 -> "LSHIFT";
            case 341 -> "LCTRL";
            case 342 -> "LALT";
            case 344 -> "RSHIFT";
            case 345 -> "RCTRL";
            case 346 -> "RALT";
            default -> {
               String n = GLFW.glfwGetKeyName(k, 0);
               yield n != null ? n.toUpperCase(Locale.ROOT) : "K" + k;
            }
         };
      }
   }

   public static final class Poller {
      private final boolean[] prev = new boolean[349];

      public void sync(long h) {
         for (int k = 32; k <= 348; k++) {
            this.prev[k] = GLFW.glfwGetKey(h, k) == 1;
         }
      }

      public void poll(long h, IntConsumer onPress) {
         for (int k = 32; k <= 348; k++) {
            boolean d = GLFW.glfwGetKey(h, k) == 1;
            if (d && !this.prev[k]) {
               onPress.accept(k);
            }

            this.prev[k] = d;
         }
      }
   }
}

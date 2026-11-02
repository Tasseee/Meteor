package dev.nightfall.util;

import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.IntConsumer;

public final class Keys {
    private Keys() {}

    /** Erkennt neu gedrueckte Tasten per GLFW-Polling (unabhaengig von Screen-Signaturen). */
    public static final class Poller {
        private final boolean[] prev = new boolean[GLFW.GLFW_KEY_LAST + 1];

        public void sync(long h) {
            for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++)
                prev[k] = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
        }
        public void poll(long h, IntConsumer onPress) {
            for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
                boolean d = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
                if (d && !prev[k]) onPress.accept(k);
                prev[k] = d;
            }
        }
    }

    public static boolean down(long h, int key) { return key >= 0 && GLFW.glfwGetKey(h, key) == GLFW.GLFW_PRESS; }

    public static String name(int k) {
        if (k < 0) return "-";
        if (k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F12) return "F" + (k - GLFW.GLFW_KEY_F1 + 1);
        return switch (k) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            default -> {
                String n = GLFW.glfwGetKeyName(k, 0);
                yield n != null ? n.toUpperCase(Locale.ROOT) : "K" + k;
            }
        };
    }
}

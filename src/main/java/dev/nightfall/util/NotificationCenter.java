package dev.nightfall.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Small client-side toast queue shared by modules. */
public final class NotificationCenter {
    public static final long DEFAULT_DURATION_MS = 3_500L;
    private static final int MAX_QUEUE = 12;
    private static final ArrayDeque<Toast> QUEUE = new ArrayDeque<>();

    private NotificationCenter() {}

    public static synchronized void push(String title, String message) {
        push(title, message, DEFAULT_DURATION_MS, Theme.ACCENT);
    }

    public static synchronized void push(String title, String message, long durationMs, int color) {
        long now = System.currentTimeMillis();
        QUEUE.removeIf(t -> t.expiresAt <= now);
        if (QUEUE.size() >= MAX_QUEUE) QUEUE.removeFirst();
        QUEUE.addLast(new Toast(title, message, now, now + Math.max(750L, durationMs), color));
    }

    public static synchronized List<Toast> active(long now) {
        QUEUE.removeIf(t -> t.expiresAt <= now);
        return new ArrayList<>(QUEUE);
    }

    public static final class Toast {
        public final String title;
        public final String message;
        public final long createdAt;
        public final long expiresAt;
        public final int color;

        private Toast(String title, String message, long createdAt, long expiresAt, int color) {
            this.title = title;
            this.message = message;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.color = color;
        }

        public float progress(long now) {
            long duration = Math.max(1L, expiresAt - createdAt);
            return Math.max(0f, Math.min(1f, (expiresAt - now) / (float) duration));
        }
    }
}

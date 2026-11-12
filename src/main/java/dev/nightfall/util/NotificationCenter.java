package dev.nightfall.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class NotificationCenter {
   public static final long DEFAULT_DURATION_MS = 3500L;
   private static final int MAX_QUEUE = 12;
   private static final ArrayDeque<NotificationCenter.Toast> QUEUE = new ArrayDeque<>();

   private NotificationCenter() {
   }

   public static synchronized void push(String title, String message) {
      push(title, message, 3500L, Theme.ACCENT);
   }

   public static synchronized void push(String title, String message, long durationMs, int color) {
      long now = System.currentTimeMillis();
      QUEUE.removeIf(t -> t.expiresAt <= now);
      if (QUEUE.size() >= 12) {
         QUEUE.removeFirst();
      }

      QUEUE.addLast(new NotificationCenter.Toast(title, message, now, now + Math.max(750L, durationMs), color));
   }

   public static synchronized List<NotificationCenter.Toast> active(long now) {
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
         long duration = Math.max(1L, this.expiresAt - this.createdAt);
         return Math.max(0.0F, Math.min(1.0F, (float)(this.expiresAt - now) / (float)duration));
      }
   }
}

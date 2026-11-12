package dev.nightfall.scanner;

import dev.nightfall.util.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ChunkFavorites {
   private static final Set<Long> FAVORITES = new HashSet<>();
   private static String loadedContext = "";

   private ChunkFavorites() {
   }

   private static long key(int x, int z) {
      return (long)x << 32 ^ z & 4294967295L;
   }

   private static String context() {
      return Storage.server() + "_" + Storage.dim();
   }

   private static Path file(String c) {
      return Storage.dir().resolve("chunk_favorites_" + c.replaceAll("[^A-Za-z0-9._-]", "_") + ".txt");
   }

   private static synchronized void ensureLoaded() {
      String c = context();
      if (!c.equals(loadedContext)) {
         FAVORITES.clear();
         loadedContext = c;
         Path p = file(c);
         if (Files.exists(p)) {
            try {
               for (String line : Files.readAllLines(p)) {
                  String[] a = line.trim().split(",", 2);
                  if (a.length == 2) {
                     try {
                        FAVORITES.add(key(Integer.parseInt(a[0]), Integer.parseInt(a[1])));
                     } catch (NumberFormatException var6) {
                     }
                  }
               }
            } catch (IOException var7) {
            }
         }
      }
   }

   public static synchronized boolean isFavorite(int x, int z) {
      ensureLoaded();
      return FAVORITES.contains(key(x, z));
   }

   public static synchronized boolean toggle(int x, int z) {
      ensureLoaded();
      long v = key(x, z);
      boolean added;
      if (FAVORITES.add(v)) {
         added = true;
      } else {
         FAVORITES.remove(v);
         added = false;
      }

      StringBuilder s = new StringBuilder();

      for (long k : FAVORITES) {
         s.append((int)(k >> 32)).append(',').append((int)k).append('\n');
      }

      try {
         Files.writeString(file(loadedContext), s.toString());
      } catch (IOException var9) {
      }

      return added;
   }
}

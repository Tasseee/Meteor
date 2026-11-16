package dev.nightfall.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Module;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Local-only module usage totals. No player, server, world, or gameplay data is recorded.
 */
public final class ModuleUsageStats {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type DATA_TYPE = new TypeToken<Map<String, UsageRecord>>() {}.getType();
   private static final Path FILE = Storage.dir().resolve("module-usage.json");
   private static final long SAVE_INTERVAL_MS = 15000L;
   private static final Map<String, UsageRecord> RECORDS = new HashMap<>();
   private static final Map<String, Long> ACTIVE_SINCE = new HashMap<>();
   private static boolean dirty;
   private static boolean tracking;
   private static long lastSaveAt;

   private ModuleUsageStats() {
   }

   public static void load() {
      RECORDS.clear();
      ACTIVE_SINCE.clear();
      dirty = false;
      tracking = false;
      lastSaveAt = System.currentTimeMillis();
      if (!Files.exists(FILE)) {
         return;
      }

      try {
         Map<String, UsageRecord> loaded = GSON.fromJson(Files.readString(FILE), DATA_TYPE);
         if (loaded != null) {
            for (Map.Entry<String, UsageRecord> entry : loaded.entrySet()) {
               String name = entry.getKey();
               UsageRecord record = entry.getValue();
               if (name != null && !name.isBlank() && record != null) {
                  UsageRecord safe = new UsageRecord();
                  safe.activations = Math.max(0L, record.activations);
                  safe.activeMillis = Math.max(0L, record.activeMillis);
                  RECORDS.put(name, safe);
               }
            }
         }
      } catch (Exception exception) {
         Nightfall.LOGGER.error("Modul-Nutzungsdaten konnten nicht gelesen werden: {}", FILE, exception);
      }
   }

   public static void startTracking(Iterable<Module> modules) {
      tracking = true;
      for (Module module : modules) {
         if (module != null && module.isEnabled()) {
            onEnabled(module.getName());
         }
      }
   }

   public static void onEnabled(String moduleName) {
      if (!tracking || moduleName == null || moduleName.isBlank() || ACTIVE_SINCE.containsKey(moduleName)) {
         return;
      }

      UsageRecord record = RECORDS.computeIfAbsent(moduleName, ignored -> new UsageRecord());
      if (record.activations < Long.MAX_VALUE) {
         record.activations++;
      }
      ACTIVE_SINCE.put(moduleName, System.nanoTime());
      dirty = true;
   }

   public static void onDisabled(String moduleName) {
      if (!tracking) {
         return;
      }
      Long startedAt = ACTIVE_SINCE.remove(moduleName);
      if (startedAt == null) {
         return;
      }

      UsageRecord record = RECORDS.computeIfAbsent(moduleName, ignored -> new UsageRecord());
      long elapsed = elapsedMillis(startedAt);
      record.activeMillis = saturatingAdd(record.activeMillis, elapsed);
      dirty = true;
   }

   public static long activations(String moduleName) {
      UsageRecord record = RECORDS.get(moduleName);
      return record == null ? 0L : record.activations;
   }

   public static long activeMillis(String moduleName) {
      UsageRecord record = RECORDS.get(moduleName);
      long total = record == null ? 0L : record.activeMillis;
      Long startedAt = ACTIVE_SINCE.get(moduleName);
      return startedAt == null ? total : saturatingAdd(total, elapsedMillis(startedAt));
   }

   public static String summary(String moduleName) {
      long count = activations(moduleName);
      long millis = activeMillis(moduleName);
      return count + "x · " + formatDuration(millis);
   }

   public static String formatDuration(long millis) {
      long seconds = Math.max(0L, millis) / 1000L;
      if (seconds < 60L) {
         return seconds + "s";
      }

      long minutes = seconds / 60L;
      if (minutes < 60L) {
         return minutes + "m";
      }

      long hours = minutes / 60L;
      long remainingMinutes = minutes % 60L;
      if (hours < 24L) {
         return hours + "h " + remainingMinutes + "m";
      }

      long days = hours / 24L;
      return days + "d " + (hours % 24L) + "h";
   }

   public static void tick() {
      long now = System.currentTimeMillis();
      if ((dirty || !ACTIVE_SINCE.isEmpty()) && now - lastSaveAt >= SAVE_INTERVAL_MS) {
         save();
      }
   }

   public static void save() {
      Map<String, UsageRecord> snapshot = new HashMap<>();
      for (Map.Entry<String, UsageRecord> entry : RECORDS.entrySet()) {
         UsageRecord copy = new UsageRecord();
         copy.activations = entry.getValue().activations;
         copy.activeMillis = entry.getValue().activeMillis;
         Long startedAt = ACTIVE_SINCE.get(entry.getKey());
         if (startedAt != null) {
            copy.activeMillis = saturatingAdd(copy.activeMillis, elapsedMillis(startedAt));
         }
         snapshot.put(entry.getKey(), copy);
      }

      Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
      try {
         Files.createDirectories(FILE.getParent());
         Files.writeString(temp, GSON.toJson(snapshot));
         try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (IOException atomicMoveError) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
         }
         dirty = false;
         lastSaveAt = System.currentTimeMillis();
      } catch (IOException exception) {
         Nightfall.LOGGER.error("Modul-Nutzungsdaten konnten nicht gespeichert werden: {}", FILE, exception);
      }
   }

   private static long elapsedMillis(long startedAtNanos) {
      return Math.max(0L, System.nanoTime() - startedAtNanos) / 1000000L;
   }

   private static long saturatingAdd(long left, long right) {
      if (right > 0L && left > Long.MAX_VALUE - right) {
         return Long.MAX_VALUE;
      }
      return left + right;
   }

   private static final class UsageRecord {
      long activations;
      long activeMillis;
   }
}

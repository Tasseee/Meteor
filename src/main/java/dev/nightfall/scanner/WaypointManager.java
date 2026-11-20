package dev.nightfall.scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class WaypointManager {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type LIST = (new TypeToken<List<Waypoint>>() {}).getType();
   private static final List<Waypoint> LIST_ = new ArrayList<>();
   private static String loadedFor;

   private WaypointManager() {
   }

   private static Path file() {
      return Storage.dir().resolve("waypoints_" + Storage.server() + ".json");
   }

   public static List<Waypoint> all() {
      String s = Storage.server();
      if (!s.equals(loadedFor)) {
         load();
         loadedFor = s;
      }

      return LIST_;
   }

   private static void load() {
      LIST_.clear();
      Path f = file();
      if (Files.exists(f)) {
         try {
            List<Waypoint> in = (List<Waypoint>)GSON.fromJson(Files.readString(f), LIST);
            if (in != null) {
               int skipped = 0;
               for (Waypoint waypoint : in) {
                  if (waypoint == null
                     || waypoint.name == null
                     || waypoint.name.isBlank()
                     || waypoint.dim == null
                     || waypoint.dim.isBlank()) {
                     skipped++;
                     continue;
                  }

                  if (waypoint.category == null || waypoint.category.isBlank()) {
                     waypoint.category = "Default";
                  }
                  LIST_.add(waypoint);
               }

               if (skipped > 0) {
                  Nightfall.LOGGER.warn("Waypoints uebersprungen: {} ungueltige Eintraege in {}", skipped, f);
               }
            }
         } catch (Exception var2) {
            Nightfall.LOGGER.error("Waypoints defekt: {}", f, var2);
         }
      }
   }

   public static void save() {
      all();

      Path target = file();
      Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
      try {
         Path parent = target.getParent();
         if (parent != null) Files.createDirectories(parent);
         Files.writeString(temporary, GSON.toJson(LIST_));
         try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException failure) {
         Nightfall.LOGGER.error("Waypoints speichern fehlgeschlagen", failure);
      } finally {
         try {
            Files.deleteIfExists(temporary);
         } catch (IOException cleanupFailure) {
            Nightfall.LOGGER.warn("Temporäre Waypoint-Datei konnte nicht entfernt werden: {}", temporary, cleanupFailure);
         }
      }
   }

   public static Waypoint add(String name, String category, int x, int y, int z) {
      List<Waypoint> l = all();
      Waypoint w = new Waypoint();
      w.name = name;
      w.category = category == null ? "Default" : category;
      w.dim = Storage.dim();
      w.x = x;
      w.y = y;
      w.z = z;
      w.color = Theme.PALETTE[l.size() % Theme.PALETTE.length];
      l.add(w);
      save();
      return w;
   }

   public static boolean remove(String name) {
      boolean r = all().removeIf(w -> w.name.equalsIgnoreCase(name));
      if (r) {
         save();
      }

      return r;
   }

   public static void remove(Waypoint w) {
      if (all().remove(w)) {
         save();
      }
   }

   public static int removeCategory(String category) {
      int removed = all().size();
      all().removeIf(w -> category != null && category.equalsIgnoreCase(w.category));
      removed -= all().size();
      if (removed > 0) {
         save();
      }
      return removed;
   }
}

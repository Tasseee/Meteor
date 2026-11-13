package dev.nightfall.scanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
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
               for (Waypoint waypoint : in) {
                  if (waypoint == null) continue;
                  waypoint.name = waypoint.name == null || waypoint.name.isBlank() ? "Waypoint" : waypoint.name.trim();
                  waypoint.category = waypoint.category == null || waypoint.category.isBlank() ? "Default" : waypoint.category.trim();
                  waypoint.dim = waypoint.dim == null || waypoint.dim.isBlank() ? "unknown" : waypoint.dim.trim();
                  LIST_.add(waypoint);
               }
            }
         } catch (Exception var2) {
            Nightfall.LOGGER.error("Waypoints defekt: {}", f, var2);
         }
      }
   }

   public static void save() {
      all();

      try {
         Files.writeString(file(), GSON.toJson(LIST_));
      } catch (IOException var1) {
         Nightfall.LOGGER.error("Waypoints speichern fehlgeschlagen", var1);
      }
   }

   public static Waypoint add(String name, String category, int x, int y, int z) {
      List<Waypoint> l = all();
      Waypoint w = new Waypoint();
      w.name = name == null || name.isBlank() ? "Waypoint" : name.trim();
      w.category = category == null || category.isBlank() ? "Default" : category.trim();
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
      if (name == null || name.isBlank()) return false;
      String normalizedName = name.trim();
      boolean r = all().removeIf(w -> w != null && w.name != null && w.name.equalsIgnoreCase(normalizedName));
      if (r) {
         save();
      }

      return r;
   }

   public static void remove(Waypoint w) {
      if (w != null && all().remove(w)) {
         save();
      }
   }
}

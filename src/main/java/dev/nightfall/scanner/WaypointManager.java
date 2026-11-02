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

/** Waypoints je Server, dauerhaft in config/nightfall/waypoints_<server>.json. */
public final class WaypointManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<List<Waypoint>>() {}.getType();
    private static final List<Waypoint> LIST_ = new ArrayList<>();
    private static String loadedFor;

    private WaypointManager() {}

    private static Path file() { return Storage.dir().resolve("waypoints_" + Storage.server() + ".json"); }

    public static List<Waypoint> all() {
        String s = Storage.server();
        if (!s.equals(loadedFor)) { load(); loadedFor = s; }
        return LIST_;
    }

    private static void load() {
        LIST_.clear();
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            List<Waypoint> in = GSON.fromJson(Files.readString(f), LIST);
            if (in != null) LIST_.addAll(in);
        } catch (Exception e) { Nightfall.LOGGER.error("Waypoints defekt: {}", f, e); }
    }

    public static void save() {
        all();
        try { Files.writeString(file(), GSON.toJson(LIST_)); }
        catch (IOException e) { Nightfall.LOGGER.error("Waypoints speichern fehlgeschlagen", e); }
    }

    public static Waypoint add(String name, String category, int x, int y, int z) {
        List<Waypoint> l = all();
        Waypoint w = new Waypoint();
        w.name = name; w.category = category == null ? "Default" : category;
        w.dim = Storage.dim(); w.x = x; w.y = y; w.z = z;
        w.color = Theme.PALETTE[l.size() % Theme.PALETTE.length];
        l.add(w);
        save();
        return w;
    }

    public static boolean remove(String name) {
        boolean r = all().removeIf(w -> w.name.equalsIgnoreCase(name));
        if (r) save();
        return r;
    }

    public static void remove(Waypoint w) { if (all().remove(w)) save(); }
}

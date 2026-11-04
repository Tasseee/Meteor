package dev.nightfall.config;

import com.google.gson.*;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.Setting;
import dev.nightfall.util.Storage;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nightfall.json");

    public static int guiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;
    public static int hudKey = GLFW.GLFW_KEY_H;
    public static int mapKey = GLFW.GLFW_KEY_M;
    public static boolean snapGrid = true;
    /** Soft keyboard click feedback while Nightfall GUI/editor is open. */
    public static boolean keySounds = true;
    public static int gridSize = 4;

    /** Panel-Zustand je Kategorie: {x, y, offen(1/0)} */
    public static final Map<String, int[]> panels = new HashMap<>();
    /** HUD-Element-Zustand: {x, y, scale*100, locked(1/0)} */
    public static final Map<String, int[]> hud = new HashMap<>();

    private Config() {}

    public static Path file() { return FILE; }

    public static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", Nightfall.VERSION);
            root.addProperty("guiKey", guiKey);
            root.addProperty("hudKey", hudKey);
            root.addProperty("mapKey", mapKey);
            root.addProperty("snapGrid", snapGrid);
            root.addProperty("keySounds", keySounds);
            root.addProperty("gridSize", gridSize);

            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.all()) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.isEnabled());
                o.addProperty("key", m.getKey());
                JsonObject s = new JsonObject();
                for (Setting<?> st : m.getSettings()) s.add(st.getName(), st.save());
                o.add("settings", s);
                mods.add(m.getName(), o);
            }
            root.add("modules", mods);
            root.add("panels", mapToJson(panels));
            root.add("hud", mapToJson(hud));

            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("nightfall.json.tmp");
            Files.writeString(tmp, GSON.toJson(root));
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Nightfall.LOGGER.error("Config konnte nicht gespeichert werden", e);
        }
    }

    private static JsonObject mapToJson(Map<String, int[]> map) {
        JsonObject pj = new JsonObject();
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            JsonArray a = new JsonArray();
            for (int v : e.getValue()) a.add(v);
            pj.add(e.getKey(), a);
        }
        return pj;
    }

    private static void jsonToMap(JsonObject o, Map<String, int[]> map, int len) {
        map.clear();
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            try {
                if (!e.getValue().isJsonArray()) continue;
                JsonArray a = e.getValue().getAsJsonArray();
                if (a.size() != len) continue;
                int[] v = new int[len];
                for (int i = 0; i < len; i++) v[i] = a.get(i).getAsInt();
                map.put(e.getKey(), v);
            } catch (RuntimeException invalidEntry) {
                Nightfall.LOGGER.warn("Ungueltiger Config-Eintrag '{}' wird ignoriert", e.getKey());
            }
        }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(FILE));
            if (!parsed.isJsonObject()) throw new JsonParseException("Config-Wurzel ist kein JSON-Objekt");
            JsonObject root = parsed.getAsJsonObject();

            if (root.has("guiKey")) guiKey = root.get("guiKey").getAsInt();
            if (root.has("hudKey")) hudKey = root.get("hudKey").getAsInt();
            if (root.has("mapKey")) mapKey = root.get("mapKey").getAsInt();
            if (root.has("snapGrid")) snapGrid = root.get("snapGrid").getAsBoolean();
            if (root.has("keySounds")) keySounds = root.get("keySounds").getAsBoolean();
            if (root.has("gridSize")) gridSize = Math.max(1, Math.min(64, root.get("gridSize").getAsInt()));

            JsonObject mods = root.has("modules") && root.get("modules").isJsonObject()
                    ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ModuleManager.all()) {
                if (!mods.has(m.getName()) || !mods.get(m.getName()).isJsonObject()) continue;
                JsonObject o = mods.getAsJsonObject(m.getName());

                if (o.has("settings") && o.get("settings").isJsonObject()) {
                    JsonObject settings = o.getAsJsonObject("settings");
                    for (Setting<?> setting : m.getSettings()) {
                        if (!settings.has(setting.getName())) continue;
                        try {
                            setting.load(settings.get(setting.getName()));
                        } catch (RuntimeException invalidSetting) {
                            Nightfall.LOGGER.warn("Setting '{}' in Modul '{}' ist ungueltig und wird ignoriert",
                                    setting.getName(), m.getName());
                        }
                    }
                }

                if (o.has("key")) {
                    try {
                        int configuredKey = o.get("key").getAsInt();
                        String conflict = ModuleManager.assignKey(m, configuredKey);
                        if (conflict != null) Nightfall.LOGGER.warn(
                                "Keybind fuer '{}' wird ignoriert: {}", m.getName(), conflict);
                    } catch (RuntimeException invalidKey) {
                        Nightfall.LOGGER.warn("Ungueltiger Keybind fuer Modul '{}'", m.getName());
                    }
                }

                if (o.has("enabled")) {
                    try {
                        m.setEnabled(o.get("enabled").getAsBoolean());
                    } catch (RuntimeException | Error activationFailure) {
                        Nightfall.LOGGER.error("Modul '{}' konnte aus der Config nicht aktiviert werden",
                                m.getName(), activationFailure);
                    }
                }
            }

            if (root.has("panels") && root.get("panels").isJsonObject())
                jsonToMap(root.getAsJsonObject("panels"), panels, 3);
            if (root.has("hud") && root.get("hud").isJsonObject())
                jsonToMap(root.getAsJsonObject("hud"), hud, 4);
        } catch (Exception e) {
            Path backup = FILE.resolveSibling("nightfall.json.corrupt-" + System.currentTimeMillis());
            try {
                Files.copy(FILE, backup, StandardCopyOption.REPLACE_EXISTING);
                Nightfall.LOGGER.error("Defekte Config wurde gesichert unter {}; Nightfall startet mit Defaults",
                        backup, e);
            } catch (IOException backupError) {
                Nightfall.LOGGER.error("Config ist defekt und konnte nicht gesichert werden: {}", FILE, e);
                Nightfall.LOGGER.warn("Config-Backup fehlgeschlagen", backupError);
            }
        }
    }

    // ---- Profile (Import/Export) ----
    public static Path profile(String name) {
        Path d = Storage.dir().resolve("profiles");
        try { Files.createDirectories(d); } catch (IOException ignored) { }
        return d.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
    }

    public static boolean saveProfile(String name) {
        try { save(); Files.copy(FILE, profile(name), StandardCopyOption.REPLACE_EXISTING); return true; }
        catch (IOException e) { Nightfall.LOGGER.error("Profil speichern fehlgeschlagen", e); return false; }
    }

    public static boolean loadProfile(String name) {
        Path p = profile(name);
        if (!Files.exists(p)) return false;
        try { Files.copy(p, FILE, StandardCopyOption.REPLACE_EXISTING); load(); return true; }
        catch (IOException e) { Nightfall.LOGGER.error("Profil laden fehlgeschlagen", e); return false; }
    }
}

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
    public static float accentHue = 270.0f;
    public static int blurStrength = 72;
    public static String categoryOrder = "Combat,Movement,Visuals,Player,World,Misc,HUD,Config";

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
            root.addProperty("accentHue", accentHue);
            root.addProperty("blurStrength", blurStrength);
            root.addProperty("categoryOrder", categoryOrder);

            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.all()) {
                JsonObject o = new JsonObject();
                o.addProperty("enabled", m.isEnabled());
                o.addProperty("favorite", m.isFavorite());
                o.addProperty("key", m.getKey());
                JsonObject s = new JsonObject();
                for (Setting<?> st : m.getSettings()) s.add(st.getName(), st.save());
                o.add("settings", s);
                mods.add(m.getName(), o);
            }
            root.add("modules", mods);
            root.add("panels", mapToJson(panels));
            root.add("hud", mapToJson(hud));

            Path tmp = FILE.resolveSibling("nightfall.json.tmp");
            Files.writeString(tmp, GSON.toJson(root));
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
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
            JsonArray a = e.getValue().getAsJsonArray();
            if (a.size() != len) continue;
            int[] v = new int[len];
            for (int i = 0; i < len; i++) v[i] = a.get(i).getAsInt();
            map.put(e.getKey(), v);
        }
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (root.has("guiKey")) guiKey = root.get("guiKey").getAsInt();
            if (root.has("hudKey")) hudKey = root.get("hudKey").getAsInt();
            if (root.has("mapKey")) mapKey = root.get("mapKey").getAsInt();
            if (root.has("snapGrid")) snapGrid = root.get("snapGrid").getAsBoolean();
            if (root.has("keySounds")) keySounds = root.get("keySounds").getAsBoolean();
            if (root.has("gridSize")) gridSize = Math.max(1, root.get("gridSize").getAsInt());
            if (root.has("accentHue")) accentHue = Math.max(0, Math.min(360, root.get("accentHue").getAsFloat()));
            if (root.has("blurStrength")) blurStrength = Math.max(0, Math.min(150, root.get("blurStrength").getAsInt()));
            if (root.has("categoryOrder")) categoryOrder = root.get("categoryOrder").getAsString();

            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ModuleManager.all()) {
                if (!mods.has(m.getName())) continue;
                JsonObject o = mods.getAsJsonObject(m.getName());
                if (o.has("settings")) {
                    JsonObject s = o.getAsJsonObject("settings");
                    for (Setting<?> st : m.getSettings()) if (s.has(st.getName())) st.load(s.get(st.getName()));
                }
                if (o.has("key")) m.setKey(o.get("key").getAsInt());
                if (o.has("favorite")) m.setFavorite(o.get("favorite").getAsBoolean());
                if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
            }
            if (root.has("panels")) jsonToMap(root.getAsJsonObject("panels"), panels, 3);
            if (root.has("hud")) jsonToMap(root.getAsJsonObject("hud"), hud, 4);
        } catch (Exception e) {
            Nightfall.LOGGER.error("Config ist defekt und wird ignoriert: {}", FILE, e);
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

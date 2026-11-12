package dev.nightfall.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.Setting;
import dev.nightfall.util.Storage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;

public final class Config {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nightfall.json");
   public static int guiKey = 344;
   public static int hudKey = 72;
   public static int mapKey = 77;
   public static boolean snapGrid = true;
   public static boolean keySounds = true;
   public static int gridSize = 4;
   public static float accentHue = 174.0F;
   public static int blurStrength = 72;
   public static int guiTransparency = 82;
   public static int borderSize = 2;
   public static int cornerRadius = 24;
   public static int guiScalePercent = 100;
   public static int guiEdgeMargin = 12;
   public static String categoryOrder = "Combat,Movement,Visuals,Player,World,Misc,HUD,Config";
   public static boolean guiAnimations = true;
   public static float guiAnimationSpeed = 1.0F;
   public static String guiAnimationType = "Slide";
   public static String guiAnimationDirection = "Left";
   public static String guiEasing = "Cubic";
   public static int guiAnimationDuration = 220;
   public static final Map<String, int[]> panels = new HashMap<>();
   public static final Map<String, int[]> hud = new HashMap<>();

   private Config() {
   }

   public static Path file() {
      return FILE;
   }

   public static void save() {
      try {
         JsonObject root = new JsonObject();
         root.addProperty("version", "0.5.15");
         root.addProperty("guiKey", guiKey);
         root.addProperty("hudKey", hudKey);
         root.addProperty("mapKey", mapKey);
         root.addProperty("snapGrid", snapGrid);
         root.addProperty("keySounds", keySounds);
         root.addProperty("gridSize", gridSize);
         root.addProperty("accentHue", accentHue);
         root.addProperty("blurStrength", blurStrength);
         root.addProperty("guiTransparency", guiTransparency);
         root.addProperty("borderSize", borderSize);
         root.addProperty("cornerRadius", cornerRadius);
         root.addProperty("guiScalePercent", guiScalePercent);
         root.addProperty("guiEdgeMargin", guiEdgeMargin);
         root.addProperty("categoryOrder", categoryOrder);
         root.addProperty("guiAnimations", guiAnimations);
         root.addProperty("guiAnimationSpeed", guiAnimationSpeed);
         root.addProperty("guiAnimationType", guiAnimationType);
         root.addProperty("guiAnimationDirection", guiAnimationDirection);
         root.addProperty("guiEasing", guiEasing);
         root.addProperty("guiAnimationDuration", guiAnimationDuration);
         JsonObject mods = new JsonObject();

         for (Module m : ModuleManager.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.isEnabled());
            o.addProperty("favorite", m.isFavorite());
            o.addProperty("key", m.getKey());
            JsonObject s = new JsonObject();

            for (Setting<?> st : m.getSettings()) {
               s.add(st.getName(), st.save());
            }

            o.add("settings", s);
            mods.add(m.getName(), o);
         }

         root.add("modules", mods);
         root.add("panels", mapToJson(panels));
         root.add("hud", mapToJson(hud));
         Path tmp = FILE.resolveSibling("nightfall.json.tmp");
         Files.writeString(tmp, GSON.toJson(root));
         Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException var8) {
         Nightfall.LOGGER.error("Config konnte nicht gespeichert werden", var8);
      }
   }

   private static JsonObject mapToJson(Map<String, int[]> map) {
      JsonObject pj = new JsonObject();

      for (Entry<String, int[]> e : map.entrySet()) {
         JsonArray a = new JsonArray();

         for (int v : e.getValue()) {
            a.add(v);
         }

         pj.add(e.getKey(), a);
      }

      return pj;
   }

   private static void jsonToMap(JsonObject o, Map<String, int[]> map, int len) {
      map.clear();

      for (Entry<String, JsonElement> e : o.entrySet()) {
         JsonArray a = e.getValue().getAsJsonArray();
         if (a.size() == len) {
            int[] v = new int[len];

            for (int i = 0; i < len; i++) {
               v[i] = a.get(i).getAsInt();
            }

            map.put(e.getKey(), v);
         }
      }
   }

   public static void load() {
      if (Files.exists(FILE)) {
         try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (root.has("guiKey")) {
               guiKey = root.get("guiKey").getAsInt();
            }

            if (root.has("hudKey")) {
               hudKey = root.get("hudKey").getAsInt();
            }

            if (root.has("mapKey")) {
               mapKey = root.get("mapKey").getAsInt();
            }

            if (root.has("snapGrid")) {
               snapGrid = root.get("snapGrid").getAsBoolean();
            }

            if (root.has("keySounds")) {
               keySounds = root.get("keySounds").getAsBoolean();
            }

            if (root.has("gridSize")) {
               gridSize = Math.max(1, root.get("gridSize").getAsInt());
            }

            if (root.has("accentHue")) {
               accentHue = Math.max(0.0F, Math.min(360.0F, root.get("accentHue").getAsFloat()));
            }

            if (root.has("blurStrength")) {
               blurStrength = Math.max(0, Math.min(150, root.get("blurStrength").getAsInt()));
            }

            if (root.has("guiTransparency")) {
               guiTransparency = Math.max(0, Math.min(100, root.get("guiTransparency").getAsInt()));
            }
            if (root.has("borderSize")) {
               borderSize = Math.max(1, Math.min(6, root.get("borderSize").getAsInt()));
            }
            if (root.has("cornerRadius")) {
               cornerRadius = Math.max(10, Math.min(32, root.get("cornerRadius").getAsInt()));
            }
            if (root.has("guiScalePercent")) {
               guiScalePercent = Math.max(70, Math.min(100, root.get("guiScalePercent").getAsInt()));
            }
            if (root.has("guiEdgeMargin")) {
               guiEdgeMargin = Math.max(0, Math.min(60, root.get("guiEdgeMargin").getAsInt()));
            }
            if (root.has("categoryOrder")) {
               categoryOrder = root.get("categoryOrder").getAsString();
            }

            if (root.has("guiAnimations")) {
               guiAnimations = root.get("guiAnimations").getAsBoolean();
            }

            if (root.has("guiAnimationSpeed")) {
               guiAnimationSpeed = Math.max(0.1F, Math.min(5.0F, root.get("guiAnimationSpeed").getAsFloat()));
            }

            if (root.has("guiAnimationType")) {
               guiAnimationType = root.get("guiAnimationType").getAsString();
            }

            if (root.has("guiAnimationDirection")) {
               guiAnimationDirection = root.get("guiAnimationDirection").getAsString();
            }

            if (root.has("guiEasing")) {
               guiEasing = root.get("guiEasing").getAsString();
            }

            if (root.has("guiAnimationDuration")) {
               guiAnimationDuration = Math.max(50, Math.min(1000, root.get("guiAnimationDuration").getAsInt()));
            }

            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();

            for (Module m : ModuleManager.all()) {
               if (mods.has(m.getName())) {
                  JsonObject o = mods.getAsJsonObject(m.getName());
                  if (o.has("settings")) {
                     JsonObject s = o.getAsJsonObject("settings");

                     for (Setting<?> st : m.getSettings()) {
                        if (s.has(st.getName())) {
                           st.load(s.get(st.getName()));
                        }
                     }
                  }

                  if (o.has("key")) {
                     m.setKey(o.get("key").getAsInt());
                  }

                  if (o.has("favorite")) {
                     m.setFavorite(o.get("favorite").getAsBoolean());
                  }

                  if (o.has("enabled")) {
                     m.setEnabled(o.get("enabled").getAsBoolean());
                  }
               }
            }

            if (root.has("panels")) {
               jsonToMap(root.getAsJsonObject("panels"), panels, 3);
            }

            if (root.has("hud")) {
               jsonToMap(root.getAsJsonObject("hud"), hud, 4);
            }
         } catch (Exception var8) {
            Nightfall.LOGGER.error("Config ist defekt und wird ignoriert: {}", FILE, var8);
         }
      }
   }

   public static Path profile(String name) {
      Path d = Storage.dir().resolve("profiles");

      try {
         Files.createDirectories(d);
      } catch (IOException var3) {
      }

      return d.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
   }

   public static boolean saveProfile(String name) {
      try {
         save();
         Files.copy(FILE, profile(name), StandardCopyOption.REPLACE_EXISTING);
         return true;
      } catch (IOException var2) {
         Nightfall.LOGGER.error("Profil speichern fehlgeschlagen", var2);
         return false;
      }
   }

   public static boolean loadProfile(String name) {
      Path p = profile(name);
      if (!Files.exists(p)) {
         return false;
      } else {
         try {
            Files.copy(p, FILE, StandardCopyOption.REPLACE_EXISTING);
            load();
            return true;
         } catch (IOException var3) {
            Nightfall.LOGGER.error("Profil laden fehlgeschlagen", var3);
            return false;
         }
      }
   }
}

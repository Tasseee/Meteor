package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Explicit allow-list of actions the model may request.
 *
 * Never add a generic command/code execution tool here. Every action must validate
 * its arguments locally and be previewed/confirmed by the player before execution.
 */
public final class AIActionRegistry {
    private static final Map<String, String> BIOMES = new LinkedHashMap<>();

    static {
        BIOMES.put("minecraft:jungle", "Jungle");
        BIOMES.put("minecraft:bamboo_jungle", "Bambusdschungel");
        BIOMES.put("minecraft:desert", "Wüste");
        BIOMES.put("minecraft:cherry_grove", "Kirschhain");
        BIOMES.put("minecraft:badlands", "Badlands");
        BIOMES.put("minecraft:mangrove_swamp", "Mangrovensumpf");
        BIOMES.put("minecraft:mushroom_fields", "Pilzfelder");
        BIOMES.put("minecraft:dark_forest", "Dunkler Wald");
        BIOMES.put("minecraft:savanna", "Savanne");
        BIOMES.put("minecraft:taiga", "Taiga");
        BIOMES.put("minecraft:swamp", "Sumpf");
        BIOMES.put("minecraft:plains", "Ebene");
        BIOMES.put("minecraft:ocean", "Ozean");
        BIOMES.put("minecraft:snowy_plains", "Schneeebene");
    }

    private AIActionRegistry() {}

    public record Action(String name, JsonObject arguments, String preview) {}

    /** OpenAI-compatible function definitions. The model can suggest, never execute, these tools. */
    public static JsonArray toolDefinitions() {
        JsonArray tools = new JsonArray();
        tools.add(tool("locate_biome", "Find a biome using the server's normal /locate biome command. The player must confirm first.",
                objectSchema(
                        property("biome", "string", "Minecraft biome identifier. Choose one of the supported identifiers.", enumArray(BIOMES.keySet())),
                        "biome")));
        tools.add(tool("add_waypoint", "Save a waypoint at the player's current position. The player must confirm first.",
                objectSchema(property("name", "string", "Short waypoint name, max 32 characters."), "name")));
        tools.add(tool("set_module_state", "Enable or disable an existing Nightfall client module. The player must confirm first.",
                moduleStateSchema()));
        tools.add(tool("get_inventory", "Read the player's inventory and summarize item names and counts. This data is sent to the configured AI provider; the player must confirm first.",
                objectSchema()));
        return tools;
    }

    public static Action prepare(String name, String rawArguments) {
        if (name == null || name.length() > 64) throw new IllegalArgumentException("Unbekannte Aktion.");
        JsonObject args;
        try {
            args = rawArguments == null || rawArguments.isBlank()
                    ? new JsonObject() : JsonParser.parseString(rawArguments).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Die KI hat ungültige Aktionsargumente geliefert.");
        }

        return switch (name) {
            case "locate_biome" -> {
                String biome = requiredString(args, "biome", 64).toLowerCase(Locale.ROOT);
                if (!BIOMES.containsKey(biome)) throw new IllegalArgumentException("Dieses Biom wird von Nightfall noch nicht unterstützt.");
                yield new Action(name, args, "Nightfall sendet nach deiner Bestätigung den Serverbefehl:\n/locate biome " + biome
                        + "\nDer Server muss diesen Befehl erlauben. Du wirst nicht teleportiert.");
            }
            case "add_waypoint" -> {
                String waypointName = requiredString(args, "name", 32).trim();
                if (waypointName.isBlank() || !waypointName.matches("[\\p{L}0-9 _.-]{1,32}"))
                    throw new IllegalArgumentException("Der Wegpunktname enthält ungültige Zeichen.");
                yield new Action(name, args, "Nightfall speichert nach deiner Bestätigung einen Wegpunkt namens „"
                        + waypointName + "“ an deiner aktuellen Position.");
            }
            case "set_module_state" -> {
                String moduleName = requiredString(args, "module", 64);
                boolean enabled = requiredBoolean(args, "enabled");
                Module module = findModule(moduleName);
                if (module == null) throw new IllegalArgumentException("Dieses Nightfall-Modul existiert nicht.");
                yield new Action(name, args, "Nightfall wird das Modul „" + module.getName() + "“ "
                        + (enabled ? "aktivieren" : "deaktivieren") + "."
                        + (module.isEnabled() == enabled ? "\nDas Modul hat diesen Zustand bereits." : ""));
            }
            case "get_inventory" -> new Action(name, args,
                    "Nightfall liest dein Inventar und sendet Itemnamen und Mengen an den konfigurierten KI-Anbieter.");
            default -> throw new IllegalArgumentException("Diese Aktion ist nicht freigegeben.");
        };
    }

    /** Called only from the Minecraft client thread after explicit player confirmation. */
    public static String execute(Action action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return "Aktion fehlgeschlagen: Du bist nicht in einer Welt.";
        JsonObject args = action.arguments();

        return switch (action.name()) {
            case "locate_biome" -> {
                if (mc.player.networkHandler == null) yield "Aktion fehlgeschlagen: Keine Serververbindung.";
                String biome = args.get("biome").getAsString();
                try {
                    mc.player.networkHandler.sendChatCommand("locate biome " + biome);
                    yield "Der Befehl /locate biome " + biome + " wurde gesendet. Warte auf die Serverantwort; Serverrechte werden nicht umgangen.";
                } catch (RuntimeException error) {
                    yield "Der Befehl konnte nicht gesendet werden. Prüfe deine Verbindung.";
                }
            }
            case "add_waypoint" -> {
                var pos = mc.player.getBlockPos();
                Waypoint waypoint = WaypointManager.add(args.get("name").getAsString().trim(), "AI",
                        pos.getX(), pos.getY(), pos.getZ());
                yield "Wegpunkt „" + waypoint.name + "“ gespeichert bei X=" + waypoint.x
                        + ", Y=" + waypoint.y + ", Z=" + waypoint.z + " in " + waypoint.dim + ".";
            }
            case "set_module_state" -> {
                Module module = findModule(args.get("module").getAsString());
                if (module == null) yield "Aktion abgebrochen: Das Modul ist nicht mehr verfügbar.";
                boolean enabled = args.get("enabled").getAsBoolean();
                if (module.isEnabled() == enabled) yield "Das Modul „" + module.getName() + "“ war bereits "
                        + (enabled ? "aktiv" : "inaktiv") + ".";
                try {
                    module.setEnabled(enabled);
                    yield "Modul „" + module.getName() + "“ wurde " + (enabled ? "aktiviert" : "deaktiviert") + ".";
                } catch (RuntimeException | Error failure) {
                    yield "Nightfall konnte das Modul nicht " + (enabled ? "aktivieren" : "deaktivieren")
                            + ": " + failure.getClass().getSimpleName() + ".";
                }
            }
            case "get_inventory" -> inventorySummary(mc);
            default -> "Aktion nicht freigegeben.";
        };
    }

    private static String inventorySummary(MinecraftClient mc) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            items.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
        }
        if (items.isEmpty()) return "Das Inventar ist leer.";
        StringBuilder out = new StringBuilder("Inventar des Spielers (Itemname: Gesamtmenge):");
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            out.append("\n- ").append(entry.getKey()).append(": ").append(entry.getValue());
            if (out.length() > 3500) {
                out.append("\n(gekürzt)");
                break;
            }
        }
        return out.toString();
    }

    private static Module findModule(String name) {
        for (Module module : ModuleManager.all())
            if (module.getName().equalsIgnoreCase(name)) return module;
        return null;
    }

    private static String requiredString(JsonObject args, String key, int maxLength) {
        if (!args.has(key) || !args.get(key).isJsonPrimitive() || !args.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("Aktionsparameter '" + key + "' fehlt.");
        String value = args.get(key).getAsString();
        if (value.length() > maxLength) throw new IllegalArgumentException("Aktionsparameter '" + key + "' ist zu lang.");
        return value;
    }

    private static boolean requiredBoolean(JsonObject args, String key) {
        if (!args.has(key) || !args.get(key).isJsonPrimitive() || !args.getAsJsonPrimitive(key).isBoolean())
            throw new IllegalArgumentException("Aktionsparameter '" + key + "' fehlt oder ist ungültig.");
        return args.get(key).getAsBoolean();
    }

    private static JsonObject tool(String name, String description, JsonObject parameters) {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static JsonObject objectSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject objectSchema(JsonObject property, String required) {
        JsonObject schema = objectSchema();
        schema.getAsJsonObject("properties").add(property.get("name").getAsString(), property.get("schema"));
        JsonArray requiredList = new JsonArray();
        requiredList.add(required);
        schema.add("required", requiredList);
        return schema;
    }

    private static JsonObject property(String name, String type, String description, JsonArray options) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);
        if (options != null) schema.add("enum", options);
        JsonObject result = new JsonObject();
        result.addProperty("name", name);
        result.add("schema", schema);
        return result;
    }

    private static JsonObject property(String name, String type, String description) {
        return property(name, type, description, null);
    }

    private static JsonArray enumArray(Iterable<String> options) {
        JsonArray result = new JsonArray();
        for (String option : options) result.add(option);
        return result;
    }

    private static JsonObject moduleStateSchema() {
        JsonObject module = property("module", "string", "Exact Nightfall module name.").get("schema").getAsJsonObject();
        JsonArray names = new JsonArray();
        for (Module item : ModuleManager.all()) names.add(item.getName());
        module.add("enum", names);
        JsonObject enabled = property("enabled", "boolean", "True to enable, false to disable.").get("schema").getAsJsonObject();
        JsonObject schema = objectSchema();
        JsonObject props = schema.getAsJsonObject("properties");
        props.add("module", module);
        props.add("enabled", enabled);
        JsonArray required = new JsonArray();
        required.add("module");
        required.add("enabled");
        schema.add("required", required);
        return schema;
    }
}

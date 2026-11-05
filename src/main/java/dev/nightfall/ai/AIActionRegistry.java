package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explicit allow-list of actions the model may request.
 *
 * Never add shell/code execution or unrestricted server administration here. Every action
 * must validate its arguments locally and be previewed/confirmed before execution.
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
        tools.add(tool("get_player_status", "Read the player's current dimension, coordinates and health. This data is sent to the configured AI provider; the player must confirm first.",
                objectSchema()));
        tools.add(tool("random_teleport", "Request the server's random teleport using its common /rtp command. The player must confirm first; the server must support it.",
                objectSchema()));
        JsonObject command = property("command", "string",
                "A single Minecraft server command requested by the player, without the leading slash. Do not use server administration or destructive world-edit commands.");
        command.getAsJsonObject("schema").addProperty("maxLength", 256);
        tools.add(tool("run_server_command",
                "Send one ordinary Minecraft/server command exactly as requested by the player. Nightfall previews the exact command and asks for confirmation. Server permissions remain authoritative; dangerous administration and world-edit roots are blocked.",
                objectSchema(command, "command")));
        JsonObject buildStructureSchema = objectSchema(
                property("structure", "string", "Exact blueprint identifier.", enumArray(Set.of("starter_shelter", "large_house", "watchtower"))),
                "structure");
        JsonObject requestedMaterial = property("material", "string",
                "Optional Minecraft item ID or localized block name already in the player's inventory, for example minecraft:birch_planks. Omit to let Nightfall choose the most numerous matching full block.").get("schema").getAsJsonObject();
        requestedMaterial.addProperty("maxLength", 64);
        buildStructureSchema.getAsJsonObject("properties").add("material", requestedMaterial);
        tools.add(tool("build_structure",
                "Build a validated Nightfall blueprint: starter_shelter (54 blocks), large_house (130 blocks), or watchtower (74 blocks). You may specify an exact block material from the full player inventory; otherwise Nightfall picks the most numerous full-cube block. Validates materials and a clear level footprint, previews, asks for confirmation, then confirms each placement from a server world update.",
                buildStructureSchema));
        tools.add(tool("build_starter_shelter",
                "Backward-compatible alias for build_structure with structure=starter_shelter.",
                objectSchema()));
        JsonObject customBlueprintSchema = objectSchema();
        JsonObject customProps = customBlueprintSchema.getAsJsonObject("properties");
        JsonObject customName = property("name", "string", "Short descriptive blueprint name.").get("schema").getAsJsonObject();
        customName.addProperty("maxLength", 48);
        customProps.add("name", customName);
        JsonObject customMaterial = property("material", "string",
                "Optional Minecraft item ID or localized block name in the player's inventory. Omit to auto-select.").get("schema").getAsJsonObject();
        customMaterial.addProperty("maxLength", 64);
        customProps.add("material", customMaterial);
        JsonObject coordinateSchema = new JsonObject();
        coordinateSchema.addProperty("type", "object");
        JsonObject coordinateProps = new JsonObject();
        for (String axis : List.of("x", "y", "z")) {
            JsonObject coordinate = new JsonObject();
            coordinate.addProperty("type", "integer");
            coordinate.addProperty("minimum", 0);
            coordinate.addProperty("maximum", axis.equals("y") ? 10 : 15);
            coordinateProps.add(axis, coordinate);
        }
        JsonObject blockMaterial = new JsonObject();
        blockMaterial.addProperty("type", "string");
        blockMaterial.addProperty("maxLength", 64);
        blockMaterial.addProperty("description", "Optional Minecraft item ID or localized block name for this specific position.");
        coordinateProps.add("material", blockMaterial);
        coordinateSchema.add("properties", coordinateProps);
        JsonArray coordinateRequired = new JsonArray();
        coordinateRequired.add("x");
        coordinateRequired.add("y");
        coordinateRequired.add("z");
        coordinateSchema.add("required", coordinateRequired);
        coordinateSchema.addProperty("additionalProperties", false);
        JsonObject blocksSchema = new JsonObject();
        blocksSchema.addProperty("type", "array");
        blocksSchema.addProperty("minItems", 1);
        blocksSchema.addProperty("maxItems", 200);
        blocksSchema.add("items", coordinateSchema);
        customProps.add("blocks", blocksSchema);
        JsonArray customRequired = new JsonArray();
        customRequired.add("name");
        customRequired.add("blocks");
        customBlueprintSchema.add("required", customRequired);
        tools.add(tool("build_custom_structure",
                "Create a custom connected blueprint with at most 200 blocks in a 16x16x11 grid. Coordinates are nonnegative x/y/z offsets; blocks must be connected face-to-face to solid ground or existing solid blocks. You may set an optional material item ID/name per block to create a palette; otherwise use the top-level material or Nightfall's selected full-cube block from the player inventory. Nightfall validates per-material counts and previews the plan before confirmation.",
                customBlueprintSchema));
        JsonObject blueprintSearchSchema = objectSchema(property("query", "string",
                "Search the local curated Minecraft blueprint and farm knowledge database, e.g. dripstone farm, iron farm, storage."), "query");
        tools.add(tool("search_blueprints",
                "Search Nightfall's local version-aware blueprint knowledge base. Returns plans, prerequisites, provenance and honest tested/automation status. This is read-only and never starts building.",
                blueprintSearchSchema));
        tools.add(tool("list_schematics",
                "List locally imported schematic files and curated community schematic sources. Does not download files or start building.",
                objectSchema()));
        JsonObject schematicFile = property("file", "string",
                "Exact local filename inside config/nightfall/schematics, ending in .schem or .litematic.").get("schema").getAsJsonObject();
        schematicFile.addProperty("maxLength", 110);
        JsonObject schematicSchema = objectSchema(schematicFile, "file");
        tools.add(tool("inspect_schematic",
                "Inspect one locally downloaded Sponge v2 .schem or Litematica v5-v7 .litematic file. Reports dimensions, materials, automatic-build subset and blocks requiring manual placement. Read-only.",
                schematicSchema));
        tools.add(tool("build_schematic",
                "Prepare a local schematic for building. Nightfall preserves each supported BlockState and tests vanilla BlockItem placement contexts across possible support faces and player orientations. It clears air cells and occupied build positions with normal mining interactions, approaches nearby excavation targets through loaded terrain, and only counts exact server-confirmed states. It stops instead of placing a state it cannot reproduce. Block-entity blocks such as hoppers and chests can be placed with fresh default data, but inventories/custom NBT, fluids, falling blocks and entities are not recreated. Preview and explicit player confirmation are required.",
                schematicSchema));
        JsonObject walkTicks = property("ticks", "integer", "Dauer der kurzen Vorwärtsbewegung in Ticks (1 bis 100).").get("schema").getAsJsonObject();
        walkTicks.addProperty("minimum", 1);
        walkTicks.addProperty("maximum", 100);
        tools.add(tool("walk_forward", "Walk forward for a short bounded duration. This is not obstacle-aware pathfinding. Requires player confirmation.", objectSchema(walkTicks, "ticks")));
        tools.add(tool("stop_movement", "Immediately release Nightfall's movement keys and stop its active movement.", objectSchema()));
        JsonObject targetPosition = positionSchema();
        tools.add(tool("navigate_to",
                "Navigate to a feet-level block position x/y/z using A* paths through already-loaded chunks in automatic 32-block segments. Replans after each segment as new chunks load. Avoids solid blocks, fluids and common hazards. Stops if stuck or no safe next segment is available. Requires player confirmation.",
                targetPosition));
        tools.add(tool("mine_block",
                "Mine one visible block at x/y/z using the most suitable tool from the player's full inventory and normal vanilla block-breaking interactions. Nightfall temporarily swaps it into the hotbar and restores the displaced stack when possible. Will not break block entities/containers or unbreakable blocks; requires reach and clear line of sight. Waits for a world update. Requires player confirmation.",
                positionSchema()));
        tools.add(tool("mine_looked_block",
                "Mine the exact block currently under the player's crosshair using the most suitable tool from the player's full inventory and normal vanilla block-breaking interactions. Nightfall temporarily swaps it into the hotbar and restores the displaced stack when possible. Rejects containers, unbreakable or out-of-reach blocks. Requires player confirmation.",
                objectSchema()));
        tools.add(tool("craft_item",
                "Craft one batch of an exact item using a recipe already unlocked in the connected server's recipe book. Nightfall checks ingredients in the player's inventory, supports only shaped recipes up to 2x2 and shapeless recipes with up to four ingredients, places the recipe with the normal recipe-book interaction and waits for the output to appear in the inventory. It never creates ingredients or bypasses server recipe rules. Requires player confirmation.",
                objectSchema(property("item", "string",
                        "Exact Minecraft item ID, item path or localized item name to craft.").get("schema").getAsJsonObject(), "item"));
        JsonObject foodSchema = objectSchema();
        JsonObject foodItem = property("item", "string", "Optional exact Minecraft item ID, item path or localized name already in the hotbar.").get("schema").getAsJsonObject();
        foodItem.addProperty("maxLength", 64);
        foodSchema.getAsJsonObject("properties").add("item", foodItem);
        tools.add(tool("eat_food",
                "Eat the best available food from the hotbar when hunger is not full. Optionally choose an exact item. Uses the normal item-consumption interaction and restores the previous hotbar slot. Requires player confirmation.",
                foodSchema));
        tools.add(tool("attack_nearest_hostile",
                "Engage the nearest nearby hostile mob only (never players or passive/neutral mobs). Approaches using local pathfinding and performs normal melee attacks within reach. Bounded to 12 attacks or 45 seconds. Requires player confirmation.",
                objectSchema()));
        tools.add(tool("stop_current_action",
                "Immediately stop Nightfall movement, navigation, mining, crafting, eating and combat, and release all keys controlled by Nightfall. Safe emergency stop.",
                objectSchema()));
        tools.add(tool("get_build_status",
                "Read the current Nightfall builder status: confirmed block count, phase, pause reason, blueprint and materials. Read-only; no confirmation required.",
                objectSchema()));
        tools.add(tool("pause_build",
                "Pause the running Nightfall build safely. A pending block is still checked for server confirmation, but no new placement starts until resumed.",
                objectSchema()));
        tools.add(tool("resume_build",
                "Resume a Nightfall build that the player manually paused. New block placements require the player to confirm this action.",
                objectSchema()));
        tools.add(tool("stop_build",
                "Stop the currently running Nightfall block-by-block build. Already placed blocks remain in the world.",
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
            case "get_player_status" -> new Action(name, args,
                    "Nightfall liest Dimension, Position und Lebenspunkte und sendet diese Daten an den konfigurierten KI-Anbieter.");
            case "random_teleport" -> new Action(name, args,
                    "Nightfall sendet nach deiner Bestätigung den Serverbefehl /rtp. Der Server muss diesen Befehl anbieten; Nightfall kann den Teleport nicht erzwingen.");
            case "run_server_command" -> {
                String command = requiredString(args, "command", 256).trim();
                while (command.startsWith("/")) command = command.substring(1).trim();
                if (command.isBlank() || command.chars().anyMatch(Character::isISOControl)
                        || command.indexOf(';') >= 0)
                    throw new IllegalArgumentException("Bitte gib genau einen gültigen Minecraft-Befehl ohne Zeilenumbruch oder Befehlsverkettung an.");
                String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                int namespace = root.lastIndexOf(':');
                if (namespace >= 0) root = root.substring(namespace + 1);
                if (Set.of("op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "kick",
                        "stop", "restart", "whitelist", "reload", "save-all", "save-off", "save-on",
                        "execute", "function", "fill", "clone", "setblock", "place", "data", "loot",
                        "forceload", "worldedit", "we", "fawe", "fastasyncworldedit",
                        "set", "replace", "walls", "outline", "sphere", "hsphere", "cyl", "hcyl",
                        "pyramid", "hpyramid", "brush", "mask", "sel", "pos1", "pos2",
                        "copy", "cut", "paste", "undo", "redo", "schem", "schematic",
                        "stack", "move", "rotate", "flip", "naturalize", "regen", "deform",
                        "sudo", "console", "servercmd",
                        "lp", "luckperms", "pex", "permissionsex", "groupmanager",
                        "manuadd", "manudel").contains(root))
                    throw new IllegalArgumentException("Dieser Serververwaltungs- oder Welteditierbefehl ist aus Sicherheitsgründen gesperrt.");
                yield new Action(name, args, "Nightfall sendet nach deiner Bestätigung exakt diesen Befehl an den aktuellen Server:\n/"
                        + command + "\nDer Server entscheidet über Berechtigungen und Ausführung. Nightfall umgeht keine Rechte.");
            }
            case "build_structure" -> new Action(name, args, AIBuilder.prepareStructure(
                    requiredString(args, "structure", 32),
                    args.has("material") ? requiredString(args, "material", 64) : null));
            case "build_custom_structure" -> {
                JsonElement blockArgument = args.get("blocks");
                if (blockArgument == null || !blockArgument.isJsonArray())
                    throw new IllegalArgumentException("Der Bauplan benötigt ein blocks-Array.");
                yield new Action(name, args, AIBuilder.prepareCustomStructure(
                        requiredString(args, "name", 48),
                        args.has("material") ? requiredString(args, "material", 64) : null,
                        readCustomBlocks(blockArgument.getAsJsonArray())));
            }
            case "build_starter_shelter" -> new Action(name, args, AIBuilder.prepareStarterShelter());
            case "search_blueprints" -> new Action(name, args,
                    "Nightfall durchsucht die lokale Bauplan-Datenbank nach: " + requiredString(args, "query", 96));
            case "list_schematics" -> new Action(name, args, SchematicManager.listForAI()
                    + "\n\n" + SchematicManager.communityCatalog());
            case "inspect_schematic" -> new Action(name, args,
                    SchematicManager.inspectSummary(requiredString(args, "file", 110)));
            case "build_schematic" -> new Action(name, args,
                    SchematicManager.prepareBuild(requiredString(args, "file", 110)));
            case "walk_forward" -> {
                int ticks = readBoundedInteger(args, "ticks", 1, 100);
                yield new Action(name, args, "Nightfall hält die Vorwärtstaste für höchstens " + ticks
                        + " Ticks. Keine Hindernis-Umgehung; du kannst jederzeit „Stopp“ sagen.");
            }
            case "stop_movement" -> new Action(name, args, "Nightfall gibt sofort alle eigenen Bewegungs-Tasten frei.");
            case "navigate_to" -> {
                int x = readBoundedInteger(args, "x", -30_000_000, 30_000_000);
                int y = readBoundedInteger(args, "y", -2_048, 2_048);
                int z = readBoundedInteger(args, "z", -30_000_000, 30_000_000);
                yield new Action(name, args, AIPlayerController.previewNavigation(x, y, z));
            }
            case "mine_block" -> {
                int x = readBoundedInteger(args, "x", -30_000_000, 30_000_000);
                int y = readBoundedInteger(args, "y", -2_048, 2_048);
                int z = readBoundedInteger(args, "z", -30_000_000, 30_000_000);
                yield new Action(name, args, AIPlayerController.previewMine(x, y, z));
            }
            case "mine_looked_block" -> new Action(name, args, AIPlayerController.previewLookedBlock());
            case "craft_item" -> new Action(name, args,
                    AIPlayerController.previewCraftItem(requiredString(args, "item", 64)));
            case "eat_food" -> new Action(name, args, AIPlayerController.previewEat(
                    args.has("item") ? requiredString(args, "item", 64).trim() : null));
            case "attack_nearest_hostile" -> new Action(name, args, AICombatController.preview());
            case "stop_current_action" -> new Action(name, args,
                    "Nightfall stoppt sofort Navigation, Bewegung, Blockabbau, Crafting, Essen und Kampf und gibt alle eigenen Tasten frei.");
            case "get_build_status" -> new Action(name, args, AIBuilder.describeStatus());
            case "pause_build" -> new Action(name, args,
                    AIBuilder.isRunning() ? "Nightfall pausiert den Bau nach einer eventuell noch ausstehenden Blockbestätigung."
                            : "Aktuell läuft kein Nightfall-Bau.");
            case "resume_build" -> new Action(name, args,
                    "Nightfall setzt den bestätigten Bauplan fort und platziert danach wieder echte Blöcke.");
            case "stop_build" -> new Action(name, args,
                    AIBuilder.isRunning() ? "Nightfall stoppt den laufenden Bau. Bereits gesetzte Blöcke bleiben bestehen."
                            : "Aktuell läuft kein Nightfall-Bau.");
            default -> throw new IllegalArgumentException("Diese Aktion ist nicht freigegeben.");
        };
    }

    /** Called only from the Minecraft client thread after explicit player confirmation. */
    public static boolean requiresConfirmation(String actionName) {
        return !Set.of("search_blueprints", "list_schematics", "inspect_schematic",
                "get_build_status", "pause_build", "stop_build", "stop_movement", "stop_current_action").contains(actionName);
    }

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
            case "get_player_status" -> playerStatus(mc);
            case "random_teleport" -> {
                if (mc.player.networkHandler == null) yield "Aktion fehlgeschlagen: Keine Serververbindung.";
                try {
                    mc.player.networkHandler.sendChatCommand("rtp");
                    yield "Der Befehl /rtp wurde gesendet. Ob der Server ihn unterstützt und wohin er teleportiert, entscheidet ausschließlich der Server.";
                } catch (RuntimeException error) {
                    yield "Der /rtp-Befehl konnte nicht gesendet werden. Prüfe deine Verbindung.";
                }
            }
            case "run_server_command" -> {
                if (mc.player.networkHandler == null) yield "Aktion fehlgeschlagen: Keine Serververbindung.";
                String command = args.get("command").getAsString().trim();
                while (command.startsWith("/")) command = command.substring(1).trim();
                try {
                    mc.player.networkHandler.sendChatCommand(command);
                    yield "Der Befehl /" + command + " wurde an den Server gesendet. Ob er funktioniert, entscheidet der Server.";
                } catch (RuntimeException error) {
                    yield "Der Befehl konnte nicht gesendet werden. Prüfe deine Verbindung.";
                }
            }
            case "build_structure", "build_starter_shelter", "build_custom_structure" -> AIBuilder.startPreparedStructure();
            case "search_blueprints" -> BlueprintCatalog.search(args.get("query").getAsString());
            case "list_schematics" -> SchematicManager.listForAI() + "\n\n" + SchematicManager.communityCatalog();
            case "inspect_schematic" -> SchematicManager.inspectSummary(args.get("file").getAsString());
            case "build_schematic" -> AIBuilder.startPreparedStructure();
            case "walk_forward" -> AIPlayerController.walkForward(readBoundedInteger(args, "ticks", 1, 100));
            case "stop_movement" -> AIPlayerController.stop("Auf deinen Befehl gestoppt.");
            case "navigate_to" -> AIPlayerController.navigateTo(
                    args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
            case "mine_block" -> AIPlayerController.mineBlock(
                    args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
            case "mine_looked_block" -> AIPlayerController.mineLookedBlock();
            case "craft_item" -> AIPlayerController.craftItem(args.get("item").getAsString());
            case "eat_food" -> AIPlayerController.eatFood(args.has("item") ? args.get("item").getAsString() : null);
            case "attack_nearest_hostile" -> AICombatController.start();
            case "stop_current_action" -> {
                AICombatController.stop(null);
                AIBuilder.stop();
                yield AIPlayerController.stop("Auf deinen Befehl gestoppt.");
            }
            case "get_build_status" -> AIBuilder.describeStatus();
            case "pause_build" -> AIBuilder.pause();
            case "resume_build" -> AIBuilder.resume();
            case "stop_build" -> AIBuilder.stop();
            default -> "Aktion nicht freigegeben.";
        };
    }

    /** Discard local plan state when the player cancels the confirmation. */
    public static void cancel(Action action) {
        if (action != null && ("build_starter_shelter".equals(action.name())
                || "build_structure".equals(action.name())
                || "build_custom_structure".equals(action.name())
                || "build_schematic".equals(action.name()))) AIBuilder.cancelPrepared();
    }

    private static String playerStatus(MinecraftClient mc) {
        var pos = mc.player.getBlockPos();
        return "Spielerstatus:"
                + "\\nDimension: " + mc.world.getRegistryKey().getValue()
                + "\\nPosition: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ()
                + "\\nLeben: " + String.format(Locale.ROOT, "%.1f", mc.player.getHealth())
                + " / " + String.format(Locale.ROOT, "%.1f", mc.player.getMaxHealth());
    }

    private static String inventorySummary(MinecraftClient mc) {
        StringBuilder out = new StringBuilder("Inventar des Spielers. Der Builder kann die Hotbar und alle 27 Hauptinventar-Slots verwenden. Bei Bedarf holt er Stacks per normalem SWAP-Klick in die Hotbar.");
        boolean any = false;
        out.append("\nHotbar:");
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            any = true;
            out.append("\n- Slot ").append(slot + 1).append(": ")
                    .append(stack.getName().getString()).append(" [")
                    .append(Registries.ITEM.getId(stack.getItem())).append("] × ").append(stack.getCount());
        }
        if (!any) out.append("\n- leer");

        Map<String, Integer> rest = new LinkedHashMap<>();
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            String key = stack.getName().getString() + " [" + Registries.ITEM.getId(stack.getItem()) + "]";
            rest.merge(key, stack.getCount(), Integer::sum);
        }
        out.append("\nRestliches Inventar:");
        if (rest.isEmpty()) out.append("\n- leer");
        for (Map.Entry<String, Integer> entry : rest.entrySet()) {
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

    private static List<AIBuilder.RelativeBlock> readCustomBlocks(JsonArray blocks) {
        if (blocks == null || blocks.isEmpty() || blocks.size() > 200)
            throw new IllegalArgumentException("Ein eigener Bauplan muss 1 bis 200 Blöcke enthalten.");
        List<AIBuilder.RelativeBlock> result = new ArrayList<>(blocks.size());
        for (JsonElement element : blocks) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Ein Block im Bauplan ist kein Objekt.");
            JsonObject block = element.getAsJsonObject();
            if (block.size() < 3 || block.size() > 4 || !block.has("x") || !block.has("y") || !block.has("z")
                    || block.keySet().stream().anyMatch(key -> !Set.of("x", "y", "z", "material").contains(key)))
                throw new IllegalArgumentException("Jeder Bauplan-Block muss x, y, z und optional material enthalten.");
            String blockMaterial = null;
            if (block.has("material")) {
                if (!block.get("material").isJsonPrimitive() || !block.getAsJsonPrimitive("material").isString()
                        || block.get("material").getAsString().isBlank()
                        || block.get("material").getAsString().length() > 64)
                    throw new IllegalArgumentException("Das optionale Blockmaterial muss eine gültige Item-ID oder ein Blockname sein.");
                blockMaterial = block.get("material").getAsString();
            }
            result.add(new AIBuilder.RelativeBlock(readCoordinate(block, "x"),
                    readCoordinate(block, "y"), readCoordinate(block, "z"), blockMaterial));
        }
        return result;
    }

    private static JsonObject positionSchema() {
        JsonObject schema = objectSchema();
        JsonObject properties = schema.getAsJsonObject("properties");
        for (String axis : List.of("x", "z")) {
            JsonObject coordinate = new JsonObject();
            coordinate.addProperty("type", "integer");
            coordinate.addProperty("minimum", -30_000_000);
            coordinate.addProperty("maximum", 30_000_000);
            properties.add(axis, coordinate);
        }
        JsonObject y = new JsonObject();
        y.addProperty("type", "integer");
        y.addProperty("minimum", -2_048);
        y.addProperty("maximum", 2_048);
        properties.add("y", y);
        JsonArray required = new JsonArray();
        required.add("x");
        required.add("y");
        required.add("z");
        schema.add("required", required);
        return schema;
    }

    private static int readBoundedInteger(JsonObject args, String key, int min, int max) {
        if (!args.has(key) || !args.get(key).isJsonPrimitive() || !args.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Aktionsparameter '" + key + "' muss eine ganze Zahl sein.");
        double value = args.get(key).getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value) || value < min || value > max)
            throw new IllegalArgumentException("Aktionsparameter '" + key + "' muss zwischen " + min + " und " + max + " liegen.");
        return (int) value;
    }

    private static int readCoordinate(JsonObject object, String key) {
        if (!object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("Die Koordinate " + key + " muss eine ganze Zahl sein.");
        double value;
        try {
            value = object.get(key).getAsDouble();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Die Koordinate " + key + " muss eine gültige Zahl sein.");
        }
        int maximum = "y".equals(key) ? 10 : 15;
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 0 || value > maximum)
            throw new IllegalArgumentException("Die Koordinate " + key + " muss eine ganze Zahl zwischen 0 und " + maximum + " sein.");
        return (int) value;
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

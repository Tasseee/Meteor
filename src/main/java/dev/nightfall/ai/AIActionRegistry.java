package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.Setting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.config.Config;
import dev.nightfall.gui.ClickGuiScreen;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.BlockEntityEsp;
import dev.nightfall.modules.ChunkEsp;
import dev.nightfall.modules.EntityScanEsp;
import dev.nightfall.modules.IntelligenceScanner;
import dev.nightfall.modules.StaffWatch;
import dev.nightfall.modules.AntiVanishHints;
import dev.nightfall.modules.FakeBases;
import dev.nightfall.scanner.ChunkResult;
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

    private static volatile JsonArray cachedToolDefinitions;

    private AIActionRegistry() {}

    public record Action(String name, JsonObject arguments, String preview) {}

    /** Local action schemas shared by the offline command bridge; actions are validated and never executed from a model response. */
    private static JsonArray cachedToolDefinitions() {
        JsonArray cached = cachedToolDefinitions;
        if (cached == null) {
            synchronized (AIActionRegistry.class) {
                cached = cachedToolDefinitions;
                if (cached == null) {
                    cached = buildToolDefinitions();
                    cachedToolDefinitions = cached;
                }
            }
        }
        return cached;
    }

    public static JsonArray toolDefinitions() {
        return cachedToolDefinitions().deepCopy();
    }

    /** Copy only schemas selected for this request, avoiding a full-catalog clone. */
    public static JsonArray toolDefinitions(Set<String> selectedNames) {
        JsonArray filtered = new JsonArray();
        if (selectedNames == null || selectedNames.isEmpty()) return filtered;

        for (JsonElement element : cachedToolDefinitions()) {
            if (!element.isJsonObject()) continue;
            JsonObject function = element.getAsJsonObject().getAsJsonObject("function");
            if (function == null || !function.has("name")) continue;
            if (selectedNames.contains(function.get("name").getAsString())) {
                filtered.add(element.deepCopy());
            }
        }
        return filtered;
    }

    private static JsonArray buildToolDefinitions() {
        JsonArray tools = new JsonArray();
        tools.add(tool("search_structures",
                "Explore the nearby Minecraft world on foot without server commands. Walks a bounded expanding square spiral using safe local A* navigation through loaded chunks and scans nearby loaded blocks for characteristic block-pattern hints for ancient cities, trial chambers, nether fortresses, bastions or strongholds. Stops and reports likely coordinates when a pattern threshold is reached. This is heuristic, not guaranteed identification; never uses /locate or teleport. Requires player confirmation; user can say stop.",
                objectSchema(property("structure", "string", "Structure family to search for: any, ancient_city, trial_chambers, fortress, bastion, or stronghold.",
                        enumArray(List.of("any", "ancient_city", "trial_chambers", "fortress", "bastion", "stronghold"))), "structure")));
        tools.add(tool("locate_structure",
                "Search for the nearest Minecraft structure using the server's native /locate structure command. Accepts a structure ID such as minecraft:village, minecraft:ancient_city, minecraft:fortress, minecraft:bastion_remnant, minecraft:trial_chambers, minecraft:stronghold or minecraft:woodland_mansion. The server must support and permit the command. When a matching coordinate response arrives, Nightfall starts its bounded local A* navigation toward the reported location; it never teleports.",
                objectSchema(property("structure", "string", "Structure registry ID, for example minecraft:village or minecraft:ancient_city."), "structure")));
        tools.add(tool("locate_biome", "Find a biome using the server's normal /locate biome command. The player must confirm first.",
                objectSchema(
                        property("biome", "string", "Minecraft biome identifier. Choose one of the supported identifiers.", enumArray(BIOMES.keySet())),
                        "biome")));
        tools.add(tool("add_waypoint", "Save a waypoint at the player's current position. The player must confirm first.",
                objectSchema(property("name", "string", "Short waypoint name, max 32 characters."), "name")));
        tools.add(tool("list_waypoints",
                "Read the locally saved waypoints for the current server, including names, dimensions and coordinates. Data stays on the Minecraft client in offline mode. Read-only; does not navigate, add, edit or remove anything.",
                objectSchema()));
        tools.add(tool("search_bases",
                "Start or resume Nightfall's incremental BaseFinder scan. It inspects only chunks already loaded and sent to this client; it does not teleport, issue server commands, or reveal server-side hidden data. Requires player confirmation.",
                objectSchema()));
        tools.add(tool("get_base_findings",
                "Read the latest BaseFinder results: suspected chunks, score, observed evidence, scan completeness and whether server masking limited the data. Read-only; do not describe a SUS result as a confirmed player base.",
                objectSchema()));
        JsonObject observationSchema = objectSchema();
        observationSchema.getAsJsonObject("properties").add("module",
                property("module", "string", "Optional exact Nightfall module name, e.g. PlayerESP, EntityESP, StorageESP, BaseFinder, ChunkESP or Intelligence. Omit to inspect all relevant modules.").get("schema"));
        tools.add(tool("get_module_observations",
                "Read live observations from existing enabled Nightfall modules. Uses PlayerESP/EntityESP/ItemESP and StorageESP/BlockEntityESP caches plus BaseFinder/ChunkESP SUS findings and Intelligence scanner counters. Read-only; reports only client-visible loaded data. If a requested module is disabled, report that and ask before enabling it.",
                observationSchema));
        JsonObject fakeBaseSchema = objectSchema(property("name", "string", "Short local label for this fake/decoy base, max 32 characters."),
                "name");
        fakeBaseSchema.getAsJsonObject("properties").add("learn",
                property("learn", "boolean", "If true, learn the current loaded chunk's block-entity layout as a reusable fake-base template.").get("schema"));
        tools.add(tool("mark_fake_base",
                "Mark the player's current chunk as a known fake/decoy base, saved locally for this server and dimension. Optionally learn its loaded block-entity layout as a template. Does not change world blocks. Requires confirmation.",
                fakeBaseSchema));
        tools.add(tool("list_fake_base_templates",
                "List the names of locally learned fake-base templates. Read-only; templates are local and server/dimension scoped.",
                objectSchema()));
        tools.add(tool("set_module_state", "Enable or disable an existing Nightfall client module. The player must confirm first.",
                moduleStateSchema()));
        JsonObject moduleQuery = objectSchema();
        moduleQuery.getAsJsonObject("properties").add("module", property("module", "string", "Optional exact Nightfall module name. Omit to list settings for all modules.").get("schema"));
        tools.add(tool("get_module_settings", "Read the live Nightfall ClickGUI module list and their editable settings, current values, ranges and allowed modes. Read-only; no confirmation required. Do not guess setting names; inspect first.", moduleQuery));
        JsonObject setSettingSchema = objectSchema();
        JsonObject setSettingProperties = setSettingSchema.getAsJsonObject("properties");
        setSettingProperties.add("module", property("module", "string", "Exact Nightfall module name.").get("schema"));
        setSettingProperties.add("setting", property("setting", "string", "Exact setting name as returned by get_module_settings.").get("schema"));
        setSettingProperties.add("value", property("value", "string", "New value: true/false, number, allowed mode text, string text, or #RRGGBB for colors.").get("schema"));
        JsonArray setSettingRequired = new JsonArray();
        setSettingRequired.add("module");
        setSettingRequired.add("setting");
        setSettingRequired.add("value");
        setSettingSchema.add("required", setSettingRequired);
        tools.add(tool("set_module_setting", "Change one existing Nightfall ClickGUI setting by exact module and setting name. Supports boolean toggles, bounded numbers, allowed mode choices, text settings and colors (#RRGGBB). The client validates the setting type and range, shows a preview and requires player confirmation. Unsupported list settings cannot be edited by AI.", setSettingSchema));
        tools.add(tool("open_nightfall_menu", "Open Nightfall's own ClickGUI menu on the client. Does not change any setting.", objectSchema()));
        tools.add(tool("get_inventory", "Read the player's inventory and summarize item names and counts locally. Data stays on the Minecraft client in offline mode.",
                objectSchema()));
        tools.add(tool("sort_inventory",
                "Sort permitted ordinary items in the player's 36 normal inventory/hotbar slots by category and item ID. Preserves exact stack counts, durability, enchantments and item components; does not merge or delete items. Combat-oriented items remain in their exact slots. Armor, offhand, crafting slots and cursor are never moved. Uses normal server-confirmed inventory swaps, one at a time; requires a preview and explicit confirmation.",
                objectSchema()));
        tools.add(tool("get_player_status", "Read the player's current dimension, coordinates and health locally. Data stays on the Minecraft client in offline mode.",
                objectSchema()));
        tools.add(tool("random_teleport", "Request the server's random teleport using its common /rtp command. The player must confirm first; the server must support it.",
                objectSchema()));
        JsonObject command = property("command", "string",
                "A single Minecraft server command requested by the player, without the leading slash. Do not use server administration or destructive world-edit commands.");
        command.getAsJsonObject("schema").addProperty("maxLength", 256);
        tools.add(tool("run_server_command",
                "Send one ordinary Minecraft/server command exactly as requested by the player. Nightfall previews the exact command and asks for confirmation. Server permissions remain authoritative; dangerous administration and world-edit roots are blocked.",
                objectSchema(command, "command")));
        JsonObject materialPlanSchema = objectSchema(
                property("structure", "string", "Fixed Nightfall blueprint identifier to analyze.",
                        enumArray(Set.of("starter_shelter", "large_house", "watchtower"))),
                "structure");
        JsonObject materialPlanMaterial = property("material", "string",
                "Optional exact Minecraft block item ID or localized name to check. It may be omitted to rank available full-block materials.").get("schema").getAsJsonObject();
        materialPlanMaterial.addProperty("maxLength", 64);
        materialPlanSchema.getAsJsonObject("properties").add("material", materialPlanMaterial);
        tools.add(tool("plan_build_materials",
                "Read-only material availability plan for starter_shelter (54 blocks), large_house (130 blocks), or watchtower (74 blocks). Counts suitable full-cube block items across the player's 36 normal inventory/hotbar slots and ranks the best fit before shortages. Optional material selects one exact material. Reads inventory data locally on the Minecraft client; no provider receives it in offline mode. Does not prepare a build, save a checkpoint, swap slots, or change the world.",
                materialPlanSchema));

        JsonObject buildStructureSchema = objectSchema(
                property("structure", "string", "Exact blueprint identifier.", enumArray(Set.of("starter_shelter", "large_house", "watchtower"))),
                "structure");
        JsonObject requestedMaterial = property("material", "string",
                "Optional Minecraft item ID or localized block name already in the player's inventory, for example minecraft:birch_planks. Omit to let Nightfall choose the most numerous matching full block.").get("schema").getAsJsonObject();
        requestedMaterial.addProperty("maxLength", 64);
        buildStructureSchema.getAsJsonObject("properties").add("material", requestedMaterial);
        tools.add(tool("build_structure",
                "Build a validated Nightfall blueprint: starter_shelter (54 blocks), large_house (130 blocks), or watchtower (74 blocks). You may specify an exact block material from the full player inventory; otherwise Nightfall picks the most numerous full-cube block. Requires level solid ground; safely excavates ordinary removable obstacles within planned block positions, but refuses block entities, fluids, falling blocks and unbreakable blocks. Previews the excavation count, block IDs and build plan, requires confirmation, then confirms each excavation and placement from world updates.",
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
        blocksSchema.addProperty("maxItems", 512);
        blocksSchema.add("items", coordinateSchema);
        customProps.add("blocks", blocksSchema);
        JsonArray customRequired = new JsonArray();
        customRequired.add("name");
        customRequired.add("blocks");
        customBlueprintSchema.add("required", customRequired);
        tools.add(tool("build_custom_structure",
                "Create a custom connected blueprint with at most 512 blocks in a 32x32x32 grid. Coordinates are nonnegative x/y/z offsets from 0 to 31; blocks must be connected face-to-face to solid ground or existing solid blocks. You may set an optional material item ID/name per block to create a palette; otherwise use the top-level material or Nightfall's selected full-cube block from the player inventory. Nightfall validates per-material counts and previews the plan before confirmation.",
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
        JsonObject partX = property("part_x", "integer", "0-basierter Abschnitt entlang der X-Achse; jeder Abschnitt ist maximal 32 Blöcke breit.").get("schema").getAsJsonObject();
        partX.addProperty("minimum", 0); partX.addProperty("maximum", 15);
        JsonObject partY = property("part_y", "integer", "0-basierter Abschnitt entlang der Y-Achse; jeder Abschnitt ist maximal 32 Blöcke hoch.").get("schema").getAsJsonObject();
        partY.addProperty("minimum", 0); partY.addProperty("maximum", 15);
        JsonObject partZ = property("part_z", "integer", "0-basierter Abschnitt entlang der Z-Achse; jeder Abschnitt ist maximal 32 Blöcke lang.").get("schema").getAsJsonObject();
        partZ.addProperty("minimum", 0); partZ.addProperty("maximum", 15);
        schematicSchema.getAsJsonObject("properties").add("part_x", partX);
        schematicSchema.getAsJsonObject("properties").add("part_y", partY);
        schematicSchema.getAsJsonObject("properties").add("part_z", partZ);
        JsonObject resetAnchor = property("reset_anchor", "boolean",
                "Setze nur bei einem ausdrücklich gewünschten neuen Farm-Standort auf true. Der neue Startpunkt wird bei Abschnitt 0/0/0 gespeichert; alle folgenden Abschnitte müssen false verwenden.").get("schema").getAsJsonObject();
        schematicSchema.getAsJsonObject("properties").add("reset_anchor", resetAnchor);
        tools.add(tool("inspect_schematic",
                "Inspect a local Sponge v2 .schem or Litematica v5-v7 .litematic file, including mirrored/negative region axes and large designs up to 512 blocks per axis and 8 million volume. Reports the 32x32x32 build-section grid, materials, automatic subset, manual-only blocks, exact build center and an accessible feet-level position at the saved farm anchor's Y. For nonzero part_x/y/z, start with section 0/0/0 first so a stable farm anchor is saved. Set reset_anchor=true only when the player explicitly wants a new build location; otherwise preserve the existing anchor.",
                schematicSchema));
        tools.add(tool("repair_schematic",
                "Create a NEW local Sponge v2 .schem copy containing only the source schematic's block states and still-water sources that Nightfall can import automatically. The original file is never modified or overwritten. Unsupported blocks are replaced with air; entities and custom block-entity NBT/inventories are omitted. This sanitizes the placement subset only; it does not repair farm logic, redstone timing, fluids, villagers or guarantee working output. The player must inspect the generated file again before building. Requires explicit confirmation.",
                objectSchema(property("file", "string", "Exact original filename inside config/nightfall/schematics, ending in .schem or .litematic.").get("schema").getAsJsonObject(), "file")));
        tools.add(tool("build_schematic",
                "Prepare exactly one 32x32x32 section of a local schematic for building. For oversized structures, pass part_x, part_y and part_z (zero-based); inspect_schematic reports the valid grid. Start with part_x=0, part_y=0, part_z=0: Nightfall stores a server/dimension/file-bound farm anchor in config/nightfall/schematic_build_session.json. Set reset_anchor=true only when intentionally starting another copy at a new location; all remaining calls for that copy must keep it false. Later sections retain the exact overall schematic alignment and report both their build center and an access position at the farm anchor's Y-level; navigate to the access position before previewing so the destination chunks are loaded. Build every section in ascending part_y order (lowest layer first), then across X/Z, so upper components can attach to already-built lower layers. Nightfall then approaches reachable placement targets using bounded A* navigation and saves/resumes section progress across client restarts. It preserves supported BlockStates and stops when a stable orientation/state cannot be reproduced. Redstone dust is placed with its dedicated vanilla item interaction after structural supports and redstone devices; repeaters, comparators, observers, pistons, levers, buttons, lamps and other supported BlockItems retain their static orientation/configuration. Neighbor- and tick-driven values (wire connections/power, powered/locked/lit/extended) are allowed to update naturally and are not treated as fixed schematic values. This does not verify redstone timing, inventory contents, signal paths or farm output. Still water source cells can be placed after solid blocks only in Creative mode when a water bucket is in the player's inventory; flowing water is left to vanilla fluid simulation and must be checked in-game. Survival water, lava, falling blocks, entities and custom block-entity NBT/inventories remain manual.\n"
                + "Material-shortage workflow: the current section requires its exact materials in the player's inventory; Creative mode only requires one representative stack per material. In Survival, obtain the missing counts before building the section. Do not claim the entire schematic is complete after building one section.",
                schematicSchema));
        JsonObject schematicProgressSchema = objectSchema(
                property("file", "string", "Exact local .schem or .litematic filename.").get("schema").getAsJsonObject(), "file");
        schematicProgressSchema.getAsJsonObject("properties").getAsJsonObject("file").addProperty("maxLength", 110);
        tools.add(tool("get_schematic_progress",
                "Read-only durable progress for a local schematic. Reports fully completed sections for the current server and dimension and the next unfinished section in bottom-up order. Does not create or move the farm anchor and does not start building.",
                schematicProgressSchema));
        JsonObject verifySchematicSchema = objectSchema(
                property("file", "string", "Exact local .schem or .litematic filename.").get("schema").getAsJsonObject(), "file");
        verifySchematicSchema.getAsJsonObject("properties").getAsJsonObject("file").addProperty("maxLength", 110);
        for (String axis : List.of("part_x", "part_y", "part_z")) {
            JsonObject index = property(axis, "integer", "Zero-based 32-block section index (0–15).").get("schema").getAsJsonObject();
            index.addProperty("minimum", 0);
            index.addProperty("maximum", 15);
            verifySchematicSchema.getAsJsonObject("properties").add(axis, index);
        }
        tools.add(tool("verify_schematic_section",
                "Read-only in-world structural check for one previously anchored schematic section. Compares expected imported block types/states, still-water sources, clear cells and presence of default block entities against already-loaded client chunks. Reports unloaded positions separately. Does not test villager behavior, custom block-entity inventories, fluid flow, redstone timing/signal paths or farm output; it cannot certify a working farm.",
                verifySchematicSchema));
        JsonObject walkTicks = property("ticks", "integer", "Dauer der kurzen Vorwärtsbewegung in Ticks (1 bis 100).").get("schema").getAsJsonObject();
        walkTicks.addProperty("minimum", 1);
        walkTicks.addProperty("maximum", 100);
        tools.add(tool("walk_forward", "Walk forward for a short bounded duration. This is not obstacle-aware pathfinding. Requires player confirmation.", objectSchema(walkTicks, "ticks")));
        tools.add(tool("stop_movement", "Immediately release Nightfall's movement keys and stop its active movement.", objectSchema()));
        JsonObject targetPosition = positionSchema();
        tools.add(tool("navigate_to",
                "Navigate to a feet-level block position x/y/z using A* paths through already-loaded chunks in automatic 32-block segments. Replans after each segment as new chunks load. Avoids solid blocks, fluids and common hazards. Stops if stuck or no safe next segment is available. Requires player confirmation.",
                targetPosition));
        tools.add(tool("resume_saved_task",
                "Preview and, after explicit player confirmation, resume a saved gathering or navigation intent from the same server and dimension. Nightfall revalidates the request and creates a fresh scan or path; it never reuses old block targets or waypoints. Only gathering and ordinary navigation are supported.",
                objectSchema()));
        tools.add(tool("clear_saved_task",
                "Preview and, only after explicit player confirmation, delete the local saved gathering/navigation task checkpoint for the current server and dimension. Does not stop a running action or delete saved build progress; valid checkpoints for other servers/dimensions remain untouched.",
                objectSchema()));
        tools.add(tool("mine_block",
                "Mine one visible block at x/y/z using the most suitable tool from the player's full inventory and normal vanilla block-breaking interactions. Nightfall temporarily swaps it into the hotbar and restores the displaced stack when possible. Will not break block entities/containers or unbreakable blocks; requires reach and clear line of sight. Waits for a world update. Requires player confirmation.",
                positionSchema()));
        tools.add(tool("mine_looked_block",
                "Mine the exact block currently under the player's crosshair using the most suitable tool from the player's full inventory and normal vanilla block-breaking interactions. Nightfall temporarily swaps it into the hotbar and restores the displaced stack when possible. Rejects containers, unbreakable or out-of-reach blocks. Requires player confirmation.",
                objectSchema()));
        JsonObject gatherWoodSchema = objectSchema();
        JsonObject gatherWoodItem = property("item", "string",
                "Exact vanilla wood block item ID, e.g. minecraft:oak_log or minecraft:crimson_stem.").get("schema").getAsJsonObject();
        gatherWoodItem.addProperty("maxLength", 64);
        JsonObject gatherWoodCount = property("count", "integer",
                "Target total amount of this item in the player's inventory (1–1024).").get("schema").getAsJsonObject();
        gatherWoodCount.addProperty("minimum", 1);
        gatherWoodCount.addProperty("maximum", 1_024);
        JsonObject gatherWoodDuration = property("duration_seconds", "integer",
                "Optionales Laufzeitlimit in Sekunden (0–1.200). 0 = bis zum Mengenziel bzw. bis keine sicheren Ziele mehr verfügbar sind.").get("schema").getAsJsonObject();
        gatherWoodDuration.addProperty("minimum", 0);
        gatherWoodDuration.addProperty("maximum", 1_200);
        gatherWoodSchema.getAsJsonObject("properties").add("item", gatherWoodItem);
        gatherWoodSchema.getAsJsonObject("properties").add("count", gatherWoodCount);
        gatherWoodSchema.getAsJsonObject("properties").add("duration_seconds", gatherWoodDuration);
        JsonArray gatherWoodRequired = new JsonArray();
        gatherWoodRequired.add("item");
        gatherWoodRequired.add("count");
        gatherWoodSchema.add("required", gatherWoodRequired);
        tools.add(tool("gather_wood",
                "Gather a target total amount (1–1024) of a specific vanilla log/wood/stem/hyphae item. Optional duration_seconds is 0–1200 (20 minutes maximum); 0 means stop at the count target or when no safe loaded targets remain. Re-scans loaded chunks around the player's current position, up to 32 blocks horizontally, tracks already attempted blocks, uses safe nearby A* paths and normal vanilla block breaking, and waits for confirmed drops. The whole job is capped at 2048 target blocks and the inventory must have room. For a duration-first request, choose a high but valid count target and set duration_seconds. Requires player confirmation.",
                gatherWoodSchema));
        JsonObject gatherBlockSchema = objectSchema();
        JsonObject gatherBlockItem = property("item", "string",
                "Exact supported vanilla block item ID, e.g. minecraft:cobblestone, minecraft:dirt or minecraft:netherrack.").get("schema").getAsJsonObject();
        gatherBlockItem.addProperty("maxLength", 64);
        JsonObject gatherBlockCount = property("count", "integer",
                "Target total amount of this item in the player's inventory (1–1024).").get("schema").getAsJsonObject();
        gatherBlockCount.addProperty("minimum", 1);
        gatherBlockCount.addProperty("maximum", 256);
        JsonObject gatherBlockDuration = property("duration_seconds", "integer",
                "Optionales Laufzeitlimit in Sekunden (0–1.200). 0 = bis zum Mengenziel bzw. bis keine sicheren Ziele mehr verfügbar sind.").get("schema").getAsJsonObject();
        gatherBlockDuration.addProperty("minimum", 0);
        gatherBlockDuration.addProperty("maximum", 1_200);
        gatherBlockSchema.getAsJsonObject("properties").add("item", gatherBlockItem);
        gatherBlockSchema.getAsJsonObject("properties").add("count", gatherBlockCount);
        gatherBlockSchema.getAsJsonObject("properties").add("duration_seconds", gatherBlockDuration);
        JsonArray gatherBlockRequired = new JsonArray();
        gatherBlockRequired.add("item");
        gatherBlockRequired.add("count");
        gatherBlockSchema.add("required", gatherBlockRequired);
        tools.add(tool("gather_block",
                "Gather a target total amount (1–1024) of a whitelisted vanilla self-dropping block item. Optional duration_seconds is 0–1200. Re-scans already-loaded chunks within 32 horizontal blocks, uses safe A* paths, checks drop correctness and waits for confirmed inventory pickups. Still rejects falling blocks, fluids, block entities, unbreakable or unsupported blocks and items requiring unsupported drop logic. At most 2048 source blocks per task. Requires player confirmation.",
                gatherBlockSchema));
        JsonObject gatherOreSchema = objectSchema();
        JsonObject gatherOreItem = property("item", "string",
                "Exact ore drop item ID, e.g. minecraft:raw_iron, minecraft:coal, minecraft:diamond or minecraft:ancient_debris.").get("schema").getAsJsonObject();
        gatherOreItem.addProperty("maxLength", 64);
        JsonObject gatherOreCount = property("count", "integer",
                "Target total amount of this drop item in the player's inventory (1–256).").get("schema").getAsJsonObject();
        gatherOreCount.addProperty("minimum", 1);
        gatherOreCount.addProperty("maximum", 256);
        JsonObject gatherOreDuration = property("duration_seconds", "integer",
                "Optionales Laufzeitlimit in Sekunden (0–1.200). 0 = bis zum Mengenziel bzw. bis keine sicheren Ziele mehr verfügbar sind.").get("schema").getAsJsonObject();
        gatherOreDuration.addProperty("minimum", 0);
        gatherOreDuration.addProperty("maximum", 1_200);
        JsonObject gatherOreHeight = property("target_y", "integer",
                "Optionale sichere Zielhöhe der Füße. Nightfall versucht, zu einer begehbaren Position auf dieser Höhe zu navigieren, bevor es scannt.").get("schema").getAsJsonObject();
        gatherOreHeight.addProperty("minimum", -64);
        gatherOreHeight.addProperty("maximum", 320);
        gatherOreSchema.getAsJsonObject("properties").add("item", gatherOreItem);
        gatherOreSchema.getAsJsonObject("properties").add("count", gatherOreCount);
        gatherOreSchema.getAsJsonObject("properties").add("duration_seconds", gatherOreDuration);
        gatherOreSchema.getAsJsonObject("properties").add("target_y", gatherOreHeight);
        JsonArray gatherOreRequired = new JsonArray();
        gatherOreRequired.add("item");
        gatherOreRequired.add("count");
        gatherOreSchema.add("required", gatherOreRequired);
        tools.add(tool("gather_ore",
                "Gather a target total amount (1–256) of supported ore drops, including Ancient Debris. Optional duration_seconds is 0–1200 (20 minutes maximum); optional target_y requests a safe route to a walkable feet-level position at that Y before scanning. Re-scans only already-loaded chunks within 32 horizontal blocks, 24 below and 12 above the current position, excludes attempted targets and uses a bounded clockwise square-spiral of safe A* exploration legs (maximum 64) to load nearby new chunks. Ancient Debris still requires the Nether and a suitable mining tool; it may use a short checked horizontal tunnel (up to 8 steps) through approved Nether blocks. It never digs downward or into fluids, bedrock, hazards or unknown rock. All mining uses normal vanilla interactions and confirmed block/drop updates; the whole task is capped at 2048 source blocks and will stop on timeout, inventory capacity, missing targets or unsafe routes. This is a systematic local survey, not a scan of the entire world or unloaded regions. For duration-first requests, set a high but valid count target plus duration_seconds. Requires player confirmation.",
                gatherOreSchema));
        JsonObject smeltSchema = objectSchema();
        JsonObject smeltItem = property("item", "string",
                "Raw smeltable item ID: minecraft:raw_iron, minecraft:raw_gold, minecraft:raw_copper or minecraft:ancient_debris.").get("schema").getAsJsonObject();
        smeltItem.addProperty("maxLength", 64);
        JsonObject smeltCount = property("count", "integer",
                "Number of raw items to smelt in this single furnace batch (1–16).").get("schema").getAsJsonObject();
        smeltCount.addProperty("minimum", 1);
        smeltCount.addProperty("maximum", 16);
        smeltSchema.getAsJsonObject("properties").add("item", smeltItem);
        smeltSchema.getAsJsonObject("properties").add("count", smeltCount);
        JsonArray smeltRequired = new JsonArray();
        smeltRequired.add("item");
        smeltRequired.add("count");
        smeltSchema.add("required", smeltRequired);
        tools.add(tool("smelt_item",
                "Smelt 1–16 of minecraft:raw_iron, raw_gold, raw_copper or ancient_debris in the empty furnace/blast furnace directly under the player's crosshair. Requires enough matching raw input and coal/charcoal already in the player's inventory. Computes the fuel amount for the chosen furnace type, inserts only the requested batch with normal furnace slot interactions, waits for server-confirmed output and takes outputs one at a time. Never opens or modifies a non-empty furnace, never removes existing contents, and stops if the GUI closes, the inventory lacks room or output cannot be confirmed. Requires player confirmation.",
                smeltSchema));
        JsonObject craftSchema = objectSchema(property("item", "string",
                "Exact Minecraft item ID, item path or localized item name to craft."), "item");
        JsonObject craftCount = property("count", "integer",
                "Additional amount to craft (1–256); defaults to one item batch if omitted.").get("schema").getAsJsonObject();
        craftCount.addProperty("minimum", 1);
        craftCount.addProperty("maximum", 256);
        craftSchema.getAsJsonObject("properties").add("count", craftCount);
        tools.add(tool("craft_item",
                "Plan and execute a bounded multi-step chain from unlocked vanilla Minecraft recipes only. Supports shaped recipes up to 3x3 and shapeless recipes with up to nine ingredient slots. Protected output categories are excluded. If any planned step needs 3x3, the player must open a crafting table before requesting the action; Nightfall never places or opens a table itself. It recursively plans permitted intermediate ingredients from the player's 36 main inventory slots, then crafts each recipe step separately, taking each output and waiting for server-confirmed inventory updates before the next step. Limits: 32 recipe steps, dependency depth 8, 256 total batches and five minutes. Stops on missing ingredients, full inventory, closed crafting screen, unsupported recipe displays, locked recipes or timeout. It does not create ingredients or bypass server recipe rules. Requires player confirmation.",
                craftSchema));
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
        tools.add(tool("guard_nearby_hostiles",
                "Temporarily take control of the player's movement to defend them from nearby vanilla HostileEntity mobs within 10 blocks. Uses local safe pathfinding and normal melee attacks, reacquires nearby hostile mobs, and never attacks players, passive mobs or neutral mobs. Stops after 90 seconds or 24 attacks; the player can stop immediately with stop_current_action. This controls the player's avatar, so manual movement is limited during the guard. Requires player confirmation.",
                objectSchema()));
        tools.add(tool("get_player_action_status",
                "Read the current status of Nightfall's player movement and interaction controller (navigation, mining, gathering, crafting, sorting, smelting or eating). Read-only; no confirmation required. Reports only the current client-side task state. An idle result does not prove that the last task succeeded; use task-specific inventory/world verification where available. Builder status is reported separately by get_build_status.",
                objectSchema()));
        tools.add(tool("stop_current_action",
                "Immediately stop Nightfall movement, navigation, mining, wood/resource gathering, crafting, smelting, eating and combat, and release all keys controlled by Nightfall. Safe emergency stop.",
                objectSchema()));
        tools.add(tool("resume_saved_build", "Resume a locally saved Nightfall build after Minecraft restart or reconnect. The checkpoint is only prepared when the original server and dimension match; this action requires explicit player confirmation.", objectSchema()));
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
            case "search_structures" -> {
                String structure = args.has("structure") ? requiredString(args, "structure", 32) : "any";
                yield new Action(name, args, AIPlayerController.previewStructureSearch(structure));
            }
            case "locate_structure" -> {
                String structure = requiredString(args, "structure", 96).trim().toLowerCase(Locale.ROOT);
                if (!structure.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                    throw new IllegalArgumentException("Bitte eine gültige Struktur-ID wie minecraft:village angeben.");
                yield new Action(name, args, "Nightfall fragt nach deiner Bestätigung den Server mit /locate structure "
                        + structure + " nach der nächstgelegenen passenden Struktur. Die Koordinaten erscheinen in der Serverantwort; Nightfall teleportiert dich nicht.");
            }
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
            case "list_waypoints" -> new Action(name, args,
                    "Nightfall liest die lokalen Wegpunkte dieses Servers. Namen, Dimensionen und Koordinaten bleiben im Offline-Modus auf dem Minecraft-Client. Es wird nichts geändert und keine Navigation gestartet.\n\n" + waypointSummary());
            case "search_bases" -> new Action(name, args,
                    "Nightfall aktiviert den BaseFinder und beginnt mit dem inkrementellen Scan bereits geladener Chunks. Das Ergebnis ist eine lokale Heuristik, kein bestätigter Basisfund.");
            case "get_base_findings" -> new Action(name, args, baseFindingsSummary());
            case "get_module_observations" -> {
                String module = args.has("module") ? requiredString(args, "module", 64).trim() : null;
                if (module != null && module.isBlank()) throw new IllegalArgumentException("Modulname darf nicht leer sein.");
                yield new Action(name, args, moduleObservationSummary(module));
            }
            case "mark_fake_base" -> {
                String label = requiredString(args, "name", 32).trim();
                if (label.isBlank() || !label.matches("[\\p{L}0-9 _.-]{1,32}"))
                    throw new IllegalArgumentException("Bitte einen kurzen gültigen Fake-Base-Namen angeben.");
                boolean learn = args.has("learn") && requiredBoolean(args, "learn");
                yield new Action(name, args, "Nightfall markiert deinen aktuellen Chunk lokal als FakeBase „" + label + "“."
                        + (learn ? " Das Layout der geladenen BlockEntities wird zusätzlich als Vorlage gelernt." : "")
                        + " Es werden keine Weltblöcke verändert.");
            }
            case "list_fake_base_templates" -> new Action(name, args,
                    "Lokale Fake-Base-Vorlagen: " + (FakeBases.templateNames().isEmpty() ? "keine" : String.join(", ", FakeBases.templateNames())));
            case "set_module_state" -> {
                String moduleName = requiredString(args, "module", 64);
                boolean enabled = requiredBoolean(args, "enabled");
                Module module = findModule(moduleName);
                if (module == null) throw new IllegalArgumentException("Dieses Nightfall-Modul existiert nicht.");
                yield new Action(name, args, "Nightfall wird das Modul „" + module.getName() + "“ "
                        + (enabled ? "aktivieren" : "deaktivieren") + "."
                        + (module.isEnabled() == enabled ? "\nDas Modul hat diesen Zustand bereits." : ""));
            }
            case "get_module_settings" -> new Action(name, args, moduleSettingsSummary(
                    args.has("module") ? requiredString(args, "module", 64) : null));
            case "set_module_setting" -> {
                String moduleName = requiredString(args, "module", 64);
                String settingName = requiredString(args, "setting", 64);
                String value = requiredString(args, "value", 256);
                Module module = findModule(moduleName);
                if (module == null) throw new IllegalArgumentException("Dieses Nightfall-Modul existiert nicht.");
                Setting<?> setting = findSetting(module, settingName);
                if (setting == null) throw new IllegalArgumentException("Diese Einstellung existiert in diesem Modul nicht.");
                yield new Action(name, args, previewSettingChange(module, setting, value));
            }
            case "open_nightfall_menu" -> new Action(name, args, "Öffnet das Nightfall-Menü auf deinem Bildschirm. Es werden keine Einstellungen geändert.");
            case "get_inventory" -> new Action(name, args,
                    "Nightfall liest dein Inventar lokal. Itemnamen und Mengen verlassen im Offline-Modus den Minecraft-Client nicht.");
            case "sort_inventory" -> new Action(name, args, AIPlayerController.previewInventorySort());
            case "get_player_status" -> new Action(name, args,
                    "Nightfall liest Dimension, Position und Lebenspunkte lokal. Diese Daten verlassen im Offline-Modus den Minecraft-Client nicht.");
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
            case "plan_build_materials" -> new Action(name, args, AIBuilder.analyzeStructureMaterials(
                    requiredString(args, "structure", 32),
                    args.has("material") ? requiredString(args, "material", 64) : null));
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
                    SchematicManager.inspectSummary(requiredString(args, "file", 110),
                            args.has("part_x") ? readBoundedInteger(args, "part_x", 0, 15) : 0,
                            args.has("part_y") ? readBoundedInteger(args, "part_y", 0, 15) : 0,
                            args.has("part_z") ? readBoundedInteger(args, "part_z", 0, 15) : 0,
                            args.has("reset_anchor") && args.get("reset_anchor").getAsBoolean()));
            case "repair_schematic" -> new Action(name, args,
                    SchematicManager.previewRepair(requiredString(args, "file", 110)));
            case "build_schematic" -> new Action(name, args,
                    SchematicManager.prepareBuild(requiredString(args, "file", 110),
                            args.has("part_x") ? readBoundedInteger(args, "part_x", 0, 15) : 0,
                            args.has("part_y") ? readBoundedInteger(args, "part_y", 0, 15) : 0,
                            args.has("part_z") ? readBoundedInteger(args, "part_z", 0, 15) : 0,
                            args.has("reset_anchor") && args.get("reset_anchor").getAsBoolean()));
            case "get_schematic_progress" -> new Action(name, args,
                    SchematicManager.progressSummary(requiredString(args, "file", 110)));
            case "verify_schematic_section" -> new Action(name, args,
                    SchematicManager.verifyPlacedSection(requiredString(args, "file", 110),
                            args.has("part_x") ? readBoundedInteger(args, "part_x", 0, 15) : 0,
                            args.has("part_y") ? readBoundedInteger(args, "part_y", 0, 15) : 0,
                            args.has("part_z") ? readBoundedInteger(args, "part_z", 0, 15) : 0));
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
            case "resume_saved_task" -> new Action(name, args, AIPlayerController.previewSavedTask());
            case "clear_saved_task" -> new Action(name, args, AIPlayerController.previewClearSavedTask());
            case "mine_block" -> {
                int x = readBoundedInteger(args, "x", -30_000_000, 30_000_000);
                int y = readBoundedInteger(args, "y", -2_048, 2_048);
                int z = readBoundedInteger(args, "z", -30_000_000, 30_000_000);
                yield new Action(name, args, AIPlayerController.previewMine(x, y, z));
            }
            case "mine_looked_block" -> new Action(name, args, AIPlayerController.previewLookedBlock());
            case "gather_wood" -> new Action(name, args,
                    AIPlayerController.previewGatherWood(requiredString(args, "item", 64),
                            readBoundedInteger(args, "count", 1, 1_024),
                            args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0,
                            Integer.MIN_VALUE));
            case "gather_block" -> new Action(name, args,
                    AIPlayerController.previewGatherBlock(requiredString(args, "item", 64),
                            readBoundedInteger(args, "count", 1, 1_024),
                            args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0));
            case "gather_ore" -> new Action(name, args,
                    AIPlayerController.previewGatherOre(requiredString(args, "item", 64),
                            readBoundedInteger(args, "count", 1, 256),
                            args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0,
                            args.has("target_y") ? readBoundedInteger(args, "target_y", -64, 320) : Integer.MIN_VALUE));
            case "smelt_item" -> new Action(name, args,
                    AIPlayerController.previewSmeltItem(requiredString(args, "item", 64),
                            readBoundedInteger(args, "count", 1, 16)));
            case "craft_item" -> new Action(name, args,
                    AIPlayerController.previewCraftItem(requiredString(args, "item", 64),
                            args.has("count") ? readBoundedInteger(args, "count", 1, 256) : 1));
            case "eat_food" -> new Action(name, args, AIPlayerController.previewEat(
                    args.has("item") ? requiredString(args, "item", 64).trim() : null));
            case "attack_nearest_hostile" -> new Action(name, args, AICombatController.preview());
            case "guard_nearby_hostiles" -> new Action(name, args, AICombatController.previewGuard());
            case "get_player_action_status" -> new Action(name, args,
                    "Liest den aktuellen Status der Spieleraktionen: " + AIPlayerController.status()
                            + ". Das ist eine Momentaufnahme des Clientzustands. Wenn keine Aktion aktiv ist, bedeutet das allein nicht, dass der letzte Auftrag erfolgreich war; prüfe dafür das jeweilige Ergebnis, z. B. Inventar oder Weltzustand.");
            case "stop_current_action" -> new Action(name, args,
                    "Nightfall stoppt sofort Navigation, Bewegung, Blockabbau, Holz- und Rohstoffsammlung, Crafting, Schmelzen, Essen und Kampf und gibt alle eigenen Tasten frei.");
            case "resume_saved_build" -> new Action(name, args, AIBuilder.hasRestoredCheckpoint()
                    ? "Nightfall lädt den gespeicherten Bauplan und setzt ihn nach deiner Bestätigung ab dem letzten serverbestätigten Block fort."
                    : "Kein passender gespeicherter Bauplan ist in dieser Welt bereit.");
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
        return !Set.of("search_blueprints", "list_schematics", "inspect_schematic", "get_module_settings", "open_nightfall_menu",
                "get_build_status", "get_schematic_progress", "verify_schematic_section", "get_base_findings", "get_module_observations", "list_fake_base_templates",
                "get_player_action_status", "pause_build", "stop_build", "stop_movement", "stop_current_action").contains(actionName);
    }

    private static String moduleObservationSummary(String requestedModule) {
        String filter = requestedModule == null ? null : requestedModule.trim();
        StringBuilder out = new StringBuilder("Nightfall-Modulbeobachtungen (nur clientseitig sichtbare Daten):");
        int matched = 0;
        for (Module module : ModuleManager.all()) {
            boolean relevant = module instanceof BaseFinder || module instanceof ChunkEsp
                    || module instanceof IntelligenceScanner || module instanceof EntityScanEsp
                    || module instanceof BlockEntityEsp || module instanceof StaffWatch
                    || module instanceof AntiVanishHints;
            if (!relevant || (filter != null && !module.getName().equalsIgnoreCase(filter))) continue;
            matched++;
            out.append("\n\n").append(module.getName()).append(" [")
                    .append(module.isEnabled() ? "AN" : "AUS").append("]");
            if (!module.isEnabled()) {
                out.append(" — nicht aktiv; keine aktuellen Modulbeobachtungen.");
                continue;
            }
            if (module instanceof BaseFinder) {
                out.append("\n").append(baseFindingsSummary());
            } else if (module instanceof IntelligenceScanner scanner) {
                out.append("\nDeep-Scan-Chunks: ").append(scanner.scannedChunks())
                        .append("; offene Scan-Aufträge: ").append(scanner.pendingChunks())
                        .append("; unterirdische Entity-Hinweise: ").append(scanner.undergroundEntityCount())
                        .append(". Detaillierte SUS-Einstufungen stehen beim BaseFinder.");
            } else if (module instanceof EntityScanEsp entityEsp) {
                List<String> observations = entityEsp.aiObservations(10);
                out.append("\nTreffer: ").append(observations.size()).append(" (max. 10 angezeigt).");
                for (String observation : observations) out.append("\n- ").append(observation);
                if (observations.isEmpty()) out.append("\nKeine passenden Entities im aktuellen Modulbereich.");
            } else if (module instanceof BlockEntityEsp blockEsp) {
                List<String> observations = blockEsp.aiObservations(10);
                out.append("\nTreffer im aktuellen ESP-Cache: ").append(observations.size()).append(" (max. 10 angezeigt).");
                for (String observation : observations) out.append("\n- ").append(observation);
                if (observations.isEmpty()) out.append("\nKeine passenden BlockEntities im aktuellen ESP-Cache.");
            } else if (module instanceof StaffWatch watch) {
                List<String> observations = watch.aiObservations(10);
                out.append("\\nBeobachtete Namen (max. 10):");
                for (String observation : observations) out.append("\\n- ").append(observation);
            } else if (module instanceof AntiVanishHints) {
                StaffWatch watch = ModuleManager.get(StaffWatch.class);
                out.append("\\nGeteilte Tab-/Entity-Verlaufssamples aus StaffWatch (max. 10):");
                for (String observation : watch.aiObservations(10)) out.append("\\n- ").append(observation);
                out.append("\\nAntiVanishHints kann keinen serverseitigen Vanish-Status erkennen.");
            } else if (module instanceof ChunkEsp) {
                out.append("\nVisualisiert die SUS/GONE-Ergebnisse des BaseFinder; Details werden dort ausgegeben.");
            }
            if (out.length() > 10000) return out.append("\n(gekürzt)").toString();
        }
        if (matched == 0) return filter == null
                ? "Keine passenden Nightfall-Module gefunden."
                : "Kein unterstütztes Nightfall-Modul mit dem Namen „" + filter + "“ gefunden.";
        out.append("\n\nHinweis: ESP- und Scanner-Treffer sind lokale Beobachtungen aus bereits geladenen Chunks/Entities. Fehlende Treffer beweisen nicht, dass dort nichts existiert.");
        return out.toString();
    }

    private static String baseFindingsSummary() {
        BaseFinder finder;
        try { finder = ModuleManager.get(BaseFinder.class); }
        catch (RuntimeException unavailable) { return "BaseFinder ist nicht verfügbar."; }
        List<ChunkResult> findings = new ArrayList<>();
        for (ChunkResult result : finder.results()) {
            if (result.status != ChunkResult.Status.CLEAN) findings.add(result);
        }
        findings.sort((a, b) -> {
            int status = Integer.compare(statusRank(b.status), statusRank(a.status));
            return status != 0 ? status : Double.compare(b.score, a.score);
        });
        StringBuilder out = new StringBuilder();
        out.append("BaseFinder ").append(finder.isEnabled() ? "aktiv" : "inaktiv")
                .append("; bekannte Chunks: ").append(finder.resultCount())
                .append("; Scan-Warteschlange: ").append(finder.queueSize())
                .append("; zuletzt gescannt: ").append(finder.lastScanned())
                .append("; Fortschritt des letzten Durchlaufs: ")
                .append(Math.round(finder.progress() * 100)).append("%.");
        if (findings.isEmpty()) return out.append("\nNoch keine SUS-, GONE- oder DECOY-Markierungen.").toString();
        out.append("\nMarkierte Chunks (max. 12):");
        int count = 0;
        for (ChunkResult r : findings) {
            if (count++ >= 12) break;
            out.append("\n- ").append(r.status).append(" Chunk ").append(r.cx).append(",").append(r.cz)
                    .append(" (ungefähres Zentrum X=").append(r.blockX()).append(", Z=").append(r.blockZ())
                    .append("), Score ").append(String.format(Locale.ROOT, "%.1f", r.score))
                    .append(", Datendeckung ").append(Math.round(r.dataCompleteness * 100)).append("%");
            if (r.serverLimited) out.append(", serverseitig maskierte Daten");
            if (!r.reasons.isEmpty()) out.append(": ").append(String.join("; ", r.reasons));
        }
        out.append("\nWichtig: SUS ist nur ein Verdacht anhand clientseitig geladener Daten, kein Beweis für eine Spielerbasis.");
        return out.toString();
    }

    private static int statusRank(ChunkResult.Status status) {
        return switch (status) {
            case SUS -> 3;
            case DECOY -> 2;
            case GONE -> 1;
            case CLEAN -> 0;
        };
    }

    public static String execute(Action action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return "Aktion fehlgeschlagen: Du bist nicht in einer Welt.";
        JsonObject args = action.arguments();

        return switch (action.name()) {
            case "resume_saved_task" -> AIPlayerController.resumeSavedTask();
            case "clear_saved_task" -> AIPlayerController.clearSavedTask();
            case "search_structures" -> AIPlayerController.startStructureSearch(
                    args.has("structure") ? args.get("structure").getAsString() : "any");
            case "locate_structure" -> {
                if (mc.player.networkHandler == null) yield "Aktion fehlgeschlagen: Keine Serververbindung.";
                String structure = args.get("structure").getAsString();
                try {
                    mc.player.networkHandler.sendChatCommand("locate structure " + structure);
                    yield "Der Befehl /locate structure " + structure
                            + " wurde gesendet. Prüfe die Serverantwort im Chat; die Suche hängt von Serverversion und Berechtigungen ab. Nightfall wartet auf die passende Koordinatenantwort und versucht danach, einen sicheren Weg zum Fundort zu planen.";
                } catch (RuntimeException error) {
                    yield "Der Struktur-Suchbefehl konnte nicht gesendet werden. Prüfe deine Verbindung.";
                }
            }
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
            case "list_waypoints" -> waypointSummary();
            case "search_bases" -> {
                BaseFinder finder = ModuleManager.get(BaseFinder.class);
                if (!finder.isEnabled()) finder.setEnabled(true);
                yield "BaseFinder ist " + (finder.isEnabled() ? "aktiv" : "nicht aktiv")
                        + ". Der Scan verarbeitet schrittweise nur bereits geladene Chunks. Frage mit get_base_findings nach den aktuellen Ergebnissen.";
            }
            case "get_base_findings" -> baseFindingsSummary();
            case "get_module_observations" -> moduleObservationSummary(
                    args.has("module") ? args.get("module").getAsString() : null);
            case "mark_fake_base" -> {
                int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
                String label = args.get("name").getAsString().trim();
                Waypoint marker = WaypointManager.add(label, FakeBases.CATEGORY,
                        cx * 16 + 8, mc.player.getBlockY(), cz * 16 + 8);
                String learned = "";
                if (args.has("learn") && args.get("learn").getAsBoolean()) {
                    var chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    learned = " " + FakeBases.learn(label, chunk);
                }
                yield "FakeBase/Decoy „" + marker.name + "“ lokal in Chunk " + cx + "," + cz
                        + " markiert (Blockzentrum X=" + marker.x + ", Z=" + marker.z + ")." + learned;
            }
            case "list_fake_base_templates" -> {
                List<String> names = FakeBases.templateNames();
                yield names.isEmpty() ? "Es sind noch keine Fake-Base-Vorlagen gelernt."
                        : "Gelernte lokale Fake-Base-Vorlagen: " + String.join(", ", names);
            }
            case "set_module_state" -> {
                Module module = findModule(args.get("module").getAsString());
                if (module == null) yield "Aktion abgebrochen: Das Modul ist nicht mehr verfügbar.";
                boolean enabled = args.get("enabled").getAsBoolean();
                if (module.isEnabled() == enabled) yield "Das Modul „" + module.getName() + "“ war bereits "
                        + (enabled ? "aktiv" : "inaktiv") + ".";
                try {
                    module.setEnabled(enabled);
                    Config.save();
                    yield "Modul „" + module.getName() + "“ wurde " + (enabled ? "aktiviert" : "deaktiviert") + " und gespeichert.";
                } catch (RuntimeException | Error failure) {
                    yield "Nightfall konnte das Modul nicht " + (enabled ? "aktivieren" : "deaktivieren")
                            + ": " + failure.getClass().getSimpleName() + ".";
                }
            }
            case "get_module_settings" -> moduleSettingsSummary(args.has("module") ? args.get("module").getAsString() : null);
            case "set_module_setting" -> applyModuleSetting(args.get("module").getAsString(), args.get("setting").getAsString(), args.get("value").getAsString());
            case "open_nightfall_menu" -> {
                mc.setScreen(new ClickGuiScreen());
                yield "Nightfall-Menü geöffnet. Du kannst es auch mit deiner GUI-Taste öffnen.";
            }
            case "get_inventory" -> inventorySummary(mc);
            case "plan_build_materials" -> AIBuilder.analyzeStructureMaterials(
                    requiredString(args, "structure", 32),
                    args.has("material") ? requiredString(args, "material", 64) : null);
            case "sort_inventory" -> AIPlayerController.sortInventory();
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
            case "resume_saved_build" -> AIBuilder.startPreparedStructure();
            case "build_structure", "build_starter_shelter", "build_custom_structure" -> AIBuilder.startPreparedStructure();
            case "search_blueprints" -> BlueprintCatalog.search(args.get("query").getAsString());
            case "list_schematics" -> SchematicManager.listForAI() + "\n\n" + SchematicManager.communityCatalog();
            case "inspect_schematic" -> SchematicManager.inspectSummary(args.get("file").getAsString());
            case "repair_schematic" -> SchematicManager.repairToSupportedSchematic(args.get("file").getAsString());
            case "build_schematic" -> AIBuilder.startPreparedStructure();
            case "get_schematic_progress" -> SchematicManager.progressSummary(requiredString(args, "file", 110));
            case "verify_schematic_section" -> SchematicManager.verifyPlacedSection(
                    requiredString(args, "file", 110),
                    args.has("part_x") ? readBoundedInteger(args, "part_x", 0, 15) : 0,
                    args.has("part_y") ? readBoundedInteger(args, "part_y", 0, 15) : 0,
                    args.has("part_z") ? readBoundedInteger(args, "part_z", 0, 15) : 0);
            case "walk_forward" -> AIPlayerController.walkForward(readBoundedInteger(args, "ticks", 1, 100));
            case "stop_movement" -> AIPlayerController.stop("Auf deinen Befehl gestoppt.");
            case "navigate_to" -> AIPlayerController.navigateTo(
                    args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
            case "mine_block" -> AIPlayerController.mineBlock(
                    args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
            case "mine_looked_block" -> AIPlayerController.mineLookedBlock();
            case "gather_wood" -> AIPlayerController.gatherWood(args.get("item").getAsString(),
                    readBoundedInteger(args, "count", 1, 1_024),
                    args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0,
                    Integer.MIN_VALUE);
            case "gather_block" -> AIPlayerController.gatherBlock(args.get("item").getAsString(),
                    readBoundedInteger(args, "count", 1, 1_024),
                    args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0);
            case "gather_ore" -> AIPlayerController.gatherOre(args.get("item").getAsString(),
                    readBoundedInteger(args, "count", 1, 256),
                    args.has("duration_seconds") ? readBoundedInteger(args, "duration_seconds", 0, 1_200) : 0,
                    args.has("target_y") ? readBoundedInteger(args, "target_y", -64, 320) : Integer.MIN_VALUE);
            case "smelt_item" -> AIPlayerController.smeltItem(args.get("item").getAsString(),
                    readBoundedInteger(args, "count", 1, 16));
            case "craft_item" -> AIPlayerController.craftItem(args.get("item").getAsString(),
                    args.has("count") ? readBoundedInteger(args, "count", 1, 256) : 1);
            case "eat_food" -> AIPlayerController.eatFood(args.has("item") ? args.get("item").getAsString() : null);
            case "attack_nearest_hostile" -> AICombatController.start();
            case "guard_nearby_hostiles" -> AICombatController.startGuard();
            case "get_player_action_status" -> {
                yield "Status der Spieleraktionen: " + AIPlayerController.status()
                        + ". Clientseitige Momentaufnahme; kein Nachweis für den Erfolg eines bereits beendeten Auftrags.";
            }
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

    private static String waypointSummary() {
        List<Waypoint> waypoints = WaypointManager.all();
        if (waypoints.isEmpty()) return "Für diesen Server sind noch keine Nightfall-Wegpunkte gespeichert.";

        StringBuilder out = new StringBuilder("Gespeicherte Nightfall-Wegpunkte für diesen Server (maximal 30 angezeigt):");
        int shown = 0;
        for (Waypoint waypoint : waypoints) {
            if (waypoint == null) continue;
            if (shown++ >= 30) break;
            out.append("\n- ").append(waypoint.name == null || waypoint.name.isBlank() ? "(ohne Namen)" : waypoint.name)
                    .append(" [").append(waypoint.category == null ? "Default" : waypoint.category).append("]")
                    .append(": ").append(waypoint.dim == null ? "Dimension unbekannt" : waypoint.dim)
                    .append(" · X=").append(waypoint.x).append(", Y=").append(waypoint.y).append(", Z=").append(waypoint.z);
            if (out.length() > 5_500) {
                out.append("\n(gekürzt)");
                break;
            }
        }
        if (waypoints.size() > 30) out.append("\nWeitere Wegpunkte sind in der lokalen Wegpunktliste sichtbar.");
        return out.toString();
    }

    private static String playerStatus(MinecraftClient mc) {
        var pos = mc.player.getBlockPos();
        return "Spielerstatus:"
                + "\nDimension: " + mc.world.getRegistryKey().getValue()
                + "\nPosition: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ()
                + "\nLeben: " + String.format(Locale.ROOT, "%.1f", mc.player.getHealth())
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

    private static Setting<?> findSetting(Module module, String name) {
        for (Setting<?> setting : module.getSettings())
            if (setting.getName().equalsIgnoreCase(name)) return setting;
        return null;
    }

    private static String moduleSettingsSummary(String moduleName) {
        StringBuilder out = new StringBuilder("Nightfall ClickGUI-Einstellungen:");
        int modules = 0;
        for (Module module : ModuleManager.all()) {
            if (moduleName != null && !module.getName().equalsIgnoreCase(moduleName)) continue;
            if (++modules > 80) { out.append("\n(weitere Module ausgelassen)"); break; }
            out.append("\n\n").append(module.getName()).append(" [").append(module.isEnabled() ? "AN" : "AUS").append("]");
            for (Setting<?> setting : module.getSettings()) {
                out.append("\n- ").append(setting.getName()).append(" = ");
                if (setting instanceof BoolSetting bool) out.append(bool.get());
                else if (setting instanceof NumberSetting number) out.append(number.display()).append(" (").append(number.min).append(" bis ").append(number.max).append(", Schritt ").append(number.step).append(")");
                else if (setting instanceof ModeSetting mode) out.append(mode.get()).append(" (Optionen: ").append(String.join(", ", mode.getModes())).append(")");
                else if (setting instanceof ColorSetting color) out.append(String.format(Locale.ROOT, "#%06X", color.rgb()));
                else if (setting instanceof StringSetting string) out.append(string.get());
                else out.append("(nicht direkt durch KI editierbar)");
                if (out.length() > 12000) return out.append("\n(gekürzt)").toString();
            }
        }
        if (modules == 0) return "Kein passendes Nightfall-Modul gefunden.";
        return out.toString();
    }

    private static String previewSettingChange(Module module, Setting<?> setting, String raw) {
        String value = raw.trim();
        if (setting instanceof BoolSetting) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")
                    && !value.equalsIgnoreCase("an") && !value.equalsIgnoreCase("aus")
                    && !value.equalsIgnoreCase("on") && !value.equalsIgnoreCase("off"))
                throw new IllegalArgumentException("Bitte true/false oder an/aus für diese Einstellung verwenden.");
            return "Nightfall ändert nach deiner Bestätigung „" + module.getName() + "“ → „" + setting.getName()
                    + "“ von " + setting.get() + " auf " + (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("an") || value.equalsIgnoreCase("on"));
        }
        if (setting instanceof NumberSetting number) {
            double parsed;
            try { parsed = Double.parseDouble(value.replace(',', '.')); }
            catch (NumberFormatException invalid) { throw new IllegalArgumentException("Bitte eine Zahl für diese Einstellung angeben."); }
            if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Ungültiger Zahlenwert.");
            double bounded = Math.max(number.min, Math.min(number.max, parsed));
            double stepped = Math.max(number.min, Math.min(number.max, Math.round(bounded / number.step) * number.step));
            return "Nightfall ändert nach deiner Bestätigung „" + module.getName() + "“ → „" + setting.getName()
                    + "“ von " + number.display() + " auf " + stepped + " (erlaubt: " + number.min + " bis " + number.max + ").";
        }
        if (setting instanceof ModeSetting mode) {
            String[] options = modeOptions(mode);
            if (java.util.Arrays.stream(options).noneMatch(option -> option.equalsIgnoreCase(value)))
                throw new IllegalArgumentException("Ungültiger Modus. Erlaubt: " + String.join(", ", options));
            return "Nightfall ändert nach deiner Bestätigung „" + module.getName() + "“ → „" + setting.getName()
                    + "“ von " + mode.get() + " auf " + value + ".";
        }
        if (setting instanceof StringSetting) {
            if (value.length() > 256) throw new IllegalArgumentException("Der Text ist zu lang.");
            return "Nightfall ändert nach deiner Bestätigung „" + module.getName() + "“ → „" + setting.getName() + "“ auf: " + value;
        }
        if (setting instanceof ColorSetting) {
            String hex = value.startsWith("#") ? value.substring(1) : value;
            if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Farbe bitte als #RRGGBB angeben.");
            return "Nightfall ändert nach deiner Bestätigung „" + module.getName() + "“ → „" + setting.getName() + "“ auf #" + hex.toUpperCase(Locale.ROOT) + ".";
        }
        throw new IllegalArgumentException("Dieser Einstellungstyp wird noch nicht von Nightfall AI unterstützt.");
    }

    private static String[] modeOptions(ModeSetting setting) {
        return setting.getModes();
    }

    private static String applyModuleSetting(String moduleName, String settingName, String raw) {
        Module module = findModule(moduleName);
        if (module == null) return "Modul nicht mehr verfügbar.";
        Setting<?> setting = findSetting(module, settingName);
        if (setting == null) return "Einstellung nicht mehr verfügbar.";
        previewSettingChange(module, setting, raw);
        String value = raw.trim();
        if (setting instanceof BoolSetting bool) bool.set(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("an") || value.equalsIgnoreCase("on"));
        else if (setting instanceof NumberSetting number) number.set(Double.parseDouble(value.replace(',', '.')));
        else if (setting instanceof ModeSetting mode) {
            String canonical = java.util.Arrays.stream(mode.getModes()).filter(option -> option.equalsIgnoreCase(value)).findFirst().orElseThrow();
            mode.set(canonical);
        }
        else if (setting instanceof StringSetting string) string.set(value);
        else if (setting instanceof ColorSetting color) color.set(Integer.parseInt(value.startsWith("#") ? value.substring(1) : value, 16));
        else return "Dieser Einstellungstyp wird nicht unterstützt.";
        Config.save();
        return "Einstellung „" + module.getName() + "“ → „" + setting.getName() + "“ wurde auf " + setting.get() + " gesetzt und in Nightfall gespeichert.";
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
        if (blocks == null || blocks.isEmpty() || blocks.size() > 512)
            throw new IllegalArgumentException("Ein eigener Bauplan muss 1 bis 512 Blöcke enthalten.");
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
        int maximum = 31;
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

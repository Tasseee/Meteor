package dev.nightfall.ai;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps local-model prompts small by exposing the read-only client index plus
 * action schemas relevant to the player's latest request. This is prompt
 * routing, not authorization: the action registry and OfflineAgentActionBridge
 * still validate every proposed step.
 */
public final class LocalAIToolRouter {
    private static final Set<String> ALWAYS_READ_ONLY = Set.of(
            "get_player_status",
            "get_inventory",
            "get_module_observations",
            "get_player_action_status",
            "get_build_status",
            "list_client_modules",
            "get_module_settings",
            "list_waypoints",
            "get_base_findings",
            "list_fake_base_templates",
            "list_schematics",
            "search_blueprints"
    );

    private static final Set<String> SAFE_STOPS = Set.of(
            "stop_current_action",
            "stop_movement",
            "pause_build",
            "stop_build"
    );

    private LocalAIToolRouter() {}

    public static Set<String> selectTools(List<LocalAIPlanner.Turn> history) {
        String request = lastUserTurns(history).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> selected = new LinkedHashSet<>(ALWAYS_READ_ONLY);
        selected.addAll(SAFE_STOPS);

        boolean inventory = containsAny(request, "inventar", "material", "bestand", "rüstung",
                "ruestung", "werkzeug", "gegenstand", "item", "sortier", "sortiere",
                "inventory", "armour", "armor", "gear", "sort inventory", "vorräte", "vorrat",
                "ressourcen", "materialbedarf", "materialien prüfen", "materialien pruefen",
                "bestände", "bestaende", "bestand prüfen", "bestand pruefen", "wie viele blöcke",
                "wie viele bloecke", "hotbar", "inventarplatz", "inventarplätze", "inventarplaetze",
                "how many blocks", "how many items");
        boolean building = containsAny(request, "bau", "baue", "bauen", "haus", "hausbau",
                "schutzhaus", "wachturm", "bauplan", "blueprint", "schematic", "build",
                "house", "tower", "structure", "etage", "stockwerk", "geschoss", "mehrstöckig",
                "mehrstoeckig", "burg", "schloss", "mauer", "brücke", "bruecke", "farmhaus",
                "gebäude", "gebaeude", "bauabschnitt", "baufortschritt", "materialliste",
                "materialien", "baustufe", "bauetappe", "castle", "fortress", "building",
                "construct", "floor", "floors", "story", "stories", "storey", "storeys",
                "multi-storey", "two-story", "two-storey");
        boolean gathering = containsAny(request, "sammel", "sammle", "farm", "farmen", "holz",
                "stamm", "erz", "kohle", "eisen", "stein", "abbau", "mine", "craft",
                "crafting", "herstell", "schmelz", "ofen", "essen", "hunger",
                "gather", "collect", "wood", "log", "ore", "coal", "iron", "stone",
                "smelt", "furnace", "eat", "food", "abbauen", "sammeln",
                "beschaffe", "beschaffen", "herstellen", "herstelle", "schmelzen",
                "nahrung", "verpflegung", "werkbank", "rohstoffe", "ressourcen sammeln",
                "grinden", "grind", "minen", "mine mir");
        boolean navigation = containsAny(request, "navig", "geh zu", "gehe zu", "lauf", "laufe",
                "beweg", "wegpunkt", "waypoint", "route", "struktur", "dorf", "festung",
                "biom", "dschungel", "wüste", "wueste", "tempel", "antike stadt", "stronghold",
                "bastion", "trial chamber", "navigate", "go to", "walk", "locate", "biome",
                "jungle", "desert", "temple", "village", "fortress", "koordinaten",
                "wo ist", "finde", "suche nach", "entfernung", "position", "erkunde",
                "erkunden", "entdecken", "teleportiere", "teleport", "rtp", "zufalls-teleport",
                "zufallsteleport");
        boolean randomTeleport = containsAny(request, "rtp", "zufalls-teleport",
                "zufallsteleport", "random teleport", "teleportiere mich zufällig");
        boolean scanning = containsAny(request, "base", "basis", "scanner", "scan", "chunk",
                "fund", "finden", "suche", "such", "heatmap", "analyse", "analysiere",
                "spielerbasis", "versteckte basis", "truhen", "kisten", "blockentities",
                "block entities", "beobachtungen", "scanergebnis", "scannergebnisse",
                "fakebase", "fake base", "decoy", "staff", "vanish", "unsichtbar",
                "spieler finden", "spielersuche");
        boolean modules = containsAny(request, "modul", "module", "esp", "clickgui",
                "einstellung", "einstellungen", "setting", "settings", "aktiviere",
                "aktivieren", "aktivier", "deaktiviere", "deaktivieren", "schalte",
                "einschalt", "ausschalt", "theme", "hud", "enable", "disable", "toggle",
                "turn on", "turn off", "konfig", "configuration", "konfigur",
                "verändere", "veraendere", "ändern", "aendern", "anpassen",
                "optimiere", "optimier", "mach an", "mach aus", "menü öffnen",
                "menue oeffnen", "click gui", "client-menü", "client menü",
                "client menu", "module einstellen", "client einstellen");
        boolean schematics = containsAny(request, "schematic", "schematics", "bauplan",
                "blueprint", "struktur vergleichen", "reparier", "repariere",
                "schematic prüfen", "schematic pruefen", "bauvorlage", "vorlage bauen");
        boolean combat = containsAny(request, "feindliche mobs", "feindlicher mob", "monster",
                "skelett", "zombie", "creeper", "bewache", "verteidige mich gegen mobs",
                "mob angreifen", "mob abwehren", "greif den mob", "greife mobs",
                "beschütze mich", "beschuetze mich", "bewach mich", "wache halten",
                "hostile", "guard me", "attack the mob");
        boolean serverCommand = request.contains("/locate") || request.contains("/rtp")
                || request.contains("/home") || request.contains("/spawn")
                || request.contains("serverbefehl") || request.contains("server command")
                || request.contains("führe den befehl") || request.contains("fuehre den befehl");
        boolean taskManagement = containsAny(request, "agent", "auftrag", "aufträge",
                "auftraege", "task", "fortsetzen", "weiter machen", "abbrechen",
                "verwerfen", "checkpoint", "gespeicherte aufgabe", "gespeicherten auftrag");

        if (inventory) {
            add(selected, "get_inventory", "get_player_status", "sort_inventory",
                    "plan_build_materials", "get_module_settings");
        }
        if (building) {
            add(selected, "get_inventory", "get_player_status", "plan_build_materials",
                    "search_blueprints", "build_structure", "build_starter_shelter",
                    "build_custom_structure", "inspect_schematic", "list_schematics",
                    "build_schematic", "get_schematic_progress", "verify_schematic_section",
                    "resume_saved_build", "pause_build", "resume_build", "stop_build",
                    "gather_wood", "gather_block", "gather_ore", "craft_item", "smelt_item");
        }
        if (schematics) {
            add(selected, "search_blueprints", "list_schematics", "inspect_schematic",
                    "repair_schematic", "build_schematic", "get_schematic_progress",
                    "verify_schematic_section");
        }
        if (gathering) {
            add(selected, "gather_wood", "gather_block", "gather_ore", "craft_item",
                    "smelt_item", "eat_food", "sort_inventory", "resume_saved_task",
                    "mine_block", "mine_looked_block");
        }
        if (navigation) {
            add(selected, "locate_structure", "locate_biome", "search_structures",
                    "navigate_to", "walk_forward", "add_waypoint", "list_waypoints",
                    "resume_saved_task", "clear_saved_task");
        }
        if (randomTeleport) add(selected, "random_teleport");
        if (containsAny(request, "wegpunkt", "waypoint", "markierung", "markiere")) {
            add(selected, "add_waypoint", "list_waypoints");
        }
        if (scanning) {
            add(selected, "search_bases", "get_base_findings", "get_module_observations",
                    "mark_fake_base", "list_fake_base_templates");
        }
        if (modules) {
            add(selected, "list_client_modules", "get_module_settings", "set_module_state",
                    "set_module_setting", "open_nightfall_menu", "get_module_observations");
        }
        if (combat) add(selected, "attack_nearest_hostile", "guard_nearby_hostiles");
        if (serverCommand) {
            add(selected, "run_server_command", "locate_structure", "locate_biome",
                    "random_teleport");
        }
        if (taskManagement) {
            add(selected, "get_player_action_status", "get_build_status", "resume_saved_task",
                    "clear_saved_task", "resume_saved_build", "pause_build", "resume_build",
                    "stop_build", "stop_current_action");
        }

        // A capability overview is read-only and never exposes mutating action schemas.
        if (isCapabilityQuestion(history)) {
            add(selected, "list_client_modules", "get_module_settings", "list_waypoints",
                    "get_base_findings", "list_fake_base_templates", "search_blueprints",
                    "list_schematics");
        }
        return Set.copyOf(selected);
    }

    public static boolean isCapabilityQuestion(List<LocalAIPlanner.Turn> history) {
        String request = lastUserTurns(history).toLowerCase(Locale.ROOT).trim();
        if (Set.of("hilfe", "help", "/nf agent help").contains(request)) return true;
        return containsAny(request, "was kannst du", "was kannst du alles", "welche funktionen",
                "welche skills", "was ist möglich", "was ist moeglich",
                "zeige deine funktionen", "liste deine funktionen", "fähigkeiten",
                "faehigkeiten", "what can you do", "what are your capabilities",
                "list your functions", "show your capabilities", "capabilities overview",
                "alle funktionen", "funktionen vom client", "client funktionen",
                "was kann der client", "welche module", "alle module", "module anzeigen",
                "modulliste", "modul liste", "modulübersicht", "moduluebersicht",
                "welche module gibt es", "all features", "list all modules",
                "show available modules");
    }

    private static String lastUserTurns(List<LocalAIPlanner.Turn> history) {
        if (history == null || history.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        int found = 0;
        for (int i = history.size() - 1; i >= 0 && found < 2; i--) {
            LocalAIPlanner.Turn turn = history.get(i);
            if (turn == null || !"user".equals(turn.role()) || turn.content() == null) continue;
            String content = turn.content();
            if (content.length() > 700) content = content.substring(0, 700);
            text.insert(0, content + " ");
            found++;
        }
        return text.toString();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static void add(Set<String> target, String... names) {
        for (String name : names) target.add(name);
    }
}

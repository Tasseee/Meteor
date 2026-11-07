package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LocalAIToolRouterTest {
    private static List<LocalAIPlanner.Turn> request(String text) {
        return List.of(new LocalAIPlanner.Turn("user", text));
    }

    @Test
    void buildRequestGetsBuildAndMaterialToolsButNotUnrelatedCombatTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(
                request("Baue mir ein kleines Haus und prüfe zuerst meine Materialien."));

        assertTrue(tools.contains("plan_build_materials"));
        assertTrue(tools.contains("build_starter_shelter"));
        assertTrue(tools.contains("get_inventory"));
        assertFalse(tools.contains("attack_nearest_hostile"));
        assertFalse(tools.contains("run_server_command"));
    }

    @Test
    void customBuildRequestGetsCustomStructureTool() {
        Set<String> tools = LocalAIToolRouter.selectTools(
                request("Entwirf und baue mir eine kleine eigene Burg."));

        assertTrue(tools.contains("build_custom_structure"));
        assertTrue(tools.contains("build_schematic"));
        assertTrue(tools.contains("inspect_schematic"));
    }

    @Test
    void customBuildPromptKeepsCoordinateAndConnectivityGuidance() {
        String prompt = LocalAIPlanner.buildPrompt(
                request("Entwirf und baue mir eine kleine eigene Burg."), null);

        assertTrue(prompt.contains("connected face-to-face"));
        assertTrue(prompt.contains("\"maximum\":31"));
        assertTrue(prompt.contains("höchstens 512 Blöcken"));
        assertTrue(prompt.contains("read-only-Materialplanung"));
    }

    @Test
    void capabilityQuestionIsRecognizedWithoutExposingMutatingActions() {
        List<LocalAIPlanner.Turn> turns = request("Was kannst du alles?");
        Set<String> tools = LocalAIToolRouter.selectTools(turns);

        assertTrue(LocalAIToolRouter.isCapabilityQuestion(turns));
        assertTrue(tools.contains("get_player_status"));
        assertFalse(tools.contains("build_structure"));
        assertFalse(tools.contains("run_server_command"));
    }

    @Test
    void capabilityCatalogCoversEveryRegisteredActionAndReportsBridgeSupport() {
        String catalog = LocalAIPlanner.capabilityCatalog();
        var supported = OfflineAgentActionBridge.supportedActions();

        var definitions = AIActionRegistry.toolDefinitions();
        assertEquals(definitions.size(), supported.size(),
                "Every registered action must have a compatible offline parameter schema");
        for (var definition : definitions) {
            var function = definition.getAsJsonObject().getAsJsonObject("function");
            String name = function.get("name").getAsString();
            assertTrue(catalog.contains("- " + name + " ["), "Missing capability: " + name);
            assertTrue(supported.containsKey(name), "Action not accepted by offline bridge: " + name);
            assertTrue(catalog.contains("- " + name + " [offline unterstützt]"),
                    "Action is incorrectly labeled unsupported: " + name);
        }
    }

    @Test
    void capabilityPromptContainsTheCompleteRegistryCatalog() {
        String prompt = LocalAIPlanner.buildPrompt(request("Was kannst du alles?"), null);
        for (var definition : AIActionRegistry.toolDefinitions()) {
            String name = definition.getAsJsonObject().getAsJsonObject("function")
                    .get("name").getAsString();
            assertTrue(prompt.contains("- " + name + " ["), "Prompt omitted " + name);
        }
        assertTrue(prompt.contains("VOLLSTÄNDIGER FÄHIGKEITSKATALOG"));
    }

    @Test
    void customAndResumedBuildsWaitForBuilderCompletion() {
        assertEquals(OfflineAgentActionBridge.VerificationKind.BUILDER,
                OfflineAgentActionBridge.verificationKindFor("build_custom_structure"));
        assertEquals(OfflineAgentActionBridge.VerificationKind.BUILDER,
                OfflineAgentActionBridge.verificationKindFor("resume_build"));
    }

    @Test
    void multiFloorBuildRequestGetsMaterialAndBuildTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(request(
                "Prüfe meine Vorräte und plane ein zweistöckiges Haus mit Materialbedarf und Bauabschnitten."));
        assertTrue(tools.contains("get_inventory"));
        assertTrue(tools.contains("plan_build_materials"));
        assertTrue(tools.contains("build_custom_structure"));
        assertTrue(tools.contains("build_schematic"));
        assertTrue(tools.contains("gather_wood"));
        assertTrue(tools.contains("craft_item"));
    }

    @Test
    void waypointRequestGetsWaypointTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(request("Setze hier einen Wegpunkt und zeige meine Wegpunkte."));
        assertTrue(tools.contains("add_waypoint"));
        assertTrue(tools.contains("list_waypoints"));
    }

    @Test
    void moduleSettingRequestGetsReadAndWriteTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(
                request("Prüfe die Einstellungen des ESP-Moduls und ändere die Reichweite."));
        assertTrue(tools.contains("get_module_settings"));
        assertTrue(tools.contains("set_module_setting"));
        assertTrue(tools.contains("get_module_observations"));
    }

    @Test
    void navigationRequestGetsNavigationTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(
                request("Finde das nächste Dschungel-Biom und setze danach einen Wegpunkt."));

        assertTrue(tools.contains("locate_biome"));
        assertTrue(tools.contains("navigate_to"));
        assertTrue(tools.contains("add_waypoint"));
        assertFalse(tools.contains("build_schematic"));
    }

    @Test
    void ordinaryStatusQuestionKeepsOnlyReadOnlyAndStopTools() {
        Set<String> tools = LocalAIToolRouter.selectTools(request("Wie ist mein aktueller Status?"));

        assertTrue(tools.contains("get_player_status"));
        assertTrue(tools.contains("get_inventory"));
        assertTrue(tools.contains("get_player_action_status"));
        assertFalse(tools.contains("build_structure"));
        assertFalse(tools.contains("sort_inventory"));
        assertFalse(tools.contains("run_server_command"));
    }

    @Test
    void followUpUsesThePreviousUserIntent() {
        Set<String> tools = LocalAIToolRouter.selectTools(List.of(
                new LocalAIPlanner.Turn("user", "Plane den Bau eines Starterhauses."),
                new LocalAIPlanner.Turn("assistant", "Ich prüfe die Materialien."),
                new LocalAIPlanner.Turn("user", "Dann starte es.")
        ));

        assertTrue(tools.contains("build_starter_shelter"));
        assertTrue(tools.contains("plan_build_materials"));
    }

    @Test
    void serverCommandsAreNotExposedWithoutAnExplicitServerCommandRequest() {
        Set<String> ordinary = LocalAIToolRouter.selectTools(request("Finde ein Dorf in der Umgebung."));
        Set<String> explicit = LocalAIToolRouter.selectTools(request("Führe /locate structure minecraft:village aus."));

        assertFalse(ordinary.contains("run_server_command"));
        assertTrue(explicit.contains("run_server_command"));
    }
}

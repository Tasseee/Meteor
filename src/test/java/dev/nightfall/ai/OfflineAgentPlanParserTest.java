package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAgentPlanParserTest {
    @Test
    void parsesMultiStepGoalsAndDependencyGraphs() {
        String json = """
                {
                  "description": "Sammle Holz und craft Bretter",
                  "priority": 12,
                  "steps": [
                    {
                      "id": "gather",
                      "action": "gather_wood",
                      "parameters": {"item": "minecraft:oak_log", "count": 8},
                      "maxAttempts": 2
                    },
                    {
                      "id": "craft",
                      "action": "craft_item",
                      "parameters": {"item": "minecraft:oak_planks", "count": 32},
                      "dependsOn": ["gather"]
                    }
                  ]
                }
                """;

        OfflineAgentCore.Goal goal = OfflineAgentPlanParser.parse(json, "plan-1");

        assertEquals("plan-1", goal.id());
        assertEquals(12, goal.priority());
        assertEquals(2, goal.steps().size());
        assertEquals("gather", goal.steps().get(1).dependsOn().get(0));
        assertEquals("minecraft:oak_planks", goal.steps().get(1).parameters().get("item"));
    }

    @Test
    void keepsStructuredArrayParametersAsJsonForLocalRegistryValidation() {
        String json = """
                {
                  "description": "Kleines Podest",
                  "steps": [
                    {
                      "id": "build",
                      "action": "build_custom_structure",
                      "parameters": {
                        "name": "podest",
                        "blocks": [
                          {"x": 0, "y": 0, "z": 0, "material": "minecraft:stone"},
                          {"x": 1, "y": 0, "z": 0, "material": "minecraft:stone"}
                        ]
                      }
                    }
                  ]
                }
                """;

        OfflineAgentCore.Goal goal = OfflineAgentPlanParser.parse(json, "plan-2");

        assertTrue(goal.steps().get(0).parameters().get("blocks").startsWith("["));
        assertTrue(goal.steps().get(0).parameters().get("blocks").contains("minecraft:stone"));
    }

    @Test
    void rejectsDependencyCyclesAndUnknownStepReferences() {
        String cycle = """
                {"steps":[
                  {"id":"a","action":"gather_wood","dependsOn":["b"]},
                  {"id":"b","action":"craft_item","dependsOn":["a"]}
                ]}
                """;
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(cycle, "cycle"));

        String unknown = """
                {"steps":[
                  {"id":"a","action":"gather_wood","dependsOn":["missing"]}
                ]}
                """;
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(unknown, "unknown"));
    }

    @Test
    void rejectsUnknownFieldsNullParametersAndInvalidNumbers() {
        String unknownRoot = """
                {"model":"offline","steps":[{"id":"a","action":"gather_wood"}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(unknownRoot, "unknown-root"));

        String nullParameter = """
                {"steps":[{"id":"a","action":"gather_wood","parameters":{"item":null}}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(nullParameter, "null-param"));

        String fractionalPriority = """
                {"priority":1.5,"steps":[{"id":"a","action":"gather_wood"}]}
                """;
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(fractionalPriority, "fraction"));
    }

    @Test
    void rejectsOversizedPlansAndExcessiveStepCounts() {
        String oversized = " ".repeat(OfflineAgentPlanParser.MAX_PLAN_JSON_CHARS + 1);
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(oversized, "large"));

        StringBuilder json = new StringBuilder("{\"steps\":[");
        for (int i = 0; i <= OfflineAgentCore.MAX_STEPS_PER_GOAL; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"step").append(i)
                    .append("\",\"action\":\"gather_wood\"}");
        }
        json.append("]}");
        assertThrows(IllegalArgumentException.class, () -> OfflineAgentPlanParser.parse(json.toString(), "too-many"));
    }
}

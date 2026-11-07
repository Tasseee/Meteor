package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalAIResponseParserTest {
    @Test
    void parsesPlainLocalAnswer() {
        LocalAIResponseParser.Response response = LocalAIResponseParser.parse(
                "{\"mode\":\"answer\",\"text\":\"Du bist im Biom Ebene.\"}");

        assertInstanceOf(LocalAIResponseParser.Answer.class, response);
        assertEquals("Du bist im Biom Ebene.",
                ((LocalAIResponseParser.Answer) response).text());
    }

    @Test
    void acceptsJsonWrappedInMarkdownFence() {
        LocalAIResponseParser.Response response = LocalAIResponseParser.parse("""
                ```json
                {"mode":"answer","text":"Hallo aus Nightfall."}
                ```
                """);

        assertEquals("Hallo aus Nightfall.",
                ((LocalAIResponseParser.Answer) response).text());
    }

    @Test
    void parsesAValidatedMultiStepPlan() {
        LocalAIResponseParser.Response response = LocalAIResponseParser.parse("""
                {
                  "mode": "plan",
                  "description": "Sammle Holz und stelle Bretter her",
                  "priority": 3,
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
                """);

        assertInstanceOf(LocalAIResponseParser.Plan.class, response);
        OfflineAgentCore.Goal goal = ((LocalAIResponseParser.Plan) response).goal();
        assertEquals("Sammle Holz und stelle Bretter her", goal.description());
        assertEquals(2, goal.steps().size());
        assertEquals("gather", goal.steps().get(1).dependsOn().get(0));
        assertEquals("minecraft:oak_planks", goal.steps().get(1).parameters().get("item"));
    }

    @Test
    void rejectsUnknownResponseModeAndNonJsonOutput() {
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse("{\"mode\":\"tool_call\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse("Ich glaube, ich sollte Holz sammeln."));
    }

    @Test
    void rejectsMalformedOrOversizedAnswers() {
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse("{\"mode\":\"answer\",\"text\":42}"));
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse("{\"mode\":\"answer\",\"text\":\"   \"}"));
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse("x".repeat(24_001)));
    }

    @Test
    void rejectsModelPlansWithMoreThanTwelveSteps() {
        StringBuilder json = new StringBuilder("{\"mode\":\"plan\",\"description\":\"Zu großer Plan\",\"steps\":[");
        for (int i = 0; i < 13; i++) {
            if (i > 0) json.append(',');
            json.append("{\"id\":\"step").append(i)
                    .append("\",\"action\":\"get_player_status\"}");
        }
        json.append("]}");
        assertThrows(IllegalArgumentException.class,
                () -> LocalAIResponseParser.parse(json.toString()));
    }

    @Test
    void rejectsPlansWithInvalidDependencies() {
        assertThrows(IllegalArgumentException.class, () -> LocalAIResponseParser.parse("""
                {
                  "mode": "plan",
                  "description": "Ungültiger Auftrag",
                  "steps": [
                    {"id":"a","action":"gather_wood","dependsOn":["missing"]}
                  ]
                }
                """));
    }
}

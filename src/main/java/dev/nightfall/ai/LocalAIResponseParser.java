package dev.nightfall.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.UUID;

/** Parses the local model's answer/plan envelope; all plan steps are still client-validated. */
public final class LocalAIResponseParser {
    public sealed interface Response permits Answer, Plan {}
    public record Answer(String text) implements Response {}
    public record Plan(OfflineAgentCore.Goal goal) implements Response {}

    private LocalAIResponseParser() {}

    public static Response parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 24_000) {
            throw new IllegalArgumentException("Die lokale KI-Antwort ist leer oder zu groß.");
        }
        String json = extractJson(raw);
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Die lokale KI hat kein gültiges JSON-Ergebnis geliefert. Bitte formuliere den Auftrag genauer.");
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Die lokale KI-Antwort muss ein JSON-Objekt sein.");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("mode") || !root.get("mode").isJsonPrimitive()) {
            throw new IllegalArgumentException("Die lokale KI-Antwort enthält keinen Antwortmodus.");
        }
        String mode = root.get("mode").getAsString();
        if ("answer".equals(mode)) {
            if (!root.has("text") || !root.get("text").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("text").isString()) {
                throw new IllegalArgumentException("Die lokale KI-Antwort enthält keinen gültigen Antworttext.");
            }
            String text = root.get("text").getAsString().trim();
            if (text.isBlank() || text.length() > 8_000) {
                throw new IllegalArgumentException("Der Antworttext ist leer oder überschreitet das Limit.");
            }
            return new Answer(text);
        }
        if ("plan".equals(mode)) {
            root.remove("mode");
            OfflineAgentCore.Goal goal = OfflineAgentPlanParser.parse(
                    root.toString(), "local-" + UUID.randomUUID());
            return new Plan(goal);
        }
        throw new IllegalArgumentException("Unbekannter lokaler Antwortmodus: " + mode);
    }

    private static String extractJson(String raw) {
        String value = raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int closing = value.lastIndexOf("```");
            if (firstNewline >= 0 && closing > firstNewline) {
                value = value.substring(firstNewline + 1, closing).trim();
            }
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("Kein JSON-Objekt in der lokalen KI-Antwort gefunden.");
        return value.substring(start, end + 1);
    }
}

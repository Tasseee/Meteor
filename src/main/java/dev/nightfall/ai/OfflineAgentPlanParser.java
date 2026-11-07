package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses player-authored, structured multi-step offline goals.
 *
 * This is deliberately not a natural-language parser. The resulting action names
 * and parameter types are checked again by OfflineAgentActionBridge and the local
 * AIActionRegistry before any preview or execution.
 */
public final class OfflineAgentPlanParser {
    public static final int MAX_PLAN_JSON_CHARS = 256_000;
    public static final int MAX_PLAN_FILE_BYTES = 256 * 1024;
    private static final int MAX_PARAMETER_COUNT = 16;
    private static final Set<String> ROOT_KEYS = Set.of("description", "priority", "steps");
    private static final Set<String> STEP_KEYS = Set.of("id", "action", "parameters", "dependsOn", "maxAttempts");

    private OfflineAgentPlanParser() {}

    public static Path planDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightfall").resolve("agent_plans");
    }

    /** Parses bounded JSON into a validated dependency graph using a caller-generated goal ID. */
    public static OfflineAgentCore.Goal parse(String rawJson, String goalId) {
        if (rawJson == null || rawJson.isBlank() || rawJson.length() > MAX_PLAN_JSON_CHARS)
            throw new IllegalArgumentException("Ein Offline-Plan muss 1 bis 256.000 Zeichen enthalten.");
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(rawJson);
        } catch (RuntimeException invalidJson) {
            throw new IllegalArgumentException("Der Offline-Plan enthält ungültiges JSON.");
        }
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Der Offline-Plan muss ein JSON-Objekt sein.");
        JsonObject root = parsed.getAsJsonObject();
        requireOnlyKeys(root, ROOT_KEYS, "Plan");
        if (!root.has("steps") || !root.get("steps").isJsonArray())
            throw new IllegalArgumentException("Der Offline-Plan benötigt ein steps-Array.");

        String description = root.has("description")
                ? requiredString(root, "description", 512) : "Strukturierter Offline-Auftrag";
        int priority = root.has("priority") ? boundedInteger(root, "priority", -100, 100) : 0;
        JsonArray rawSteps = root.getAsJsonArray("steps");
        if (rawSteps.isEmpty() || rawSteps.size() > OfflineAgentCore.MAX_STEPS_PER_GOAL)
            throw new IllegalArgumentException("Ein Offline-Plan muss 1 bis 64 Schritte enthalten.");

        List<OfflineAgentCore.Step> steps = new ArrayList<>(rawSteps.size());
        for (JsonElement element : rawSteps) {
            if (element == null || !element.isJsonObject())
                throw new IllegalArgumentException("Jeder Offline-Plan-Schritt muss ein JSON-Objekt sein.");
            JsonObject step = element.getAsJsonObject();
            requireOnlyKeys(step, STEP_KEYS, "Schritt");
            String id = requiredString(step, "id", 64);
            String action = requiredString(step, "action", 64);
            Map<String, String> parameters = readParameters(step);
            List<String> dependencies = readDependencies(step);
            int maxAttempts = step.has("maxAttempts")
                    ? boundedInteger(step, "maxAttempts", 1, 5) : 2;
            steps.add(new OfflineAgentCore.Step(id, action, parameters, dependencies, maxAttempts));
        }
        return new OfflineAgentCore.Goal(goalId, description, priority, steps);
    }

    /** Loads a plan file from config/nightfall/agent_plans without accepting paths or traversal. */
    public static OfflineAgentCore.Goal loadFile(String fileName, String goalId) throws IOException {
        Path config = FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
        Path nightfall = config.resolve("nightfall");
        Path directory = nightfall.resolve("agent_plans");
        Files.createDirectories(directory);
        // The managed directories themselves must not be symlinks. Otherwise a
        // seemingly local plan filename could read from an arbitrary location.
        if (Files.isSymbolicLink(nightfall) || Files.isSymbolicLink(directory)
                || !Files.isDirectory(nightfall, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Der lokale agent_plans-Ordner darf keine symbolischen Links enthalten.");
        }
        return loadFileFromDirectory(directory, fileName, goalId);
    }

    /** Package-private path injection keeps file-loading and symlink checks testable. */
    static OfflineAgentCore.Goal loadFileFromDirectory(Path requestedDirectory,
                                                       String fileName, String goalId) throws IOException {
        if (fileName == null || !fileName.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\\.json"))
            throw new IllegalArgumentException("Bitte nur einen lokalen Dateinamen wie shelter.json angeben.");
        Path directory = requestedDirectory.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Der Plan-Ordner darf kein symbolischer Link sein.");
        }
        Path file = directory.resolve(fileName).normalize();
        if (!directory.equals(file.getParent()))
            throw new IllegalArgumentException("Der Plan muss direkt im lokalen agent_plans-Ordner liegen.");
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Plan-Datei nicht gefunden oder nicht als normale lokale Datei verfügbar: " + fileName);
        }
        long size = Files.size(file);
        if (size <= 0 || size > MAX_PLAN_FILE_BYTES)
            throw new IllegalArgumentException("Plan-Dateien müssen zwischen 1 Byte und 256 KiB groß sein.");
        return parse(Files.readString(file), goalId);
    }

    private static Map<String, String> readParameters(JsonObject step) {
        if (!step.has("parameters")) return Map.of();
        if (!step.get("parameters").isJsonObject())
            throw new IllegalArgumentException("parameters muss ein JSON-Objekt sein.");
        JsonObject properties = step.getAsJsonObject("parameters");
        if (properties.size() > MAX_PARAMETER_COUNT)
            throw new IllegalArgumentException("Ein Schritt darf höchstens 16 Parameter enthalten.");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : properties.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull())
                throw new IllegalArgumentException("Null-Parameter sind nicht erlaubt: " + key);
            String encoded = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? value.getAsString() : value.toString();
            if (encoded.length() > OfflineAgentCore.MAX_STEP_PARAMETER_CHARS)
                throw new IllegalArgumentException("Der Parameter '" + key + "' ist größer als 32 KiB.");
            result.put(key, encoded);
        }
        return Map.copyOf(result);
    }

    private static List<String> readDependencies(JsonObject step) {
        if (!step.has("dependsOn")) return List.of();
        if (!step.get("dependsOn").isJsonArray())
            throw new IllegalArgumentException("dependsOn muss ein JSON-Array aus Schritt-IDs sein.");
        JsonArray array = step.getAsJsonArray("dependsOn");
        if (array.size() > OfflineAgentCore.MAX_STEPS_PER_GOAL)
            throw new IllegalArgumentException("Zu viele Schritt-Abhängigkeiten.");
        List<String> result = new ArrayList<>(array.size());
        for (JsonElement dependency : array) {
            if (dependency == null || !dependency.isJsonPrimitive()
                    || !dependency.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Jede Abhängigkeit muss eine Text-ID sein.");
            result.add(dependency.getAsString());
        }
        return List.copyOf(result);
    }

    private static String requiredString(JsonObject object, String key, int maximumLength) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Das Feld '" + key + "' muss Text sein.");
        String text = value.getAsString();
        if (text.isBlank() || text.length() > maximumLength)
            throw new IllegalArgumentException("Das Feld '" + key + "' muss 1 bis " + maximumLength + " Zeichen enthalten.");
        return text;
    }

    private static int boundedInteger(JsonObject object, String key, int minimum, int maximum) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Das Feld '" + key + "' muss eine ganze Zahl sein.");
        try {
            int number = Integer.parseInt(value.getAsString());
            if (number < minimum || number > maximum)
                throw new IllegalArgumentException("Das Feld '" + key + "' liegt außerhalb des erlaubten Bereichs.");
            return number;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Das Feld '" + key + "' muss eine ganze Zahl sein.");
        }
    }

    private static void requireOnlyKeys(JsonObject object, Set<String> allowed, String label) {
        for (String key : object.keySet()) {
            if (!allowed.contains(key))
                throw new IllegalArgumentException(label + " enthält ein unbekanntes Feld: " + key);
        }
    }
}

package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Small local-only history of completed offline-agent goals.
 *
 * It intentionally stores only goal descriptions and per-step status summaries.
 * Inventory snapshots, coordinates, chat transcripts, server addresses and
 * credentials are not recorded. This is history, not an automatic learning model.
 */
public final class OfflineAgentMemory {
    private static final int MAX_ENTRIES = 64;
    private static final int MAX_STEPS_PER_ENTRY = OfflineAgentCore.MAX_STEPS_PER_GOAL;
    private static final int MAX_DESCRIPTION_CHARS = 512;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("nightfall").resolve("agent_memory.json");

    public record StepMemory(String id, String action, String status, int attempts) {}

    public record Entry(String goalId, String description, String status,
                        long completedAtMillis, List<StepMemory> steps) {
        public Entry {
            steps = List.copyOf(steps);
        }
    }

    private static final ArrayDeque<Entry> ENTRIES = new ArrayDeque<>();
    private static boolean loaded;

    private OfflineAgentMemory() {}

    public static synchronized void record(OfflineAgentCore.GoalView goal) {
        if (goal == null || goal.status() == OfflineAgentCore.GoalStatus.ACTIVE) return;
        loadIfNeeded();
        List<StepMemory> steps = new ArrayList<>();
        for (OfflineAgentCore.StepView step : goal.steps()) {
            if (steps.size() >= MAX_STEPS_PER_ENTRY) break;
            steps.add(new StepMemory(
                    bounded(step.id(), 64),
                    bounded(step.action(), 64),
                    step.status().name(),
                    Math.max(0, step.attempts())));
        }
        String description = bounded(goal.description(), MAX_DESCRIPTION_CHARS);
        ENTRIES.addLast(new Entry(
                bounded(goal.id(), 64),
                description,
                goal.status().name(),
                System.currentTimeMillis(),
                steps));
        while (ENTRIES.size() > MAX_ENTRIES) ENTRIES.removeFirst();
        persist();
    }

    /** Newest entries first; callers choose a small display limit. */
    public static synchronized List<Entry> recent(int limit) {
        loadIfNeeded();
        int safeLimit = Math.max(0, Math.min(limit, MAX_ENTRIES));
        List<Entry> result = new ArrayList<>(safeLimit);
        var iterator = ENTRIES.descendingIterator();
        while (iterator.hasNext() && result.size() < safeLimit) result.add(iterator.next());
        return List.copyOf(result);
    }

    public static synchronized int size() {
        loadIfNeeded();
        return ENTRIES.size();
    }

    public static synchronized boolean clear() {
        loadIfNeeded();
        ENTRIES.clear();
        try {
            Files.deleteIfExists(FILE);
            return true;
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Lokales Nightfall-Auftragsgedächtnis konnte nicht gelöscht werden: {}",
                    failure.getClass().getSimpleName());
            return false;
        }
    }

    private static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            JsonArray records = root.getAsJsonArray("entries");
            if (records == null) return;
            int start = Math.max(0, records.size() - MAX_ENTRIES);
            for (int index = start; index < records.size(); index++) {
                JsonElement element = records.get(index);
                if (!element.isJsonObject()) continue;
                JsonObject json = element.getAsJsonObject();
                String goalId = readString(json, "goalId", 64);
                String description = readString(json, "description", MAX_DESCRIPTION_CHARS);
                String status = readString(json, "status", 16);
                long completedAt = readLong(json, "completedAtMillis");
                if (goalId.isBlank() || description.isBlank()
                        || !List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status)) continue;
                List<StepMemory> steps = new ArrayList<>();
                JsonArray stepArray = json.getAsJsonArray("steps");
                if (stepArray != null) {
                    for (JsonElement stepElement : stepArray) {
                        if (steps.size() >= MAX_STEPS_PER_ENTRY || !stepElement.isJsonObject()) break;
                        JsonObject step = stepElement.getAsJsonObject();
                        String id = readString(step, "id", 64);
                        String action = readString(step, "action", 64);
                        String stepStatus = readString(step, "status", 32);
                        int attempts = (int) Math.max(0, Math.min(5, readLong(step, "attempts")));
                        if (!id.isBlank() && !action.isBlank() && !stepStatus.isBlank())
                            steps.add(new StepMemory(id, action, stepStatus, attempts));
                    }
                }
                ENTRIES.addLast(new Entry(goalId, description, status, completedAt, steps));
            }
        } catch (Exception failure) {
            ENTRIES.clear();
            Nightfall.LOGGER.warn("Lokales Nightfall-Auftragsgedächtnis konnte nicht gelesen werden: {}",
                    failure.getClass().getSimpleName());
        }
    }

    private static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject root = new JsonObject();
            JsonArray records = new JsonArray();
            for (Entry entry : ENTRIES) {
                JsonObject json = new JsonObject();
                json.addProperty("goalId", entry.goalId());
                json.addProperty("description", entry.description());
                json.addProperty("status", entry.status());
                json.addProperty("completedAtMillis", entry.completedAtMillis());
                JsonArray steps = new JsonArray();
                for (StepMemory step : entry.steps()) {
                    JsonObject stepJson = new JsonObject();
                    stepJson.addProperty("id", step.id());
                    stepJson.addProperty("action", step.action());
                    stepJson.addProperty("status", step.status());
                    stepJson.addProperty("attempts", step.attempts());
                    steps.add(stepJson);
                }
                json.add("steps", steps);
                records.add(json);
            }
            root.add("entries", records);
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, root.toString());
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Lokales Nightfall-Auftragsgedächtnis konnte nicht gespeichert werden: {}",
                    failure.getClass().getSimpleName());
        }
    }

    private static String readString(JsonObject json, String key, int maxLength) {
        try {
            if (!json.has(key) || !json.get(key).isJsonPrimitive()
                    || !json.getAsJsonPrimitive(key).isString()) return "";
            return bounded(json.get(key).getAsString(), maxLength);
        } catch (RuntimeException invalid) {
            return "";
        }
    }

    private static long readLong(JsonObject json, String key) {
        try {
            return json.has(key) ? json.get(key).getAsLong() : 0L;
        } catch (RuntimeException invalid) {
            return 0L;
        }
    }

    private static String bounded(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum);
    }
}

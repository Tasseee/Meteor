package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded, world-scoped persistence for unfinished offline-agent goals.
 *
 * Only a one-way SHA-256 world key is stored; server addresses and local save
 * paths are never written to the queue file. Interrupted in-flight actions are
 * restored as VERIFYING by OfflineAgentCore and must be checked before retry.
 */
public final class OfflineAgentQueueStore {
    private static final int VERSION = 1;
    private static final int MAX_WORLD_FILES = 8;
    private static final int MAX_ACTIVE_GOALS = OfflineAgentCore.MAX_ACTIVE_GOALS;
    private static final int MAX_STEPS_PER_GOAL = OfflineAgentCore.MAX_STEPS_PER_GOAL;
    private static final long MAX_FILE_BYTES = 1L * 1_024L * 1_024L;
    private static final long MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L;

    private final Path directory;

    public OfflineAgentQueueStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static OfflineAgentQueueStore inConfigDirectory() {
        return new OfflineAgentQueueStore(FabricLoader.getInstance().getConfigDir()
                .resolve("nightfall").resolve("agent_queues"));
    }

    /**
     * Creates a privacy-preserving key for one server/singleplayer world and
     * dimension. The underlying address or save path is never persisted.
     */
    public static String worldKey(MinecraftClient client) {
        if (client == null || client.world == null) return "";
        String server = AITaskCheckpoint.serverKey(client);
        if ("unknown".equals(server)) return "";
        String scope = server + "\\n" + client.world.getRegistryKey().getValue();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 ist auf dieser Java-Laufzeit nicht verfügbar.", impossible);
        }
    }

    /** Save active goals atomically. An empty queue removes only this world's file. */
    public synchronized boolean save(String worldKey, List<OfflineAgentCore.GoalCheckpoint> goals) {
        if (!validWorldKey(worldKey) || goals == null || goals.size() > MAX_ACTIVE_GOALS) return false;
        final Path safeDirectory;
        try {
            safeDirectory = checkedDirectory(true);
        } catch (IOException | RuntimeException unsafePath) {
            return false;
        }
        Path target = fileFor(safeDirectory, worldKey);
        if (goals.isEmpty()) {
            try {
                Files.deleteIfExists(target);
                return true;
            } catch (IOException failure) {
                return false;
            }
        }

        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("savedAtMillis", System.currentTimeMillis());
            root.addProperty("scopeHash", worldKey);
            JsonArray encodedGoals = new JsonArray();
            for (OfflineAgentCore.GoalCheckpoint checkpoint : goals) {
                encodedGoals.add(encodeGoal(checkpoint));
            }
            root.add("goals", encodedGoals);
            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) {
                // Never leave an older, stale queue that could replay an action
                // after a newer checkpoint exceeded the storage budget.
                deleteQuietly(target);
                return false;
            }

            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                // Remove only an old in-directory temp entry, then create a fresh
                // file exclusively. CREATE_NEW + NOFOLLOW_LINKS never writes
                // through a pre-existing temporary-file symlink.
                Files.deleteIfExists(temporary);
                try (var output = Files.newOutputStream(temporary,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    output.write(bytes);
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | RuntimeException failure) {
                deleteQuietly(temporary);
                deleteQuietly(target);
                return false;
            }
            pruneOldWorldFiles(target);
            return true;
        } catch (RuntimeException failure) {
            deleteQuietly(target);
            return false;
        }
    }

    /** Load only the matching world; a different server/dimension has a different filename. */
    public synchronized List<OfflineAgentCore.GoalCheckpoint> load(String worldKey) {
        if (!validWorldKey(worldKey)) return List.of();
        final Path safeDirectory;
        try {
            safeDirectory = checkedDirectory(false);
        } catch (IOException | RuntimeException unsafePath) {
            return List.of();
        }
        if (!Files.isDirectory(safeDirectory, LinkOption.NOFOLLOW_LINKS)) return List.of();
        Path file = fileFor(safeDirectory, worldKey);
        try {
            if (Files.isSymbolicLink(file)) {
                deleteQuietly(file);
                return List.of();
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
            long fileSize = Files.size(file);
            if (fileSize <= 0 || fileSize > MAX_FILE_BYTES) {
                deleteQuietly(file);
                return List.of();
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (readInt(root, "version", -1) != VERSION
                    || !worldKey.equals(readString(root, "scopeHash", 64))) {
                deleteQuietly(file);
                return List.of();
            }
            long savedAt = readLong(root, "savedAtMillis", 0L);
            long age = System.currentTimeMillis() - savedAt;
            if (savedAt <= 0L || age < 0L || age > MAX_AGE_MILLIS) {
                deleteQuietly(file);
                return List.of();
            }
            JsonArray encodedGoals = root.getAsJsonArray("goals");
            if (encodedGoals == null || encodedGoals.size() > MAX_ACTIVE_GOALS) {
                deleteQuietly(file);
                return List.of();
            }
            List<OfflineAgentCore.GoalCheckpoint> result = new ArrayList<>(encodedGoals.size());
            for (JsonElement element : encodedGoals) {
                if (!element.isJsonObject()) throw new IllegalArgumentException("Ungültiger Agenten-Auftrag.");
                result.add(decodeGoal(element.getAsJsonObject()));
            }
            return List.copyOf(result);
        } catch (Exception invalid) {
            deleteQuietly(file);
            return List.of();
        }
    }

    public synchronized void clear(String worldKey) {
        if (!validWorldKey(worldKey)) return;
        try {
            Path safeDirectory = checkedDirectory(false);
            if (Files.isDirectory(safeDirectory, LinkOption.NOFOLLOW_LINKS))
                deleteQuietly(fileFor(safeDirectory, worldKey));
        } catch (IOException | RuntimeException ignored) {
            // Do not traverse or delete through a redirected queue directory.
        }
    }

    private static JsonObject encodeGoal(OfflineAgentCore.GoalCheckpoint checkpoint) {
        JsonObject goalJson = new JsonObject();
        OfflineAgentCore.Goal goal = checkpoint.goal();
        goalJson.addProperty("id", goal.id());
        goalJson.addProperty("description", goal.description());
        goalJson.addProperty("priority", goal.priority());
        goalJson.addProperty("status", checkpoint.status().name());
        JsonArray steps = new JsonArray();
        for (OfflineAgentCore.StepCheckpoint checkpointStep : checkpoint.steps()) {
            OfflineAgentCore.Step step = checkpointStep.step();
            JsonObject stepJson = new JsonObject();
            stepJson.addProperty("id", step.id());
            stepJson.addProperty("action", step.action());
            JsonObject parameters = new JsonObject();
            for (Map.Entry<String, String> parameter : step.parameters().entrySet()) {
                parameters.addProperty(parameter.getKey(), parameter.getValue());
            }
            stepJson.add("parameters", parameters);
            JsonArray dependencies = new JsonArray();
            for (String dependency : step.dependsOn()) dependencies.add(dependency);
            stepJson.add("dependsOn", dependencies);
            stepJson.addProperty("maxAttempts", step.maxAttempts());
            stepJson.addProperty("stepStatus", checkpointStep.status().name());
            stepJson.addProperty("attempts", checkpointStep.attempts());
            stepJson.addProperty("detail", checkpointStep.detail());
            steps.add(stepJson);
        }
        goalJson.add("steps", steps);
        return goalJson;
    }

    private static OfflineAgentCore.GoalCheckpoint decodeGoal(JsonObject json) {
        String id = readString(json, "id", 64);
        String description = readString(json, "description", 512);
        int priority = readInt(json, "priority", 0);
        OfflineAgentCore.GoalStatus goalStatus = OfflineAgentCore.GoalStatus.valueOf(
                readString(json, "status", 16));
        if (goalStatus != OfflineAgentCore.GoalStatus.ACTIVE)
            throw new IllegalArgumentException("Nur aktive Aufträge dürfen in der Warteschlange liegen.");

        JsonArray encodedSteps = json.getAsJsonArray("steps");
        if (encodedSteps == null || encodedSteps.isEmpty() || encodedSteps.size() > MAX_STEPS_PER_GOAL)
            throw new IllegalArgumentException("Ungültige Anzahl von Agenten-Schritten.");
        List<OfflineAgentCore.Step> steps = new ArrayList<>(encodedSteps.size());
        List<OfflineAgentCore.StepCheckpoint> stepCheckpoints = new ArrayList<>(encodedSteps.size());
        for (JsonElement element : encodedSteps) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("Ungültiger Agenten-Schritt.");
            JsonObject stepJson = element.getAsJsonObject();
            String stepId = readString(stepJson, "id", 64);
            String action = readString(stepJson, "action", 64);
            JsonObject encodedParameters = stepJson.getAsJsonObject("parameters");
            if (encodedParameters == null || encodedParameters.size() > 16)
                throw new IllegalArgumentException("Ungültige Aktionsparameter.");
            Map<String, String> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : encodedParameters.entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Aktionsparameter müssen lokal als Text gespeichert sein.");
                parameters.put(entry.getKey(), entry.getValue().getAsString());
            }
            JsonArray encodedDependencies = stepJson.getAsJsonArray("dependsOn");
            if (encodedDependencies == null || encodedDependencies.size() > MAX_STEPS_PER_GOAL)
                throw new IllegalArgumentException("Ungültige Aktionsabhängigkeiten.");
            List<String> dependencies = new ArrayList<>(encodedDependencies.size());
            for (JsonElement dependency : encodedDependencies) {
                if (!dependency.isJsonPrimitive() || !dependency.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Ungültige Aktionsabhängigkeit.");
                dependencies.add(dependency.getAsString());
            }
            int maxAttempts = readInt(stepJson, "maxAttempts", 1);
            OfflineAgentCore.Step step = new OfflineAgentCore.Step(
                    stepId, action, parameters, dependencies, maxAttempts);
            OfflineAgentCore.StepStatus stepStatus = OfflineAgentCore.StepStatus.valueOf(
                    readString(stepJson, "stepStatus", 32));
            int attempts = readInt(stepJson, "attempts", 0);
            String detail = readString(stepJson, "detail", 512);
            steps.add(step);
            stepCheckpoints.add(new OfflineAgentCore.StepCheckpoint(step, stepStatus, attempts, detail));
        }
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(id, description, priority, steps);
        return new OfflineAgentCore.GoalCheckpoint(goal, goalStatus, stepCheckpoints);
    }

    private void pruneOldWorldFiles(Path keep) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            List<Path> files = new ArrayList<>();
            for (Path path : stream) {
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) files.add(path);
            }
            if (files.size() <= MAX_WORLD_FILES) return;
            files.sort((left, right) -> {
                try {
                    return Files.getLastModifiedTime(left).compareTo(Files.getLastModifiedTime(right));
                } catch (IOException ignored) {
                    return 0;
                }
            });
            var iterator = files.iterator();
            while (files.size() > MAX_WORLD_FILES && iterator.hasNext()) {
                Path path = iterator.next();
                if (path.equals(keep)) continue;
                deleteQuietly(path);
                iterator.remove();
            }
        } catch (IOException ignored) {
            // Queue persistence must never interrupt the Minecraft client.
        }
    }

    private Path fileFor(Path safeDirectory, String worldKey) {
        return safeDirectory.resolve(worldKey + ".json");
    }

    /**
     * The queue may be redirected only by normal paths inside the configured
     * Nightfall folder. Refuse symlinked parent/queue directories and never
     * create, read or delete queue files through them.
     */
    private Path checkedDirectory(boolean create) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (Files.isSymbolicLink(normalized)
                || (parent != null && Files.isSymbolicLink(parent))) {
            throw new IOException("Symlinked offline-agent queue directory is not allowed.");
        }
        if (create) Files.createDirectories(normalized);
        if (Files.isSymbolicLink(normalized)
                || (parent != null && Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
                || (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Offline-agent queue path is not a normal directory.");
        }
        return normalized;
    }

    private static boolean validWorldKey(String worldKey) {
        return worldKey != null && worldKey.matches("[a-f0-9]{64}");
    }

    private static void deleteQuietly(Path file) {
        try { Files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    private static String readString(JsonObject json, String key, int maximum) {
        JsonElement value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Ungültiges Agenten-Checkpoint-Feld: " + key);
        String result = value.getAsString();
        if (result.length() > maximum) throw new IllegalArgumentException("Agenten-Checkpoint-Feld ist zu lang: " + key);
        return result;
    }

    private static int readInt(JsonObject json, String key, int fallback) {
        try {
            JsonElement value = json.get(key);
            if (value == null || !value.isJsonPrimitive()) return fallback;
            return value.getAsInt();
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static long readLong(JsonObject json, String key, long fallback) {
        try {
            JsonElement value = json.get(key);
            if (value == null || !value.isJsonPrimitive()) return fallback;
            return value.getAsLong();
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }
}

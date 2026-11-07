package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAgentQueueStoreTest {
    private static final String WORLD_A = "a".repeat(64);
    private static final String WORLD_B = "b".repeat(64);

    @Test
    void persistsOnlyTheMatchingWorldQueueAndRestoresInterruptedActionsConservatively() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            OfflineAgentCore.Step gather = new OfflineAgentCore.Step(
                    "gather", "gather_wood", Map.of("item", "minecraft:oak_log", "count", "8"),
                    List.of(), 2);
            OfflineAgentCore.Step craft = new OfflineAgentCore.Step(
                    "craft", "craft_item", Map.of("item", "minecraft:oak_planks", "count", "32"),
                    List.of("gather"), 2);
            OfflineAgentCore.StepCheckpoint gatherCheckpoint = new OfflineAgentCore.StepCheckpoint(
                    gather, OfflineAgentCore.StepStatus.SUCCEEDED, 1, "8 Stämme gesammelt.");
            OfflineAgentCore.StepCheckpoint craftCheckpoint = new OfflineAgentCore.StepCheckpoint(
                    craft, OfflineAgentCore.StepStatus.RUNNING, 1, "Crafting lief beim Clientstopp.");
            OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                    "wood-job", "Sammle Holz und craft Bretter.", 5, List.of(gather, craft));
            OfflineAgentCore.GoalCheckpoint checkpoint = new OfflineAgentCore.GoalCheckpoint(
                    goal, OfflineAgentCore.GoalStatus.ACTIVE, List.of(gatherCheckpoint, craftCheckpoint));

            assertTrue(store.save(WORLD_A, List.of(checkpoint)));
            assertEquals(1, store.load(WORLD_A).size());
            assertTrue(store.load(WORLD_B).isEmpty());

            OfflineAgentCore restored = new OfflineAgentCore();
            assertEquals(1, restored.restoreActiveCheckpoints(store.load(WORLD_A)));
            List<OfflineAgentCore.GoalView> snapshot = restored.snapshot();
            assertEquals(OfflineAgentCore.StepStatus.SUCCEEDED, snapshot.get(0).steps().get(0).status());
            assertEquals(OfflineAgentCore.StepStatus.VERIFYING, snapshot.get(0).steps().get(1).status());
            assertTrue(restored.nextReadyStep().isEmpty());

            restored.verify("wood-job", "craft", true, "Spieler hat Bretterbestand überprüft.");
            assertEquals(OfflineAgentCore.GoalStatus.SUCCEEDED, restored.snapshot().get(0).status());
            assertTrue(store.save(WORLD_A, restored.exportActiveCheckpoints()));
            assertTrue(store.load(WORLD_A).isEmpty());
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void preservesPendingStepsAndDependenciesAcrossAQueueRoundTrip() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-pending-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            OfflineAgentCore.Step first = new OfflineAgentCore.Step(
                    "first", "gather_block", Map.of("item", "minecraft:cobblestone", "count", "16"),
                    List.of(), 2);
            OfflineAgentCore.Step second = new OfflineAgentCore.Step(
                    "second", "craft_item", Map.of("item", "minecraft:furnace", "count", "1"),
                    List.of("first"), 1);
            OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                    "plan", "Materialien sammeln und Ofen craften.", 10, List.of(first, second));
            var steps = List.of(
                    new OfflineAgentCore.StepCheckpoint(first, OfflineAgentCore.StepStatus.PENDING, 0, ""),
                    new OfflineAgentCore.StepCheckpoint(second, OfflineAgentCore.StepStatus.PENDING, 0, ""));
            var checkpoint = new OfflineAgentCore.GoalCheckpoint(goal, OfflineAgentCore.GoalStatus.ACTIVE, steps);

            assertTrue(store.save(WORLD_A, List.of(checkpoint)));
            OfflineAgentCore restored = new OfflineAgentCore();
            assertEquals(0, restored.restoreActiveCheckpoints(store.load(WORLD_A)));
            assertEquals("first", restored.nextReadyStep().orElseThrow().step().id());
            restored.markStarted("plan", "first");
            restored.report("plan", "first", OfflineAgentCore.ActionResult.SUCCESS, "Fertig");
            assertEquals("second", restored.nextReadyStep().orElseThrow().step().id());
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void oversizedNewQueueNeverLeavesAnOlderStaleCheckpointBehind() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-size-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            assertTrue(store.save(WORLD_A, List.of(simpleCheckpoint())));

            String payload = "x".repeat(32_760);
            List<OfflineAgentCore.Step> steps = new java.util.ArrayList<>();
            List<OfflineAgentCore.StepCheckpoint> checkpoints = new java.util.ArrayList<>();
            for (int i = 0; i < 40; i++) {
                OfflineAgentCore.Step step = new OfflineAgentCore.Step(
                        "step-" + i, "local_action_" + i, Map.of("payload", payload), List.of(), 1);
                steps.add(step);
                checkpoints.add(new OfflineAgentCore.StepCheckpoint(
                        step, OfflineAgentCore.StepStatus.PENDING, 0, ""));
            }
            OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                    "large", "Große, aber gültige Offline-Aufgabe.", 0, steps);
            OfflineAgentCore.GoalCheckpoint oversized = new OfflineAgentCore.GoalCheckpoint(
                    goal, OfflineAgentCore.GoalStatus.ACTIVE, checkpoints);

            assertFalse(store.save(WORLD_A, List.of(oversized)));
            assertTrue(store.load(WORLD_A).isEmpty(),
                    "A stale earlier checkpoint must not replay work after a newer save failed.");
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void rejectsInvalidWorldKeysAndDeletesTheQueueWhenNoGoalsRemain() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-clear-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            assertFalse(store.save("../invalid", List.of()));
            assertTrue(store.load("../invalid").isEmpty());
            assertTrue(store.save(WORLD_A, List.of(simpleCheckpoint())));
            assertTrue(store.save(WORLD_A, List.of()));
            assertTrue(store.load(WORLD_A).isEmpty());
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void expiredAndMalformedWorldQueuesAreDiscarded() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-expiry-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            Path expired = directory.resolve(WORLD_A + ".json");
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("version", 1);
            root.addProperty("savedAtMillis", System.currentTimeMillis() - (25L * 60L * 60L * 1_000L));
            root.addProperty("scopeHash", WORLD_A);
            root.add("goals", new com.google.gson.JsonArray());
            Files.writeString(expired, root.toString());

            assertTrue(store.load(WORLD_A).isEmpty());
            assertFalse(Files.exists(expired), "Expired offline goals must be deleted.");

            Path malformed = directory.resolve(WORLD_B + ".json");
            Files.writeString(malformed, "{not-json");
            assertTrue(store.load(WORLD_B).isEmpty());
            assertFalse(Files.exists(malformed), "Malformed offline goals must be deleted.");
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void worldQueueFilesRemainBounded() throws IOException {
        Path directory = Files.createTempDirectory("nightfall-agent-queue-world-bound-test");
        try {
            OfflineAgentQueueStore store = new OfflineAgentQueueStore(directory);
            for (int index = 0; index < 10; index++) {
                String key = java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(("world-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertTrue(store.save(key, List.of(simpleCheckpoint())));
            }
            try (var files = Files.list(directory)) {
                long retained = files.filter(file -> file.getFileName().toString().endsWith(".json")).count();
                assertEquals(8, retained);
            }
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        } finally {
            deleteTree(directory);
        }
    }

    private static OfflineAgentCore.GoalCheckpoint simpleCheckpoint() {
        OfflineAgentCore.Step step = new OfflineAgentCore.Step(
                "step", "sort_inventory", Map.of(), List.of(), 1);
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                "simple", "Sortiere das Inventar.", 0, List.of(step));
        return new OfflineAgentCore.GoalCheckpoint(goal, OfflineAgentCore.GoalStatus.ACTIVE,
                List.of(new OfflineAgentCore.StepCheckpoint(
                        step, OfflineAgentCore.StepStatus.PENDING, 0, "")));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}

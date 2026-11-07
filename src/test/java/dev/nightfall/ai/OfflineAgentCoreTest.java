package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAgentCoreTest {
    private OfflineAgentCore.Step step(String id, String action, List<String> dependencies, int attempts) {
        return new OfflineAgentCore.Step(id, action, Map.of(), dependencies, attempts);
    }

    @Test
    void dependenciesGateLaterStepsUntilVerifiedSuccess() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("base", "Baue eine Basis", 10, List.of(
                step("gather", "gather_block", List.of(), 2),
                step("build", "build_structure", List.of("gather"), 1)
        )));

        OfflineAgentCore.StepRef first = core.nextReadyStep().orElseThrow();
        assertEquals("gather", first.step().id());
        core.markStarted("base", "gather");
        core.report("base", "gather", OfflineAgentCore.ActionResult.SUCCESS, "Material gesammelt");

        assertEquals("build", core.nextReadyStep().orElseThrow().step().id());
    }

    @Test
    void uncertainResultMustBeVerifiedBeforeDependentsCanRun() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("farm", "Lege eine Farm an", 1, List.of(
                step("prepare", "prepare_area", List.of(), 1),
                step("plant", "plant_crops", List.of("prepare"), 1)
        )));
        core.markStarted("farm", "prepare");
        core.report("farm", "prepare", OfflineAgentCore.ActionResult.UNCERTAIN, "Ergebnis unklar");

        assertEquals(OfflineAgentCore.StepStatus.VERIFYING,
                core.snapshot().get(0).steps().get(0).status());
        assertTrue(core.nextReadyStep().isEmpty());

        core.verify("farm", "prepare", true, "Freie Fläche bestätigt");
        assertEquals("plant", core.nextReadyStep().orElseThrow().step().id());
    }

    @Test
    void retryableFailuresAreBounded() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("wood", "Sammle Holz", 1,
                List.of(step("chop", "gather_wood", List.of(), 2))));

        core.markStarted("wood", "chop");
        core.report("wood", "chop", OfflineAgentCore.ActionResult.RETRYABLE_FAILURE, "Kein Holzdrop");
        assertEquals("chop", core.nextReadyStep().orElseThrow().step().id());

        core.markStarted("wood", "chop");
        core.report("wood", "chop", OfflineAgentCore.ActionResult.RETRYABLE_FAILURE, "Erneut fehlgeschlagen");
        assertEquals(OfflineAgentCore.StepStatus.FAILED, core.snapshot().get(0).steps().get(0).status());
        assertEquals(OfflineAgentCore.GoalStatus.FAILED, core.snapshot().get(0).status());
        assertTrue(core.nextReadyStep().isEmpty());
    }

    @Test
    void structuredJsonParametersCanUseTheBoundedStepPayloadBudget() {
        String blocksJson = "[" + "x".repeat(20_000) + "]";
        OfflineAgentCore.Step step = new OfflineAgentCore.Step(
                "custom", "build_custom_structure", Map.of("blocks", blocksJson), List.of(), 1);
        assertEquals(blocksJson, step.parameters().get("blocks"));
    }

    @Test
    void structuredJsonParametersCannotExceedTheStepPayloadBudget() {
        String oversized = "x".repeat(32_769);
        assertThrows(IllegalArgumentException.class, () -> new OfflineAgentCore.Step(
                "custom", "build_custom_structure", Map.of("blocks", oversized), List.of(), 1));
    }

    @Test
    void invalidDependencyCyclesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OfflineAgentCore.Goal(
                "cycle", "Ungültiger Plan", 0, List.of(
                step("a", "action_a", List.of("b"), 1),
                step("b", "action_b", List.of("a"), 1)
        )));
    }

    @Test
    void prioritiesChooseBetweenGoalsDeterministically() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("low", "Niedrige Priorität", 1,
                List.of(step("a", "action_a", List.of(), 1))));
        core.submit(new OfflineAgentCore.Goal("high", "Hohe Priorität", 20,
                List.of(step("b", "action_b", List.of(), 1))));

        assertEquals("high", core.nextReadyStep().orElseThrow().goalId());
    }

    @Test
    void onlyOneMinecraftActionMayBeInFlight() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("first", "Erster Auftrag", 2,
                List.of(step("a", "action_a", List.of(), 1))));
        core.submit(new OfflineAgentCore.Goal("second", "Zweiter Auftrag", 1,
                List.of(step("b", "action_b", List.of(), 1))));

        core.markStarted("first", "a");
        assertTrue(core.nextReadyStep().isEmpty());
        assertThrows(IllegalStateException.class, () -> core.markStarted("second", "b"));
        core.report("first", "a", OfflineAgentCore.ActionResult.SUCCESS, "Fertig");
        assertEquals("second", core.nextReadyStep().orElseThrow().goalId());
    }

    @Test
    void cancellationWaitsForRunningActionToAcknowledgeStop() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("cancel", "Abbrechen", 0,
                List.of(step("run", "long_action", List.of(), 1))));
        core.markStarted("cancel", "run");
        core.cancel("cancel");

        assertEquals(OfflineAgentCore.StepStatus.CANCEL_REQUESTED,
                core.snapshot().get(0).steps().get(0).status());
        assertEquals(OfflineAgentCore.GoalStatus.ACTIVE, core.snapshot().get(0).status());
        core.acknowledgeStopped("cancel", "run");
        assertEquals(OfflineAgentCore.GoalStatus.CANCELLED, core.snapshot().get(0).status());
    }

    @Test
    void cancellationOfVerifyingStepDoesNotRequireRunningActionAcknowledgement() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("verify-cancel", "Brich eine laufende Prüfung ab", 0,
                List.of(step("work", "gather_wood", List.of(), 2))));
        core.markStarted("verify-cancel", "work");
        core.report("verify-cancel", "work", OfflineAgentCore.ActionResult.UNCERTAIN,
                "Controller läuft noch oder Ergebnis wartet auf Prüfung.");

        core.cancel("verify-cancel");

        assertEquals(OfflineAgentCore.StepStatus.CANCELLED,
                core.snapshot().get(0).steps().get(0).status());
        assertEquals(OfflineAgentCore.GoalStatus.CANCELLED,
                core.snapshot().get(0).status());
        assertTrue(core.nextReadyStep().isEmpty());
    }

    @Test
    void completedGoalHistoryRemainsBounded() {
        OfflineAgentCore core = new OfflineAgentCore();
        for (int i = 0; i < OfflineAgentCore.MAX_RETAINED_GOALS + 1; i++) {
            String goalId = "goal-" + i;
            core.submit(new OfflineAgentCore.Goal(goalId, "Kurzauftrag " + i, 0,
                    List.of(step("step", "local_action", List.of(), 1))));
            core.markStarted(goalId, "step");
            core.report(goalId, "step", OfflineAgentCore.ActionResult.SUCCESS, "Fertig");
        }

        List<OfflineAgentCore.GoalView> snapshot = core.snapshot();
        assertEquals(OfflineAgentCore.MAX_RETAINED_GOALS, snapshot.size());
        assertFalse(snapshot.stream().anyMatch(goal -> goal.id().equals("goal-0")));
        assertTrue(snapshot.stream().anyMatch(goal -> goal.id().equals(
                "goal-" + OfflineAgentCore.MAX_RETAINED_GOALS)));
    }

    @Test
    void failedVerificationRequeuesOnlyWhileAttemptsRemain() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("verify-retry", "Prüfe einen Aktionsabschluss", 0,
                List.of(step("work", "gather_wood", List.of(), 2))));

        core.markStarted("verify-retry", "work");
        core.report("verify-retry", "work", OfflineAgentCore.ActionResult.UNCERTAIN, "Läuft");
        core.verify("verify-retry", "work", false, "Nur teilweise abgeschlossen");
        assertEquals(OfflineAgentCore.StepStatus.PENDING,
                core.snapshot().get(0).steps().get(0).status());
        assertEquals("work", core.nextReadyStep().orElseThrow().step().id());

        core.markStarted("verify-retry", "work");
        core.report("verify-retry", "work", OfflineAgentCore.ActionResult.UNCERTAIN, "Zweiter Versuch");
        core.verify("verify-retry", "work", false, "Wieder unvollständig");
        assertEquals(OfflineAgentCore.StepStatus.FAILED,
                core.snapshot().get(0).steps().get(0).status());
        assertEquals(OfflineAgentCore.GoalStatus.FAILED,
                core.snapshot().get(0).status());
    }

    @Test
    void restoreTurnsAnInterruptedStepIntoVerificationAndBlocksDependents() {
        OfflineAgentCore core = new OfflineAgentCore();
        OfflineAgentCore.Step gather = step("gather", "gather_wood", List.of(), 2);
        OfflineAgentCore.Step craft = step("craft", "craft_item", List.of("gather"), 2);
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                "restart", "Sammle Holz und craft Bretter.", 5, List.of(gather, craft));
        OfflineAgentCore.GoalCheckpoint checkpoint = new OfflineAgentCore.GoalCheckpoint(
                goal, OfflineAgentCore.GoalStatus.ACTIVE, List.of(
                new OfflineAgentCore.StepCheckpoint(gather, OfflineAgentCore.StepStatus.SUCCEEDED, 1, "Holz gesammelt."),
                new OfflineAgentCore.StepCheckpoint(craft, OfflineAgentCore.StepStatus.RUNNING, 1, "Crafting lief.")
        ));

        assertEquals(1, core.restoreActiveCheckpoints(List.of(checkpoint)));
        assertEquals(OfflineAgentCore.StepStatus.SUCCEEDED, core.snapshot().get(0).steps().get(0).status());
        assertEquals(OfflineAgentCore.StepStatus.VERIFYING, core.snapshot().get(0).steps().get(1).status());
        assertTrue(core.nextReadyStep().isEmpty(),
                "A dependent or retry step must not start before the interrupted action is checked.");

        core.verify("restart", "craft", false, "Inventar geprüft: die Bretter fehlen noch.");
        assertEquals("craft", core.nextReadyStep().orElseThrow().step().id());
    }

    @Test
    void restoreRejectsMoreThanOneInterruptedMinecraftActionAtomically() {
        OfflineAgentCore core = new OfflineAgentCore();
        OfflineAgentCore.Step first = step("one", "gather_wood", List.of(), 2);
        OfflineAgentCore.Step second = step("two", "craft_item", List.of(), 2);
        OfflineAgentCore.Goal firstGoal = new OfflineAgentCore.Goal(
                "first", "Erster Auftrag", 1, List.of(first));
        OfflineAgentCore.Goal secondGoal = new OfflineAgentCore.Goal(
                "second", "Zweiter Auftrag", 1, List.of(second));
        OfflineAgentCore.GoalCheckpoint firstCheckpoint = new OfflineAgentCore.GoalCheckpoint(
                firstGoal, OfflineAgentCore.GoalStatus.ACTIVE,
                List.of(new OfflineAgentCore.StepCheckpoint(first, OfflineAgentCore.StepStatus.RUNNING, 1, "Unterbrochen.")));
        OfflineAgentCore.GoalCheckpoint secondCheckpoint = new OfflineAgentCore.GoalCheckpoint(
                secondGoal, OfflineAgentCore.GoalStatus.ACTIVE,
                List.of(new OfflineAgentCore.StepCheckpoint(second, OfflineAgentCore.StepStatus.VERIFYING, 1, "Unterbrochen.")));

        assertThrows(IllegalArgumentException.class,
                () -> core.restoreActiveCheckpoints(List.of(firstCheckpoint, secondCheckpoint)));
        assertTrue(core.snapshot().isEmpty(),
                "An invalid multi-action restore must not partially mutate the scheduler.");
        assertEquals(1, core.restoreActiveCheckpoints(List.of(firstCheckpoint)));
    }

    @Test
    void duplicateGoalIdsAreRejected() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("same", "Erster Plan", 0,
                List.of(step("a", "action_a", List.of(), 1))));
        assertThrows(IllegalArgumentException.class, () -> core.submit(
                new OfflineAgentCore.Goal("same", "Zweiter Plan", 0,
                        List.of(step("b", "action_b", List.of(), 1)))));
    }
}

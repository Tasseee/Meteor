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
    void duplicateDependencyIdsAreRejectedByTheCore() {
        assertThrows(IllegalArgumentException.class, () -> new OfflineAgentCore.Step(
                "build", "build_structure", Map.of(), List.of("gather", "gather"), 1));
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
    void checkpointRejectsPendingStepsWithNoAttemptsRemaining() {
        OfflineAgentCore.Step exhausted = step("work", "gather_wood", List.of(), 2);
        assertThrows(IllegalArgumentException.class, () ->
                new OfflineAgentCore.StepCheckpoint(
                        exhausted, OfflineAgentCore.StepStatus.PENDING, 2, "Alle Versuche verbraucht."));
    }

    @Test
    void restoreRejectsACompletedStepWhoseDependencyIsStillPending() {
        OfflineAgentCore core = new OfflineAgentCore();
        OfflineAgentCore.Step prepare = step("prepare", "gather_block", List.of(), 1);
        OfflineAgentCore.Step build = step("build", "build_structure", List.of("prepare"), 1);
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                "bad-order", "Ungültiger Wiederaufnahmeplan", 0, List.of(prepare, build));
        OfflineAgentCore.GoalCheckpoint checkpoint = new OfflineAgentCore.GoalCheckpoint(
                goal, OfflineAgentCore.GoalStatus.ACTIVE, List.of(
                new OfflineAgentCore.StepCheckpoint(prepare, OfflineAgentCore.StepStatus.PENDING, 0, ""),
                new OfflineAgentCore.StepCheckpoint(build, OfflineAgentCore.StepStatus.SUCCEEDED, 1, "Ungültig markiert.")
        ));

        assertThrows(IllegalArgumentException.class,
                () -> core.restoreActiveCheckpoints(List.of(checkpoint)));
        assertTrue(core.snapshot().isEmpty());
    }

    @Test
    void restoreRejectsAnInFlightStepBeforeItsDependencySucceeded() {
        OfflineAgentCore core = new OfflineAgentCore();
        OfflineAgentCore.Step gather = step("gather", "gather_wood", List.of(), 2);
        OfflineAgentCore.Step craft = step("craft", "craft_item", List.of("gather"), 2);
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                "bad-order", "Ungültige Wiederaufnahme", 0, List.of(gather, craft));
        OfflineAgentCore.GoalCheckpoint checkpoint = new OfflineAgentCore.GoalCheckpoint(
                goal, OfflineAgentCore.GoalStatus.ACTIVE, List.of(
                new OfflineAgentCore.StepCheckpoint(gather, OfflineAgentCore.StepStatus.PENDING, 0, ""),
                new OfflineAgentCore.StepCheckpoint(craft, OfflineAgentCore.StepStatus.RUNNING, 1, "Unmöglicher Zustand.")
        ));

        assertThrows(IllegalArgumentException.class, () -> core.restoreActiveCheckpoints(List.of(checkpoint)));
        assertTrue(core.snapshot().isEmpty(),
                "A dependency-invalid checkpoint must not partially restore any goal.");
    }

    @Test
    void activeGoalCheckpointRejectsTerminalStepStatuses() {
        OfflineAgentCore.Step work = step("work", "gather_wood", List.of(), 2);
        OfflineAgentCore.Goal goal = new OfflineAgentCore.Goal(
                "terminal-step", "Ungültiger aktiver Checkpoint", 0, List.of(work));
        assertThrows(IllegalArgumentException.class, () -> new OfflineAgentCore.GoalCheckpoint(
                goal, OfflineAgentCore.GoalStatus.ACTIVE,
                List.of(new OfflineAgentCore.StepCheckpoint(work, OfflineAgentCore.StepStatus.BLOCKED, 0, "Blockiert."))));
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

    @Test
    void replanningPreservesConfirmedProgressAndReplacesOnlyUnfinishedSteps() {
        OfflineAgentCore core = new OfflineAgentCore();
        OfflineAgentCore.Step gather = step("gather", "gather_wood", List.of(), 2);
        OfflineAgentCore.Step oldCraft = step("old-craft", "craft_item", List.of("gather"), 1);
        OfflineAgentCore.Step oldBuild = step("old-build", "build_structure", List.of("old-craft"), 1);
        core.submit(new OfflineAgentCore.Goal("replan", "Baue eine kleine Hütte", 4,
                List.of(gather, oldCraft, oldBuild)));
        core.markStarted("replan", "gather");
        core.report("replan", "gather", OfflineAgentCore.ActionResult.SUCCESS, "Holz bestätigt");

        OfflineAgentCore.Step newCraft = step("new-craft", "craft_item", List.of("gather"), 2);
        OfflineAgentCore.Step newBuild = step("new-build", "build_custom_structure", List.of("new-craft"), 1);
        core.replanRemaining("replan", List.of(newCraft, newBuild));

        OfflineAgentCore.GoalView revised = core.snapshot().get(0);
        assertEquals(OfflineAgentCore.GoalStatus.ACTIVE, revised.status());
        assertEquals(List.of("gather", "new-craft", "new-build"),
                revised.steps().stream().map(OfflineAgentCore.StepView::id).toList());
        assertEquals(OfflineAgentCore.StepStatus.SUCCEEDED, revised.steps().get(0).status());
        assertEquals("new-craft", core.nextReadyStep().orElseThrow().step().id());
    }

    @Test
    void replanningIsRejectedWhileAnActionIsRunningOrUncertain() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("busy-replan", "Sammle Holz", 0,
                List.of(step("gather", "gather_wood", List.of(), 2))));
        core.markStarted("busy-replan", "gather");
        assertThrows(IllegalStateException.class, () -> core.replanRemaining(
                "busy-replan", List.of(step("alternate", "gather_block", List.of(), 1))));

        core.report("busy-replan", "gather", OfflineAgentCore.ActionResult.UNCERTAIN, "Ergebnis unklar");
        assertThrows(IllegalStateException.class, () -> core.replanRemaining(
                "busy-replan", List.of(step("alternate", "gather_block", List.of(), 1))));
    }

    @Test
    void replanningFailedGoalIsBlockedWhileAnotherGoalHasAnInFlightAction() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("failed", "Baue ein Haus", 2,
                List.of(step("old", "build_structure", List.of(), 1))));
        core.markStarted("failed", "old");
        core.report("failed", "old", OfflineAgentCore.ActionResult.PERMANENT_FAILURE, "Fehlgeschlagen");

        core.submit(new OfflineAgentCore.Goal("other", "Sammle Holz", 1,
                List.of(step("gather", "gather_wood", List.of(), 2))));
        core.markStarted("other", "gather");
        assertThrows(IllegalStateException.class, () -> core.replanRemaining("failed",
                List.of(step("new-gather", "gather_wood", List.of(), 1))));

        core.report("other", "gather", OfflineAgentCore.ActionResult.SUCCESS, "Abgeschlossen");
        core.replanRemaining("failed", List.of(step("new-gather", "gather_wood", List.of(), 1)));
        assertEquals(OfflineAgentCore.GoalStatus.ACTIVE,
                core.snapshot().stream().filter(goal -> goal.id().equals("failed")).findFirst().orElseThrow().status());
    }

    @Test
    void invalidReplacementPlanDoesNotMutateExistingGoal() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("invalid-replan", "Sammle Holz", 0,
                List.of(step("gather", "gather_wood", List.of(), 2),
                        step("old-craft", "craft_item", List.of("gather"), 1))));
        core.markStarted("invalid-replan", "gather");
        core.report("invalid-replan", "gather", OfflineAgentCore.ActionResult.SUCCESS, "Holz bestätigt");

        assertThrows(IllegalArgumentException.class, () -> core.replanRemaining(
                "invalid-replan", List.of(step("missing-dependency", "craft_item", List.of("unknown"), 1))));
        assertEquals(OfflineAgentCore.StepStatus.SUCCEEDED,
                core.snapshot().get(0).steps().get(0).status());
        assertEquals(2, core.snapshot().get(0).steps().size());
    }


    @Test
    void failedGoalCanBeReopenedWithAReplacementPlanAfterFreshObservation() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("recover", "Baue eine Hütte", 3,
                List.of(step("old-build", "build_structure", List.of(), 1))));
        core.markStarted("recover", "old-build");
        core.report("recover", "old-build", OfflineAgentCore.ActionResult.PERMANENT_FAILURE,
                "Vorschau meldet fehlendes Material");
        assertEquals(OfflineAgentCore.GoalStatus.FAILED, core.snapshot().get(0).status());

        OfflineAgentCore.Step gather = step("gather", "gather_wood", List.of(), 2);
        OfflineAgentCore.Step revisedBuild = step("revised-build", "build_structure", List.of("gather"), 1);
        core.replanRemaining("recover", List.of(gather, revisedBuild));

        OfflineAgentCore.GoalView reopened = core.snapshot().get(0);
        assertEquals(OfflineAgentCore.GoalStatus.ACTIVE, reopened.status());
        assertEquals(List.of("gather", "revised-build"),
                reopened.steps().stream().map(OfflineAgentCore.StepView::id).toList());
        assertEquals("gather", core.nextReadyStep().orElseThrow().step().id());
    }

}

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
    void duplicateGoalIdsAreRejected() {
        OfflineAgentCore core = new OfflineAgentCore();
        core.submit(new OfflineAgentCore.Goal("same", "Erster Plan", 0,
                List.of(step("a", "action_a", List.of(), 1))));
        assertThrows(IllegalArgumentException.class, () -> core.submit(
                new OfflineAgentCore.Goal("same", "Zweiter Plan", 0,
                        List.of(step("b", "action_b", List.of(), 1)))));
    }
}

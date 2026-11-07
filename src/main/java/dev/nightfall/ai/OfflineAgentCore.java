package dev.nightfall.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic, model-free task coordinator for Nightfall.
 *
 * This class does not interpret chat, inspect Minecraft, or execute game actions.
 * It accepts already structured goals, selects dependency-ready steps, bounds retries,
 * and requires explicit verification when an action result is uncertain.
 */
public final class OfflineAgentCore {
    public static final int MAX_STEPS_PER_GOAL = 64;
    public static final int MAX_ACTIVE_GOALS = 16;
    public static final int MAX_RETAINED_GOALS = 128;
    private static final int MAX_PARAMETERS = 16;
    private static final int MAX_STEP_PARAMETER_CHARS = 32_768;

    public enum StepStatus {
        PENDING, RUNNING, VERIFYING, SUCCEEDED, FAILED, BLOCKED, CANCEL_REQUESTED, CANCELLED
    }

    public enum GoalStatus { ACTIVE, SUCCEEDED, FAILED, CANCELLED }

    public enum ActionResult { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE, UNCERTAIN }

    public record Step(
            String id,
            String action,
            Map<String, String> parameters,
            List<String> dependsOn,
            int maxAttempts) {
        public Step {
            requireId(id, "step id");
            requireId(action, "action");
            parameters = immutableParameters(parameters);
            dependsOn = List.copyOf(Objects.requireNonNull(dependsOn, "dependsOn"));
            if (dependsOn.size() > MAX_STEPS_PER_GOAL || dependsOn.contains(id)
                    || dependsOn.stream().anyMatch(value -> value == null || !value.matches("[a-zA-Z0-9_.-]{1,64}"))) {
                throw new IllegalArgumentException("Invalid step dependencies.");
            }
            if (maxAttempts < 1 || maxAttempts > 5) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and 5.");
            }
        }
    }

    public record Goal(String id, String description, int priority, List<Step> steps) {
        public Goal {
            requireId(id, "goal id");
            if (description == null || description.isBlank() || description.length() > 512) {
                throw new IllegalArgumentException("Goal description must contain 1 to 512 characters.");
            }
            if (priority < -100 || priority > 100) {
                throw new IllegalArgumentException("Goal priority must be between -100 and 100.");
            }
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            if (steps.isEmpty() || steps.size() > MAX_STEPS_PER_GOAL) {
                throw new IllegalArgumentException("A goal must contain 1 to 64 steps.");
            }
            validateGraph(steps);
        }
    }

    public record StepCheckpoint(Step step, StepStatus status, int attempts, String detail) {
        public StepCheckpoint {
            Objects.requireNonNull(step, "step");
            Objects.requireNonNull(status, "status");
            if (attempts < 0 || attempts > step.maxAttempts()
                    || ((status == StepStatus.RUNNING || status == StepStatus.VERIFYING
                    || status == StepStatus.CANCEL_REQUESTED || status == StepStatus.SUCCEEDED
                    || status == StepStatus.FAILED) && attempts == 0))
                throw new IllegalArgumentException("Invalid checkpoint attempt count.");
            detail = boundedDetail(detail);
        }
    }

    public record GoalCheckpoint(Goal goal, GoalStatus status, List<StepCheckpoint> steps) {
        public GoalCheckpoint {
            Objects.requireNonNull(goal, "goal");
            Objects.requireNonNull(status, "status");
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            if (status != GoalStatus.ACTIVE || steps.size() != goal.steps().size())
                throw new IllegalArgumentException("Only complete active goal checkpoints may be restored.");
        }
    }

    public record StepRef(String goalId, Step step, int attempts) {}
    public record StepView(String id, String action, StepStatus status, int attempts, String detail) {}
    public record GoalView(String id, String description, int priority, GoalStatus status, List<StepView> steps) {
        public GoalView { steps = List.copyOf(steps); }
    }

    private static final class StepState {
        private final Step step;
        private StepStatus status = StepStatus.PENDING;
        private int attempts;
        private String detail = "";

        private StepState(Step step) { this.step = step; }
    }

    private static final class GoalState {
        private final Goal goal;
        private final LinkedHashMap<String, StepState> steps = new LinkedHashMap<>();
        private GoalStatus status = GoalStatus.ACTIVE;

        private GoalState(Goal goal) {
            this.goal = goal;
            for (Step step : goal.steps()) steps.put(step.id(), new StepState(step));
        }
    }

    private final LinkedHashMap<String, GoalState> goals = new LinkedHashMap<>();
    private long revision;

    /** Adds a validated goal. Existing goal IDs are never overwritten. */
    public synchronized void submit(Goal goal) {
        Objects.requireNonNull(goal, "goal");
        if (goals.containsKey(goal.id())) throw new IllegalArgumentException("Goal ID already exists.");
        long active = goals.values().stream().filter(state -> state.status == GoalStatus.ACTIVE).count();
        if (active >= MAX_ACTIVE_GOALS) throw new IllegalStateException("Too many active goals.");
        while (goals.size() >= MAX_RETAINED_GOALS) {
            String oldestFinished = goals.entrySet().stream()
                    .filter(entry -> entry.getValue().status != GoalStatus.ACTIVE)
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (oldestFinished == null) throw new IllegalStateException("Goal history is full.");
            goals.remove(oldestFinished);
        }
        goals.put(goal.id(), new GoalState(goal));
        revision++;
    }

    /** Monotonic in-memory revision used to avoid writing unchanged checkpoints. */
    public synchronized long revision() {
        return revision;
    }

    /** Export only active goals; completed history belongs in OfflineAgentMemory. */
    public synchronized List<GoalCheckpoint> exportActiveCheckpoints() {
        List<GoalCheckpoint> result = new ArrayList<>();
        for (GoalState goal : goals.values()) {
            if (goal.status != GoalStatus.ACTIVE) continue;
            List<StepCheckpoint> steps = new ArrayList<>();
            for (StepState state : goal.steps.values()) {
                steps.add(new StepCheckpoint(state.step, state.status, state.attempts, state.detail));
            }
            result.add(new GoalCheckpoint(goal.goal, goal.status, steps));
        }
        return List.copyOf(result);
    }

    /**
     * Restore active goals after a client restart. A step that was RUNNING,
     * VERIFYING or awaiting cancellation is restored as VERIFYING, never PENDING:
     * its world/inventory effects must be checked by the player before dependents
     * can run or a retry can start.
     */
    public synchronized int restoreActiveCheckpoints(List<GoalCheckpoint> checkpoints) {
        Objects.requireNonNull(checkpoints, "checkpoints");
        if (!goals.isEmpty()) throw new IllegalStateException("Agent state must be empty before restoring checkpoints.");
        if (checkpoints.size() > MAX_ACTIVE_GOALS) throw new IllegalArgumentException("Too many active goal checkpoints.");
        int inFlight = 0;
        Set<String> restoredIds = new LinkedHashSet<>();
        List<GoalState> restored = new ArrayList<>();
        for (GoalCheckpoint checkpoint : checkpoints) {
            Objects.requireNonNull(checkpoint, "checkpoint");
            Goal goal = checkpoint.goal();
            if (checkpoint.status() != GoalStatus.ACTIVE || !restoredIds.add(goal.id()))
                throw new IllegalArgumentException("Invalid or duplicate active goal checkpoint.");
            GoalState state = new GoalState(goal);
            if (checkpoint.steps().size() != state.steps.size())
                throw new IllegalArgumentException("Checkpoint step count does not match its goal.");
            Set<String> restoredStepIds = new LinkedHashSet<>();
            for (StepCheckpoint stepCheckpoint : checkpoint.steps()) {
                if (!restoredStepIds.add(stepCheckpoint.step().id()))
                    throw new IllegalArgumentException("Checkpoint contains duplicate step IDs.");
                StepState stepState = state.steps.get(stepCheckpoint.step().id());
                if (stepState == null || !stepState.step.equals(stepCheckpoint.step()))
                    throw new IllegalArgumentException("Checkpoint step definition does not match its goal.");
                if (stepCheckpoint.attempts() > stepState.step.maxAttempts())
                    throw new IllegalArgumentException("Checkpoint attempt count exceeds its limit.");
                stepState.attempts = stepCheckpoint.attempts();
                stepState.status = stepCheckpoint.status();
                stepState.detail = boundedDetail(stepCheckpoint.detail());
                if (stepState.status == StepStatus.RUNNING
                        || stepState.status == StepStatus.VERIFYING
                        || stepState.status == StepStatus.CANCEL_REQUESTED) {
                    inFlight++;
                    stepState.status = StepStatus.VERIFYING;
                    stepState.detail = "Der vorherige Clientlauf wurde unterbrochen. Prüfe Welt und Inventar vor der Fortsetzung.";
                }
            }
            if (restoredStepIds.size() != state.steps.size())
                throw new IllegalArgumentException("Checkpoint omits one or more goal steps.");
            refreshGoal(state);
            restored.add(state);
        }
        if (inFlight > 1) throw new IllegalArgumentException("Checkpoint contains more than one interrupted in-flight action.");
        for (GoalState state : restored) goals.put(state.goal.id(), state);
        revision++;
        return inFlight;
    }

    /** Clears in-memory goals after their world-scoped queue has been saved. */
    public synchronized void clearAll() {
        if (!goals.isEmpty()) {
            goals.clear();
            revision++;
        }
    }

    /**
     * Selects the next dependency-ready step. Highest-priority goals win;
     * insertion order and declared step order break ties deterministically.
     */
    public synchronized Optional<StepRef> nextReadyStep() {
        if (hasInFlightAction()) return Optional.empty();
        GoalState selectedGoal = goals.values().stream()
                .filter(state -> state.status == GoalStatus.ACTIVE)
                .sorted((left, right) -> Integer.compare(right.goal.priority(), left.goal.priority()))
                .filter(state -> state.steps.values().stream().anyMatch(step -> isReady(state, step)))
                .findFirst().orElse(null);
        if (selectedGoal == null) return Optional.empty();

        for (StepState state : selectedGoal.steps.values()) {
            if (isReady(selectedGoal, state)) return Optional.of(new StepRef(
                    selectedGoal.goal.id(), state.step, state.attempts));
        }
        return Optional.empty();
    }

    /** Marks a selected step as running and consumes one bounded attempt. */
    public synchronized void markStarted(String goalId, String stepId) {
        if (hasInFlightAction()) throw new IllegalStateException("Only one Minecraft action may run at a time.");
        StepState state = findStep(goalId, stepId);
        if (state.status != StepStatus.PENDING || !dependenciesSucceeded(goalId, state.step)) {
            throw new IllegalStateException("Step is not ready to start.");
        }
        if (state.attempts >= state.step.maxAttempts()) {
            state.status = StepStatus.FAILED;
            state.detail = "Maximale Versuchsanzahl erreicht.";
            refreshGoal(findGoal(goalId));
            throw new IllegalStateException("Step has no attempts remaining.");
        }
        state.attempts++;
        state.status = StepStatus.RUNNING;
        state.detail = "";
        revision++;
    }

    /** Reports an action result. UNCERTAIN deliberately does not count as success. */
    public synchronized void report(String goalId, String stepId, ActionResult result, String detail) {
        Objects.requireNonNull(result, "result");
        StepState state = findStep(goalId, stepId);
        if (state.status != StepStatus.RUNNING) throw new IllegalStateException("Step is not running.");
        state.detail = boundedDetail(detail);
        switch (result) {
            case SUCCESS -> state.status = StepStatus.SUCCEEDED;
            case UNCERTAIN -> state.status = StepStatus.VERIFYING;
            case PERMANENT_FAILURE -> state.status = StepStatus.FAILED;
            case RETRYABLE_FAILURE -> state.status = state.attempts < state.step.maxAttempts()
                    ? StepStatus.PENDING : StepStatus.FAILED;
        }
        refreshGoal(findGoal(goalId));
        revision++;
    }

    /** Resolves a VERIFYING step using a fresh observation from the game integration. */
    public synchronized void verify(String goalId, String stepId, boolean confirmedSuccess, String detail) {
        StepState state = findStep(goalId, stepId);
        if (state.status != StepStatus.VERIFYING) throw new IllegalStateException("Step is not awaiting verification.");
        state.detail = boundedDetail(detail);
        if (confirmedSuccess) state.status = StepStatus.SUCCEEDED;
        else state.status = state.attempts < state.step.maxAttempts()
                ? StepStatus.PENDING : StepStatus.FAILED;
        refreshGoal(findGoal(goalId));
        revision++;
    }

    /**
     * Requests cancellation. A running action is marked CANCEL_REQUESTED and must
     * be stopped by the Minecraft integration before acknowledgeStopped is called.
     */
    public synchronized void cancel(String goalId) {
        GoalState goal = findGoal(goalId);
        if (goal.status != GoalStatus.ACTIVE) return;
        for (StepState state : goal.steps.values()) {
            if (state.status == StepStatus.RUNNING) state.status = StepStatus.CANCEL_REQUESTED;
            else if (!isTerminal(state.status)) state.status = StepStatus.CANCELLED;
        }
        refreshGoal(goal);
        revision++;
    }

    public synchronized void acknowledgeStopped(String goalId, String stepId) {
        StepState state = findStep(goalId, stepId);
        if (state.status != StepStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("Step is not awaiting stop acknowledgement.");
        }
        state.status = StepStatus.CANCELLED;
        state.detail = "Aktion wurde gestoppt.";
        refreshGoal(findGoal(goalId));
        revision++;
    }

    public synchronized List<GoalView> snapshot() {
        List<GoalView> result = new ArrayList<>();
        for (GoalState goal : goals.values()) {
            List<StepView> steps = new ArrayList<>();
            for (StepState state : goal.steps.values()) {
                steps.add(new StepView(state.step.id(), state.step.action(), state.status,
                        state.attempts, state.detail));
            }
            result.add(new GoalView(goal.goal.id(), goal.goal.description(), goal.goal.priority(),
                    goal.status, steps));
        }
        return List.copyOf(result);
    }

    private boolean hasInFlightAction() {
        return goals.values().stream().flatMap(goal -> goal.steps.values().stream())
                .anyMatch(state -> state.status == StepStatus.RUNNING
                        || state.status == StepStatus.VERIFYING
                        || state.status == StepStatus.CANCEL_REQUESTED);
    }

    private boolean isReady(GoalState goal, StepState state) {
        if (state.status != StepStatus.PENDING) return false;
        for (String dependency : state.step.dependsOn()) {
            StepState dependencyState = goal.steps.get(dependency);
            if (dependencyState == null || dependencyState.status != StepStatus.SUCCEEDED) return false;
        }
        return true;
    }

    private boolean dependenciesSucceeded(String goalId, Step step) {
        GoalState goal = findGoal(goalId);
        for (String dependency : step.dependsOn()) {
            StepState dependencyState = goal.steps.get(dependency);
            if (dependencyState == null || dependencyState.status != StepStatus.SUCCEEDED) return false;
        }
        return true;
    }

    private StepState findStep(String goalId, String stepId) {
        GoalState goal = findGoal(goalId);
        StepState state = goal.steps.get(stepId);
        if (state == null) throw new IllegalArgumentException("Unknown step ID.");
        return state;
    }

    private GoalState findGoal(String goalId) {
        GoalState goal = goals.get(goalId);
        if (goal == null) throw new IllegalArgumentException("Unknown goal ID.");
        return goal;
    }

    private void refreshGoal(GoalState goal) {
        boolean hasFailure = goal.steps.values().stream().anyMatch(state ->
                state.status == StepStatus.FAILED || state.status == StepStatus.BLOCKED);
        if (hasFailure) {
            for (StepState state : goal.steps.values()) {
                if (state.status == StepStatus.PENDING) {
                    state.status = StepStatus.BLOCKED;
                    state.detail = "Aufgabe wegen eines fehlgeschlagenen Planschritts angehalten.";
                }
            }
        }
        if (goal.steps.values().stream().allMatch(state -> state.status == StepStatus.SUCCEEDED)) {
            goal.status = GoalStatus.SUCCEEDED;
        } else if (goal.steps.values().stream().allMatch(state -> isTerminal(state.status))) {
            goal.status = hasFailure ? GoalStatus.FAILED : GoalStatus.CANCELLED;
        }
    }

    private static boolean isTerminal(StepStatus status) {
        return status == StepStatus.SUCCEEDED || status == StepStatus.FAILED
                || status == StepStatus.BLOCKED || status == StepStatus.CANCELLED;
    }

    private static String boundedDetail(String detail) {
        if (detail == null) return "";
        return detail.length() <= 512 ? detail : detail.substring(0, 512);
    }

    private static Map<String, String> immutableParameters(Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.size() > MAX_PARAMETERS) throw new IllegalArgumentException("Too many step parameters.");
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        int totalCharacters = 0;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || !key.matches("[a-zA-Z0-9_.-]{1,64}") || value == null) {
                throw new IllegalArgumentException("Invalid step parameter.");
            }
            totalCharacters = Math.addExact(totalCharacters, key.length() + value.length());
            if (totalCharacters > MAX_STEP_PARAMETER_CHARS) {
                throw new IllegalArgumentException("Step parameters exceed the 32 KiB limit.");
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void validateGraph(List<Step> steps) {
        Set<String> ids = new LinkedHashSet<>();
        for (Step step : steps) {
            Objects.requireNonNull(step, "step");
            if (!ids.add(step.id())) throw new IllegalArgumentException("Duplicate step ID.");
        }
        for (Step step : steps) {
            for (String dependency : step.dependsOn()) {
                if (!ids.contains(dependency)) throw new IllegalArgumentException("Unknown step dependency.");
            }
        }
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        for (Step step : steps) visit(step.id(), steps, visiting, visited);
    }

    private static void visit(String id, List<Step> steps, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("Cyclic step dependencies.");
        Step current = steps.stream().filter(step -> step.id().equals(id)).findFirst().orElseThrow();
        for (String dependency : current.dependsOn()) visit(dependency, steps, visiting, visited);
        visiting.remove(id);
        visited.add(id);
    }

    private static void requireId(String value, String label) {
        if (value == null || !value.matches("[a-zA-Z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("Invalid " + label + ".");
        }
    }
}

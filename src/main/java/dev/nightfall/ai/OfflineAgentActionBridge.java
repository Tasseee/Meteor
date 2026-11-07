package dev.nightfall.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Main-thread bridge between the model-free task coordinator and Nightfall's
 * existing, locally validated Minecraft actions.
 *
 * Preparing a step never executes it. A caller must present the preview and
 * explicitly confirm it. Execution results remain VERIFYING until the caller
 * supplies a fresh game observation; a returned status string is not proof that
 * an asynchronous Minecraft action completed successfully.
 */
public final class OfflineAgentActionBridge {
    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "walk_forward", "search_structures", "navigate_to", "mine_block",
            "mine_looked_block", "gather_wood", "gather_block", "gather_ore",
            "smelt_item", "craft_item", "eat_food", "sort_inventory"
    );
    private static final Set<String> INTEGER_PARAMETERS = Set.of(
            "ticks", "x", "y", "z", "count", "duration_seconds", "target_y"
    );

    public record PreparedStep(String goalId, String stepId, String preview) {}

    private final OfflineAgentCore core;
    private PreparedStep pending;
    private AIActionRegistry.Action pendingAction;
    private String verifyingGoalId;
    private String verifyingStepId;
    private String lastError = "";

    public OfflineAgentActionBridge(OfflineAgentCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    /**
     * Prepares the next ready step using the existing local action validator.
     * Must be called on the Minecraft client thread because previews may inspect
     * the current world. The returned preview must be confirmed separately.
     */
    public synchronized Optional<PreparedStep> prepareNext() {
        if (pending != null || verifyingGoalId != null) return Optional.empty();
        Optional<OfflineAgentCore.StepRef> next = core.nextReadyStep();
        if (next.isEmpty()) return Optional.empty();

        OfflineAgentCore.StepRef ref = next.get();
        try {
            if (!ALLOWED_ACTIONS.contains(ref.step().action())) {
                throw new IllegalArgumentException("Aktion ist für den Offline-Agenten nicht freigegeben.");
            }
            AIActionRegistry.Action action = AIActionRegistry.prepare(
                    ref.step().action(), toJson(ref.step().parameters()).toString());
            pendingAction = action;
            pending = new PreparedStep(ref.goalId(), ref.step().id(), action.preview());
            lastError = "";
            return Optional.of(pending);
        } catch (RuntimeException invalid) {
            core.markStarted(ref.goalId(), ref.step().id());
            core.report(ref.goalId(), ref.step().id(),
                    OfflineAgentCore.ActionResult.PERMANENT_FAILURE,
                    safeMessage(invalid));
            lastError = safeMessage(invalid);
            return Optional.empty();
        }
    }

    /** Executes only the exact step whose preview was most recently returned. */
    public synchronized String confirm(PreparedStep confirmation) {
        if (pending == null || pending != confirmation) {
            throw new IllegalStateException("Es liegt keine passende Aktionsvorschau zur Bestätigung vor.");
        }
        String goalId = pending.goalId();
        String stepId = pending.stepId();
        pending = null;
        AIActionRegistry.Action action = pendingAction;
        pendingAction = null;
        core.markStarted(goalId, stepId);
        try {
            String result = AIActionRegistry.execute(action);
            core.report(goalId, stepId, OfflineAgentCore.ActionResult.UNCERTAIN,
                    "Aktion ausgeführt; Abschluss muss anhand einer neuen Spielbeobachtung geprüft werden. " + result);
            verifyingGoalId = goalId;
            verifyingStepId = stepId;
            lastError = "";
            return result;
        } catch (RuntimeException failure) {
            if (AIPlayerController.isBusy()) {
                AIPlayerController.stop("Offline-Agentenaktion nach einem Ausführungsfehler gestoppt.");
            }
            String detail = safeMessage(failure);
            core.report(goalId, stepId, OfflineAgentCore.ActionResult.PERMANENT_FAILURE, detail);
            lastError = detail;
            return "Aktion fehlgeschlagen: " + detail;
        }
    }

    /** Rejects a preview without sending any Minecraft action. */
    public synchronized void reject(PreparedStep rejection) {
        if (pending == null || pending != rejection) {
            throw new IllegalStateException("Es liegt keine passende Aktionsvorschau vor.");
        }
        String goalId = pending.goalId();
        String stepId = pending.stepId();
        pending = null;
        pendingAction = null;
        core.markStarted(goalId, stepId);
        core.report(goalId, stepId, OfflineAgentCore.ActionResult.PERMANENT_FAILURE,
                "Aktion wurde vor der Ausführung abgelehnt.");
    }

    /**
     * Resolves the last executed step using a fresh observation supplied by the
     * game integration. Never call this merely because execute() returned.
     */
    public synchronized void verifyLastAction(boolean confirmedSuccess, String observation) {
        if (verifyingGoalId == null) throw new IllegalStateException("Keine Aktion wartet auf Überprüfung.");
        core.verify(verifyingGoalId, verifyingStepId, confirmedSuccess, observation);
        verifyingGoalId = null;
        verifyingStepId = null;
    }

    /** Cancels a prepared preview; no Minecraft action has been executed. */
    public synchronized void cancelPending() {
        pending = null;
        pendingAction = null;
    }

    /**
     * Stops a currently verifying controller action, if one is still active,
     * and marks the corresponding goal cancelled.
     */
    public synchronized void cancelVerifyingAction() {
        if (verifyingGoalId == null) return;
        if (AIPlayerController.isBusy()) AIPlayerController.stop("Offline-Agentenaktion abgebrochen.");
        core.cancel(verifyingGoalId);
        verifyingGoalId = null;
        verifyingStepId = null;
    }

    public synchronized Optional<PreparedStep> pendingPreview() {
        return Optional.ofNullable(pending);
    }

    public synchronized boolean awaitingVerification() {
        return verifyingGoalId != null;
    }

    public synchronized String lastError() {
        return lastError;
    }

    private JsonObject toJson(Map<String, String> parameters) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (INTEGER_PARAMETERS.contains(key)) {
                try {
                    json.add(key, new JsonPrimitive(Integer.parseInt(value)));
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("Parameter '" + key + "' muss eine ganze Zahl sein.");
                }
            } else {
                json.addProperty(key, value);
            }
        }
        return json;
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}

package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
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
    private static volatile Map<String, Map<String, String>> ACTION_SCHEMAS;

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
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) throw new IllegalStateException("Agentenaktionen müssen auf dem Minecraft-Client-Thread vorbereitet werden.");
        if (client.player == null || client.world == null) return Optional.empty();
        if (pending != null || verifyingGoalId != null) return Optional.empty();
        // Do not replace an action started outside the offline queue.
        if (AIPlayerController.isBusy() || AICombatController.isActive() || AIBuilder.isRunning())
            return Optional.empty();
        Optional<OfflineAgentCore.StepRef> next = core.nextReadyStep();
        if (next.isEmpty()) return Optional.empty();

        OfflineAgentCore.StepRef ref = next.get();
        try {
            if (!actionSchemas().containsKey(ref.step().action())) {
                throw new IllegalArgumentException("Aktion ist nicht in der lokalen Offline-Allowlist oder benötigt komplexe Parameter.");
            }
            AIActionRegistry.Action action = AIActionRegistry.prepare(
                    ref.step().action(), toJson(ref.step().action(), ref.step().parameters()).toString());
            pendingAction = action;
            pending = new PreparedStep(ref.goalId(), ref.step().id(), action.preview());
            lastError = "";
            client.player.sendMessage(Text.literal("[Nightfall] Offline-Agent-Vorschau für "
                    + ref.goalId() + "/" + ref.step().id() + ":\n" + action.preview()
                    + "\nBestätigen: /nf agent confirm · Ablehnen: /nf agent reject"), false);
            return Optional.of(pending);
        } catch (RuntimeException invalid) {
            core.markStarted(ref.goalId(), ref.step().id());
            core.report(ref.goalId(), ref.step().id(),
                    OfflineAgentCore.ActionResult.PERMANENT_FAILURE,
                    safeMessage(invalid));
            lastError = safeMessage(invalid);
            client.player.sendMessage(Text.literal("[Nightfall] Offline-Agent: Auftrag konnte nicht vorbereitet werden: "
                    + lastError), false);
            return Optional.empty();
        }
    }

    /** Executes only the exact step whose preview was most recently returned. */
    public synchronized String confirm(PreparedStep confirmation) {
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Agentenaktionen müssen auf dem Minecraft-Client-Thread ausgeführt werden.");
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Agentenaktionen müssen auf dem Minecraft-Client-Thread bestätigt werden.");
        if (pending == null || pending != confirmation) {
            throw new IllegalStateException("Es liegt keine passende Aktionsvorschau zur Bestätigung vor.");
        }
        if (AIPlayerController.isBusy() || AICombatController.isActive() || AIBuilder.isRunning())
            throw new IllegalStateException("Eine andere Nightfall-Aktion läuft bereits. Stoppe sie zuerst.");
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
            if (AIPlayerController.isBusy())
                AIPlayerController.stop("Offline-Agentenaktion nach einem Ausführungsfehler gestoppt.");
            if (AICombatController.isActive())
                AICombatController.stop("Offline-Agentenaktion nach einem Ausführungsfehler gestoppt.");
            if (AIBuilder.isRunning()) AIBuilder.stop();
            AIActionRegistry.cancel(action);
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
        AIActionRegistry.Action action = pendingAction;
        pending = null;
        pendingAction = null;
        AIActionRegistry.cancel(action);
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

    /** Cancels the goal behind a prepared preview; no Minecraft action is executed. */
    public synchronized void cancelPending() {
        if (pending == null) return;
        core.cancel(pending.goalId());
        AIActionRegistry.cancel(pendingAction);
        pending = null;
        pendingAction = null;
    }

    /**
     * Stops a currently verifying controller action, if one is still active,
     * and marks the corresponding goal cancelled.
     */
    public synchronized void cancelVerifyingAction() {
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Agentenaktionen müssen auf dem Minecraft-Client-Thread gestoppt werden.");
        if (verifyingGoalId == null) return;
        if (AIPlayerController.isBusy()) AIPlayerController.stop("Offline-Agentenaktion abgebrochen.");
        if (AICombatController.isActive()) AICombatController.stop("Offline-Agentenaktion abgebrochen.");
        if (AIBuilder.isRunning()) AIBuilder.stop();
        String goalId = verifyingGoalId;
        String stepId = verifyingStepId;
        core.cancel(goalId);
        core.acknowledgeStopped(goalId, stepId);
        verifyingGoalId = null;
        verifyingStepId = null;
    }

    /**
     * Drop in-memory agent work when leaving a server. The game controllers save
     * their own supported checkpoints before this is called.
     */
    public synchronized void onDisconnect() {
        if (pending != null) {
            core.cancel(pending.goalId());
            pending = null;
            pendingAction = null;
        }
        for (OfflineAgentCore.GoalView goal : core.snapshot()) {
            if (goal.status() == OfflineAgentCore.GoalStatus.ACTIVE) core.cancel(goal.id());
        }
        for (OfflineAgentCore.GoalView goal : core.snapshot()) {
            for (OfflineAgentCore.StepView step : goal.steps()) {
                if (step.status() == OfflineAgentCore.StepStatus.CANCEL_REQUESTED) {
                    core.acknowledgeStopped(goal.id(), step.id());
                }
            }
        }
        verifyingGoalId = null;
        verifyingStepId = null;
    }

    /** Cancels every active offline goal and safely stops the active action. */
    public synchronized void cancelAll() {
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Agentenaktionen müssen auf dem Minecraft-Client-Thread gestoppt werden.");
        cancelVerifyingAction();
        cancelPending();
        for (OfflineAgentCore.GoalView goal : core.snapshot()) {
            if (goal.status() == OfflineAgentCore.GoalStatus.ACTIVE) core.cancel(goal.id());
        }
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

    private JsonObject toJson(String action, Map<String, String> parameters) {
        Map<String, String> schema = actionSchemas().get(action);
        if (schema == null) throw new IllegalArgumentException("Aktion ist nicht lokal freigegeben.");
        JsonObject json = new JsonObject();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String type = schema.get(key);
            if (type == null) {
                throw new IllegalArgumentException("Parameter '" + key + "' ist für diese Aktion nicht erlaubt.");
            }
            switch (type) {
                case "integer" -> {
                    try {
                        json.add(key, new JsonPrimitive(Integer.parseInt(value)));
                    } catch (NumberFormatException invalid) {
                        throw new IllegalArgumentException("Parameter '" + key + "' muss eine ganze Zahl sein.");
                    }
                }
                case "number" -> {
                    try {
                        double number = Double.parseDouble(value);
                        if (!Double.isFinite(number)) throw new NumberFormatException();
                        json.addProperty(key, number);
                    } catch (NumberFormatException invalid) {
                        throw new IllegalArgumentException("Parameter '" + key + "' muss eine gültige Zahl sein.");
                    }
                }
                case "boolean" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                        throw new IllegalArgumentException("Parameter '" + key + "' muss true oder false sein.");
                    json.addProperty(key, Boolean.parseBoolean(value));
                }
                case "string" -> json.addProperty(key, value);
                default -> throw new IllegalArgumentException("Komplexe Parameter sind im Offline-Auftragsformat nicht verfügbar.");
            }
        }
        return json;
    }

    /**
     * Reuses the existing local action registry as the source of truth. Only tools
     * whose parameters are scalar JSON values can be queued by OfflineAgentCore.
     * The registry still validates required values, ranges, enums and permissions.
     */
    private static Map<String, Map<String, String>> actionSchemas() {
        Map<String, Map<String, String>> cached = ACTION_SCHEMAS;
        if (cached != null) return cached;
        synchronized (OfflineAgentActionBridge.class) {
            cached = ACTION_SCHEMAS;
            if (cached != null) return cached;
            JsonArray definitions = AIActionRegistry.toolDefinitions();
            Map<String, Map<String, String>> actions = new LinkedHashMap<>();
            for (JsonElement definition : definitions) {
                if (!definition.isJsonObject()) continue;
                JsonObject function = definition.getAsJsonObject().getAsJsonObject("function");
                if (function == null || !function.has("name") || !function.has("parameters")) continue;
                JsonObject parameterSchema = function.getAsJsonObject("parameters");
                JsonObject properties = parameterSchema.getAsJsonObject("properties");
                if (properties == null) continue;
                Map<String, String> fields = new LinkedHashMap<>();
                boolean scalarOnly = true;
                for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
                    if (!property.getValue().isJsonObject() || !property.getValue().getAsJsonObject().has("type")) {
                        scalarOnly = false;
                        break;
                    }
                    String type = property.getValue().getAsJsonObject().get("type").getAsString();
                    if (!Set.of("string", "integer", "number", "boolean").contains(type)) {
                        scalarOnly = false;
                        break;
                    }
                    fields.put(property.getKey(), type);
                }
                if (scalarOnly) actions.put(function.get("name").getAsString(), Map.copyOf(fields));
            }
            cached = Map.copyOf(actions);
            ACTION_SCHEMAS = cached;
            return cached;
        }
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return error.getClass().getSimpleName();
        return message.length() <= 512 ? message : message.substring(0, 512);
    }
}

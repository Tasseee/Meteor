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
    private String setupPromptKey = "";
    private String setupPrompt = "";
    private VerificationKind verificationKind = VerificationKind.NONE;
    private long playerSequenceAtStart;
    private long builderSequenceAtStart;
    private long combatSequenceAtStart;

    private enum VerificationKind { NONE, PLAYER_CONTROLLER, BUILDER, COMBAT, EXTERNAL }

    public OfflineAgentActionBridge(OfflineAgentCore core) {
        this.core = Objects.requireNonNull(core, "core");
    }

    /** True when an existing local action has a scalar-only offline parameter schema. */
    public static boolean supportsAction(String actionName) {
        return actionName != null && actionSchemas().containsKey(actionName);
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
        if (next.isEmpty()) {
            setupPromptKey = "";
            setupPrompt = "";
            return Optional.empty();
        }

        OfflineAgentCore.StepRef ref = next.get();
        if (!"smelt_item".equals(ref.step().action())) {
            setupPromptKey = "";
            setupPrompt = "";
        }
        if ("smelt_item".equals(ref.step().action()) && !AIPlayerController.isReadyToPreviewSmelting()) {
            String promptKey = ref.goalId() + "/" + ref.step().id();
            setupPrompt = "Schließe zuerst das Ofenfenster (falls offen) und richte dein Fadenkreuz auf einen leeren normalen Ofen oder Hochofen. Sobald das passt, erstellt Nightfall automatisch die Schmelzvorschau.";
            if (!promptKey.equals(setupPromptKey)) {
                setupPromptKey = promptKey;
                client.player.sendMessage(Text.literal("[Nightfall] Offline-Agent wartet auf dich: " + setupPrompt), false);
            }
            return Optional.empty();
        }
        setupPromptKey = "";
        setupPrompt = "";
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
        verificationKind = verificationKindFor(action.name());
        playerSequenceAtStart = AIPlayerController.actionCompletionSequence();
        builderSequenceAtStart = AIBuilder.completionSequence();
        combatSequenceAtStart = AICombatController.completionSequence();
        try {
            String result = AIActionRegistry.execute(action);
            if (verificationKind == VerificationKind.NONE) {
                core.report(goalId, stepId, OfflineAgentCore.ActionResult.SUCCESS,
                        "Lokale Aktion wurde ausgeführt und hat ein direktes Ergebnis geliefert. " + result);
                lastError = "";
                return result;
            }
            if (verificationKind != VerificationKind.EXTERNAL && !controllerActionStarted()) {
                boolean alreadyAtGoal = verificationKind == VerificationKind.PLAYER_CONTROLLER
                        && OfflineAgentOutcome.isSuccessfulImmediateControllerResult(result);
                if (alreadyAtGoal) {
                    core.report(goalId, stepId, OfflineAgentCore.ActionResult.SUCCESS,
                            "Lokales Ergebnis: " + result);
                    verificationKind = VerificationKind.NONE;
                    lastError = "";
                    return result;
                }
                core.report(goalId, stepId, OfflineAgentCore.ActionResult.PERMANENT_FAILURE,
                        "Der lokale Controller hat den Auftrag nicht gestartet. " + result);
                verificationKind = VerificationKind.NONE;
                lastError = result;
                return "Aktion wurde nicht gestartet: " + result;
            }
            core.report(goalId, stepId, OfflineAgentCore.ActionResult.UNCERTAIN,
                    "Aktion läuft oder wartet auf ein externes Ergebnis. " + result);
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
     * Polls the existing Minecraft controllers on the client thread. A dependent
     * step is released only after the controller reports a terminal outcome.
     */
    public synchronized void tick() {
        if (verifyingGoalId == null || verificationKind == VerificationKind.NONE
                || verificationKind == VerificationKind.EXTERNAL) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread() || client.player == null || client.world == null) return;

        String completion;
        long currentSequence;
        boolean running;
        switch (verificationKind) {
            case PLAYER_CONTROLLER -> {
                running = AIPlayerController.isBusy();
                currentSequence = AIPlayerController.actionCompletionSequence();
                completion = AIPlayerController.lastActionSummary();
            }
            case BUILDER -> {
                running = AIBuilder.isRunning();
                currentSequence = AIBuilder.completionSequence();
                completion = AIBuilder.lastCompletionStatus();
            }
            case COMBAT -> {
                running = AICombatController.isActive() || AIPlayerController.isNavigating();
                currentSequence = AICombatController.completionSequence();
                completion = AICombatController.lastCompletionStatus();
            }
            default -> { return; }
        }
        if (running) return;
        long baseline = switch (verificationKind) {
            case PLAYER_CONTROLLER -> playerSequenceAtStart;
            case BUILDER -> builderSequenceAtStart;
            case COMBAT -> combatSequenceAtStart;
            default -> Long.MAX_VALUE;
        };
        if (currentSequence <= baseline || completion == null || completion.isBlank()) return;

        boolean success = OfflineAgentOutcome.isSuccessfulControllerCompletion(completion);
        String goalId = verifyingGoalId;
        String stepId = verifyingStepId;
        core.verify(goalId, stepId, success, "Controller-Abschluss: " + completion);
        verifyingGoalId = null;
        verifyingStepId = null;
        verificationKind = VerificationKind.NONE;
        client.player.sendMessage(Text.literal("[Nightfall] Offline-Agent: " + goalId + "/" + stepId
                + (success ? " wurde automatisch als erfolgreich bestätigt."
                : " wurde als unvollständig/fehlgeschlagen erkannt. Ein erneuter Versuch benötigt eine neue Vorschau.")), false);
        prepareNext();
    }

    private boolean controllerActionStarted() {
        return switch (verificationKind) {
            case PLAYER_CONTROLLER -> AIPlayerController.isBusy()
                    || AIPlayerController.actionCompletionSequence() > playerSequenceAtStart;
            case BUILDER -> AIBuilder.isRunning()
                    || AIBuilder.completionSequence() > builderSequenceAtStart;
            case COMBAT -> AICombatController.isActive() || AIPlayerController.isNavigating()
                    || AICombatController.completionSequence() > combatSequenceAtStart;
            case EXTERNAL -> true;
            case NONE -> false;
        };
    }

    private static VerificationKind verificationKindFor(String action) {
        return switch (action) {
            case "walk_forward", "search_structures", "navigate_to", "mine_block",
                    "mine_looked_block", "gather_wood", "gather_block", "gather_ore",
                    "smelt_item", "craft_item", "eat_food", "sort_inventory",
                    "resume_saved_task" -> VerificationKind.PLAYER_CONTROLLER;
            case "build_starter_shelter", "build_structure", "build_schematic",
                    "resume_saved_build" -> VerificationKind.BUILDER;
            case "attack_nearest_hostile", "guard_nearby_hostiles" -> VerificationKind.COMBAT;
            case "locate_structure", "locate_biome", "random_teleport", "run_server_command",
                    "search_bases" -> VerificationKind.EXTERNAL;
            default -> VerificationKind.NONE;
        };
    }

    /** External server actions still require an explicit player observation. */
    public synchronized void verifyLastAction(boolean confirmedSuccess, String observation) {
        if (verifyingGoalId == null) throw new IllegalStateException("Keine Aktion wartet auf Überprüfung.");
        if (verificationKind != VerificationKind.EXTERNAL)
            throw new IllegalStateException("Diese lokale Controller-Aktion wird automatisch anhand ihres Abschlussstatus geprüft. Bitte warte auf die Nightfall-Abschlussmeldung.");
        core.verify(verifyingGoalId, verifyingStepId, confirmedSuccess, observation);
        verifyingGoalId = null;
        verifyingStepId = null;
        verificationKind = VerificationKind.NONE;
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
        verificationKind = VerificationKind.NONE;
        setupPromptKey = "";
        setupPrompt = "";
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
        verificationKind = VerificationKind.NONE;
        setupPromptKey = "";
        setupPrompt = "";
    }

    /** Cancels only a queued or running Builder step, preserving unrelated goals. */
    public synchronized String cancelBuilderAction() {
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Builder-Aufträge müssen auf dem Minecraft-Client-Thread gestoppt werden.");
        if (pending != null && pendingAction != null && isBuilderAction(pendingAction.name())) {
            cancelPending();
            return "Die vorbereitete Builder-Aktion wurde verworfen; es wurde nichts gebaut.";
        }
        if (verifyingGoalId != null && verificationKind == VerificationKind.BUILDER) {
            cancelVerifyingAction();
            return "Der laufende Builder-Auftrag wurde abgebrochen. Andere Offline-Aufträge bleiben eingereiht.";
        }
        return AIBuilder.stop();
    }

    private static boolean isBuilderAction(String action) {
        return Set.of("build_starter_shelter", "build_structure", "build_schematic",
                "resume_saved_build").contains(action);
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
        setupPromptKey = "";
        setupPrompt = "";
    }

    public synchronized Optional<PreparedStep> pendingPreview() {
        return Optional.ofNullable(pending);
    }

    public synchronized boolean awaitingVerification() {
        return verifyingGoalId != null;
    }

    public synchronized boolean awaitingManualVerification() {
        return verifyingGoalId != null && verificationKind == VerificationKind.EXTERNAL;
    }

    public synchronized boolean controllerActionInProgress() {
        return verifyingGoalId != null && verificationKind != VerificationKind.EXTERNAL;
    }

    public synchronized boolean waitingForSetup() {
        return !setupPrompt.isBlank();
    }

    public synchronized String preparationPrompt() {
        return setupPrompt;
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

package dev.nightfall.ai;

import dev.nightfall.Nightfall;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Compact local snapshot of Nightfall's own runtime: enabled modules,
 * controller activity and queued agent goals. No server address, chat log,
 * credentials or action parameters are included.
 */
public final class AILocalRuntimeContext {
    private static final int MAX_MODULES = 24;
    private static final int MAX_GOALS = 5;
    private static final int MAX_STEPS_PER_GOAL = 5;
    private static final int MAX_DESCRIPTION_CHARS = 120;

    public record ModuleInfo(String name, String category) {}
    public record StepInfo(String id, String action, String status, int attempts) {}
    public record GoalInfo(String id, String description, String status, List<StepInfo> steps) {
        public GoalInfo {
            steps = List.copyOf(steps);
        }
    }

    public record Snapshot(
            long observedAtMillis,
            String dimension,
            int enabledModuleCount,
            List<ModuleInfo> enabledModules,
            List<GoalInfo> activeGoals,
            boolean playerControllerBusy,
            boolean navigationActive,
            boolean builderActive,
            boolean combatActive,
            boolean previewPending,
            boolean verificationPending,
            boolean manualVerificationPending,
            int moduleErrorCount) {
        public Snapshot {
            dimension = safe(dimension, 80);
            enabledModules = List.copyOf(enabledModules);
            activeGoals = List.copyOf(activeGoals);
        }
    }

    private AILocalRuntimeContext() {}

    /** Capture on the Minecraft client thread only. */
    public static String capture() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isOnThread()) {
            throw new IllegalStateException("Nightfall runtime context muss auf dem Minecraft-Client-Thread erfasst werden.");
        }
        if (client.player == null || client.world == null) return "";

        List<Module> active = ModuleManager.all().stream().filter(Module::isEnabled).toList();
        List<ModuleInfo> modules = active.stream()
                .limit(MAX_MODULES)
                .map(module -> new ModuleInfo(
                        safe(module.getName(), 48),
                        module.getCategory() == null ? "Unbekannt" : safe(module.getCategory().display, 32)))
                .toList();

        List<GoalInfo> goals = new ArrayList<>();
        for (OfflineAgentCore.GoalView goal : Nightfall.offlineAgentCore().snapshot()) {
            if (goal.status() != OfflineAgentCore.GoalStatus.ACTIVE || goals.size() >= MAX_GOALS) continue;
            List<StepInfo> steps = new ArrayList<>();
            for (OfflineAgentCore.StepView step : goal.steps()) {
                if (step.status() == OfflineAgentCore.StepStatus.SUCCEEDED
                        || step.status() == OfflineAgentCore.StepStatus.CANCELLED) continue;
                steps.add(new StepInfo(
                        safe(step.id(), 48),
                        safe(step.action(), 64),
                        step.status().name(),
                        step.attempts()));
                if (steps.size() >= MAX_STEPS_PER_GOAL) break;
            }
            goals.add(new GoalInfo(
                    safe(goal.id(), 64),
                    safe(goal.description(), MAX_DESCRIPTION_CHARS),
                    goal.status().name(),
                    steps));
        }

        var bridge = Nightfall.offlineAgentBridge();
        String dimension = client.world.getRegistryKey().getValue().toString();
        Snapshot snapshot = new Snapshot(
                System.currentTimeMillis(),
                dimension,
                active.size(),
                modules,
                goals,
                AIPlayerController.isBusy(),
                AIPlayerController.isNavigating(),
                AIBuilder.isRunning(),
                AICombatController.isActive(),
                bridge.pendingPreview().isPresent(),
                bridge.awaitingVerification(),
                bridge.awaitingManualVerification(),
                ModuleManager.errorCount);
        return format(snapshot);
    }

    /** Pure formatter so runtime summaries can be tested without starting Minecraft. */
    public static String format(Snapshot snapshot) {
        if (snapshot == null) return "";
        StringBuilder out = new StringBuilder(1_600);
        out.append("Nightfall-Runtime-Snapshot (lokal vom Client beobachtet):\n")
                .append("Erfasst: ").append(snapshot.observedAtMillis()).append(" ms seit Unix-Epoch\n")
                .append("Dimension: ").append(snapshot.dimension()).append("\n")
                .append("Aktive Module: ").append(snapshot.enabledModuleCount());
        if (!snapshot.enabledModules().isEmpty()) {
            out.append(" · ");
            for (int i = 0; i < snapshot.enabledModules().size(); i++) {
                if (i > 0) out.append(", ");
                ModuleInfo module = snapshot.enabledModules().get(i);
                out.append(module.name()).append(" [").append(module.category()).append("]");
            }
            if (snapshot.enabledModuleCount() > snapshot.enabledModules().size()) {
                out.append(" · weitere ").append(snapshot.enabledModuleCount() - snapshot.enabledModules().size());
            }
        }
        out.append("\nController: Spieleraktion=").append(state(snapshot.playerControllerBusy()))
                .append(", Navigation=").append(state(snapshot.navigationActive()))
                .append(", Builder=").append(state(snapshot.builderActive()))
                .append(", Combat=").append(state(snapshot.combatActive()))
                .append("\nAgent: Vorschau wartet=").append(state(snapshot.previewPending()))
                .append(", Ergebnisprüfung offen=").append(state(snapshot.verificationPending()))
                .append(", manuelle Prüfung=").append(state(snapshot.manualVerificationPending()))
                .append("\nModulfehler in dieser Sitzung: ").append(Math.max(0, snapshot.moduleErrorCount()));

        if (snapshot.activeGoals().isEmpty()) {
            out.append("\nAktive Agent-Aufträge: keine");
        } else {
            out.append("\nAktive Agent-Aufträge:");
            for (GoalInfo goal : snapshot.activeGoals()) {
                out.append("\n- ").append(goal.id()).append(" · ").append(goal.status())
                        .append(" · ").append(goal.description());
                for (StepInfo step : goal.steps()) {
                    out.append("\n  Schritt ").append(step.id()).append(": ")
                            .append(step.action()).append(" · ").append(step.status())
                            .append(" · Versuche ").append(step.attempts());
                }
            }
        }
        out.append("\nGrenze: Modulstatus und Aufträge stammen aus dem lokalen Nightfall-Client. "
                + "Ein aktives Modul beweist nicht, dass seine Funktion in jedem Serverzustand wirkt. "
                + "Dieser Snapshot enthält keine Serveradresse, Zugangsdaten, Chatverläufe oder Aktionsparameter.");
        return out.length() > 4_000 ? out.substring(0, 3_997) + "..." : out.toString();
    }

    private static String state(boolean value) {
        return value ? "JA" : "nein";
    }

    private static String safe(String value, int maxChars) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }
}

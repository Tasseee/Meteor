package dev.nightfall.gui;

import dev.nightfall.ai.AIChatClient;
import dev.nightfall.ai.AIConfig;
import dev.nightfall.ai.AIActionRegistry;
import dev.nightfall.ai.AIBuilder;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Nightfall AI chat screen with explicit confirmation for the first world action. */
public final class AIChatScreen extends Screen {
    private static AIChatScreen active;
    private static final Pattern LOCATE_COORDINATES =
            Pattern.compile("(?s).*\\[\\s*-?\\d+\\s*,\\s*[^,]+\\s*,\\s*-?\\d+\\s*\\].*");

    private record ChatLine(String role, String content) {}

    private final List<ChatLine> history = new ArrayList<>();
    private String input = "";
    private String status = "Bereit · KI-Anbieter konfigurieren";
    private boolean waiting;
    private float scroll = Float.MAX_VALUE;
    private int panelX, panelY, panelW, panelH;
    private int sendX, sendY, sendW, sendH;
    private AIActionRegistry.Action pendingAction;
    private AIChatClient.ToolCall pendingToolCall;
    private final List<AIChatClient.Message> apiHistory = new ArrayList<>();
    private int actionRoundTrips;
    private int confirmX, confirmY, confirmW, confirmH;
    private int cancelX, cancelY, cancelW;
    private boolean waitingLocate;
    private String locatingBiome = "";
    private long locateDeadline;
    private boolean waitingCommandResponse;
    private String trackedCommand = "";
    private long commandDeadline;
    private boolean pendingCommandFeedbackAI;

    public AIChatScreen() {
        super(Text.literal("Nightfall AI"));
        active = this;
        history.add(new ChatLine("assistant",
                "Hey! Ich bin Nightfall AI. Frag mich alles rund um Minecraft – oder bitte mich, ein Biom zu suchen. Weltaktionen frage ich immer vorher bei dir an."));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        if (active == this) active = null;
    }

    public static void onGameMessage(String message) {
        if (active != null) {
            active.handleLocateMessage(message);
            active.handleServerCommandMessage(message);
        }
    }

    public static void onDisconnect() {
        AIBuilder.onDisconnect();
        if (active != null && active.waitingLocate) {
            active.waitingLocate = false;
            active.status = "Verbindung getrennt";
            active.history.add(new ChatLine("assistant",
                    "Die Suche wurde unterbrochen, weil die Serververbindung getrennt wurde."));
            active.scroll = Float.MAX_VALUE;
        }
        if (active != null && active.waitingCommandResponse) {
            active.waitingCommandResponse = false;
            String feedback = "Verbindung getrennt, bevor eine Servermeldung zu /" + active.trackedCommand
                    + " erkannt wurde. Der Befehl wurde möglicherweise gesendet, seine Ausführung ist nicht bestätigt.";
            active.history.add(new ChatLine("assistant", feedback));
            active.apiHistory.add(new AIChatClient.Message("user", feedback));
            active.scroll = Float.MAX_VALUE;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        expireLocateRequest();
        expireCommandRequest();
        if (AIBuilder.isRunning()) status = AIBuilder.statusLabel();
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.fill(0, 0, width, height, 0xA9000000);

        panelW = Math.min(570, Math.max(260, width - 24));
        panelH = Math.min(430, Math.max(230, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        ctx.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH,
                0xF01B1E29, 0xF00D1018);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 2, Theme.ACCENT);
        ctx.fill(panelX, panelY + 2, panelX + 1, panelY + panelH - 1, 0x8835E0D0);
        ctx.fill(panelX + panelW - 1, panelY + 2, panelX + panelW, panelY + panelH - 1, 0x5535E0D0);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0x5535E0D0);

        ctx.drawText(mc.textRenderer, "NIGHTFALL", panelX + 16, panelY + 12, Theme.TEXT, true);
        int titleX = panelX + 105;
        ctx.drawText(mc.textRenderer, "AI COMPANION", titleX, panelY + 13, Theme.ACCENT, true);
        ctx.drawText(mc.textRenderer, "PHASE 2 · ACTIONS", panelX + panelW - 125,
                panelY + 14, Theme.TEXT_DIM, false);

        AIConfig.Settings settings = AIConfig.current();
        String contextState = settings.shareWorldContext()
                ? "Weltkontext an: Position, Dimension, Hand"
                : "Weltkontext aus";
        ctx.drawText(mc.textRenderer, contextState, panelX + 16, panelY + 34,
                settings.shareWorldContext() ? Theme.ACCENT : Theme.TEXT_DIM, false);
        ctx.drawText(mc.textRenderer, status, panelX + 16, panelY + 47,
                (waiting || waitingLocate) ? Theme.ACCENT : Theme.TEXT_DIM, false);

        int contentTop = panelY + 65;
        int contentBottom = panelY + panelH - (pendingAction == null ? 53 : 80);
        int contentHeight = Math.max(30, contentBottom - contentTop);
        int left = panelX + 14;
        int right = panelX + panelW - 14;
        int textWidth = Math.max(60, panelW - 58);

        int totalHeight = 0;
        List<List<String>> wrapped = new ArrayList<>(history.size());
        for (ChatLine line : history) {
            List<String> lines = wrap(line.content(), textWidth);
            wrapped.add(lines);
            totalHeight += 20 + lines.size() * 10;
        }
        int maxScroll = Math.max(0, totalHeight - contentHeight);
        if (scroll == Float.MAX_VALUE) scroll = maxScroll;
        else scroll = Math.max(0, Math.min(maxScroll, scroll));

        ctx.enableScissor(left, contentTop, right, contentBottom);
        int y = (int) (contentTop - scroll);
        for (int i = 0; i < history.size(); i++) {
            ChatLine line = history.get(i);
            List<String> lines = wrapped.get(i);
            int bubbleHeight = 17 + lines.size() * 10;
            int bubbleColor = "user".equals(line.role()) ? 0x5535E0D0 : 0x552C3150;
            ctx.fill(left, y, right, y + bubbleHeight, bubbleColor);
            String label = "user".equals(line.role()) ? "DU" : "NIGHTFALL AI";
            int labelColor = "user".equals(line.role()) ? Theme.ACCENT : 0xFFB9A6FF;
            ctx.drawText(mc.textRenderer, label, left + 8, y + 5, labelColor, true);
            int textY = y + 16;
            for (String row : lines) {
                ctx.drawText(mc.textRenderer, row, left + 8, textY, Theme.TEXT, false);
                textY += 10;
            }
            y += bubbleHeight + 4;
        }
        ctx.disableScissor();

        if (pendingAction != null) {
            confirmY = panelY + panelH - 65;
            confirmH = 20;
            confirmX = panelX + 14;
            confirmW = (panelW - 36) / 2;
            cancelX = confirmX + confirmW + 8;
            cancelY = confirmY;
            cancelW = confirmW;

            ctx.fill(confirmX, confirmY, confirmX + confirmW, confirmY + confirmH, Theme.ACCENT);
            String confirmLabel = "AKTION AUSFÜHREN";
            ctx.drawText(mc.textRenderer, confirmLabel,
                    confirmX + (confirmW - mc.textRenderer.getWidth(confirmLabel)) / 2,
                    confirmY + 6, 0xFF071014, true);
            ctx.fill(cancelX, cancelY, cancelX + cancelW, cancelY + confirmH, 0xFF343848);
            String cancelLabel = "ABBRECHEN";
            ctx.drawText(mc.textRenderer, cancelLabel,
                    cancelX + (cancelW - mc.textRenderer.getWidth(cancelLabel)) / 2,
                    cancelY + 6, Theme.TEXT, true);
        }

        sendY = panelY + panelH - 38;
        sendH = 23;
        sendW = 62;
        sendX = panelX + panelW - 77;
        int inputX = panelX + 14;
        int inputW = sendX - inputX - 8;
        ctx.fill(inputX, sendY, inputX + inputW, sendY + sendH, 0xE5080B12);
        ctx.fill(inputX, sendY + sendH - 1, inputX + inputW, sendY + sendH, Theme.ACCENT_DIM);
        String shownInput = input + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        while (!shownInput.isEmpty() && mc.textRenderer.getWidth(shownInput) > inputW - 12) {
            shownInput = shownInput.substring(1);
        }
        ctx.drawText(mc.textRenderer, shownInput, inputX + 7, sendY + 7,
                input.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT, false);

        int sendColor = waiting || input.isBlank() ? 0xFF3B3E4A : Theme.ACCENT;
        ctx.fill(sendX, sendY, sendX + sendW, sendY + sendH, sendColor);
        String sendLabel = waiting ? "..." : "SEND";
        int sendLabelX = sendX + (sendW - mc.textRenderer.getWidth(sendLabel)) / 2;
        ctx.drawText(mc.textRenderer, sendLabel, sendLabelX, sendY + 7,
                waiting || input.isBlank() ? Theme.TEXT_DIM : 0xFF081014, true);

        ctx.drawText(mc.textRenderer, "ENTER: senden  ·  ESC: schließen  ·  API: config/nightfall/ai.json",
                panelX + 16, panelY + panelH - 10, Theme.TEXT_DIM, false);
    }

    private List<String> wrap(String value, int maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (textRenderer.getWidth(candidate) <= maxWidth) {
                    if (!line.isEmpty()) line.append(' ');
                    line.append(word);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                StringBuilder chunk = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    String next = chunk.toString() + word.charAt(i);
                    if (!chunk.isEmpty() && textRenderer.getWidth(next) > maxWidth) {
                        lines.add(chunk.toString());
                        chunk.setLength(0);
                    }
                    chunk.append(word.charAt(i));
                }
                line.append(chunk);
            }
            if (!line.isEmpty()) lines.add(line.toString());
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        String typed = charInput.asString();
        if (charInput.isValidChar() && input.length() + typed.length() <= 360) {
            input += typed;
            return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && pendingAction != null
                && click.x() >= confirmX && click.x() <= confirmX + confirmW
                && click.y() >= confirmY && click.y() <= confirmY + confirmH) {
            confirmPendingAction();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && pendingAction != null
                && click.x() >= cancelX && click.x() <= cancelX + cancelW
                && click.y() >= cancelY && click.y() <= cancelY + confirmH) {
            cancelPendingAction();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && click.x() >= sendX && click.x() <= sendX + sendW
                && click.y() >= sendY && click.y() <= sendY + sendH) {
            sendMessage();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, scroll - (float) verticalAmount * 32.0f);
        return true;
    }

    private void sendMessage() {
        if (waiting || pendingAction != null || input.isBlank()) return;
        String prompt = input.trim();
        input = "";
        history.add(new ChatLine("user", prompt));
        apiHistory.add(new AIChatClient.Message("user", prompt));
        actionRoundTrips = 0;
        scroll = Float.MAX_VALUE;
        requestAI();
    }

    private void requestAI() {
        if (waiting || apiHistory.isEmpty()) return;
        if (actionRoundTrips > 4) {
            history.add(new ChatLine("assistant",
                    "Ich habe mehrere Aktionen nacheinander ausgeführt. Bitte gib mir den nächsten Auftrag separat."));
            status = "Bitte nächste Aufgabe eingeben";
            scroll = Float.MAX_VALUE;
            return;
        }

        int responseIndex = history.size();
        history.add(new ChatLine("assistant", "Nightfall AI denkt …"));
        waiting = true;
        status = "Verbinde mit dem KI-Anbieter …";
        scroll = Float.MAX_VALUE;

        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        String context = settings.shareWorldContext() ? worldContext() : "";
        AIChatClient.request(new ArrayList<>(apiHistory), context, result -> {
            waiting = false;
            if (pendingCommandFeedbackAI) {
                pendingCommandFeedbackAI = false;
                actionRoundTrips = 0;
                history.set(responseIndex, new ChatLine("assistant", "Die Servermeldung ist eingegangen. Ich ordne sie ein …"));
                scroll = Float.MAX_VALUE;
                requestAI();
                return;
            }
            if (!result.success()) {
                history.set(responseIndex, new ChatLine("assistant", result.text()));
                status = "Verbindung fehlgeschlagen";
                scroll = Float.MAX_VALUE;
                return;
            }

            if (result.toolCall() != null) {
                AIChatClient.ToolCall call = result.toolCall();
                apiHistory.add(AIChatClient.Message.assistantToolCall(result.rawAssistantMessage()));
                try {
                    pendingAction = AIActionRegistry.prepare(call.name(), call.arguments());
                    pendingToolCall = call;
                    history.set(responseIndex, new ChatLine("assistant",
                            "Ich möchte folgende Aktion ausführen:\n" + pendingAction.preview()));
                    status = "Aktion wartet auf deine Bestätigung";
                    scroll = Float.MAX_VALUE;
                } catch (IllegalArgumentException invalidAction) {
                    apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(),
                            "Nightfall hat die Aktion aus Sicherheitsgründen abgelehnt: " + invalidAction.getMessage()));
                    history.set(responseIndex, new ChatLine("assistant",
                            "Diese Aktion kann ich nicht ausführen: " + invalidAction.getMessage()));
                    actionRoundTrips++;
                    scroll = Float.MAX_VALUE;
                    requestAI();
                }
                return;
            }

            history.set(responseIndex, new ChatLine("assistant", result.text()));
            apiHistory.add(new AIChatClient.Message("assistant", result.text()));
            status = "Antwort erhalten";
            scroll = Float.MAX_VALUE;
        });
    }

    private void confirmPendingAction() {
        if (pendingAction == null || pendingToolCall == null) return;
        AIActionRegistry.Action action = pendingAction;
        AIChatClient.ToolCall call = pendingToolCall;
        pendingAction = null;
        pendingToolCall = null;

        String result;
        try {
            result = AIActionRegistry.execute(action);
        } catch (RuntimeException | Error failure) {
            result = "Aktion ist fehlgeschlagen: " + failure.getClass().getSimpleName();
        }
        if ("locate_biome".equals(action.name())) {
            if (result.contains("wurde gesendet")) {
                locatingBiome = action.arguments().get("biome").getAsString();
                waitingLocate = true;
                locateDeadline = System.currentTimeMillis() + 30_000L;
            } else {
                waitingLocate = false;
            }
        }
        if ("run_server_command".equals(action.name()) && result.contains("wurde an den Server gesendet")) {
            trackedCommand = action.arguments().get("command").getAsString().replaceFirst("^/+", "").trim();
            waitingCommandResponse = true;
            commandDeadline = System.currentTimeMillis() + 30_000L;
        }
        history.add(new ChatLine("assistant", "Nightfall: " + result));
        apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(), result));
        actionRoundTrips++;
        status = "Aktion ausgeführt · KI antwortet";
        scroll = Float.MAX_VALUE;
        requestAI();
    }

    private void cancelPendingAction() {
        if (pendingAction == null || pendingToolCall == null) return;
        AIChatClient.ToolCall call = pendingToolCall;
        AIActionRegistry.cancel(pendingAction);
        pendingAction = null;
        pendingToolCall = null;
        String result = "Der Spieler hat die Aktion vor der Ausführung abgebrochen. Es wurde nichts verändert.";
        history.add(new ChatLine("assistant", "Alles klar. Ich habe nichts ausgeführt."));
        apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(), result));
        actionRoundTrips++;
        status = "Aktion abgebrochen";
        scroll = Float.MAX_VALUE;
        requestAI();
    }

    private void handleLocateMessage(String message) {
        if (!waitingLocate || message == null || message.isBlank()) return;
        if (System.currentTimeMillis() > locateDeadline) {
            expireLocateRequest();
            return;
        }

        String lower = message.toLowerCase(Locale.ROOT);
        String biomeName = locatingBiome.substring("minecraft:".length());
        boolean hasCoordinates = LOCATE_COORDINATES.matcher(message).matches();
        boolean mentionsBiome = lower.contains(biomeName)
                || lower.contains("jungle") && biomeName.equals("jungle")
                || lower.contains("dschungel") && biomeName.equals("jungle")
                || lower.contains("wüste") && biomeName.equals("desert")
                || lower.contains("desert") && biomeName.equals("desert");
        boolean rejected = lower.contains("unknown command") || lower.contains("unbekannter befehl")
                || lower.contains("unvollständiger befehl") || lower.contains("permission")
                || lower.contains("berechtigung") || lower.contains("nicht erlaubt")
                || lower.contains("not allowed") || lower.contains("you do not have");
        if (!hasCoordinates && !mentionsBiome && !rejected) return;

        waitingLocate = false;
        status = rejected ? "Server hat den Befehl abgelehnt" : "Antwort vom Server erhalten";
        history.add(new ChatLine("assistant", "Server-Antwort: " + message));
        scroll = Float.MAX_VALUE;
    }

    private void handleServerCommandMessage(String message) {
        if (!waitingCommandResponse || message == null || message.isBlank()) return;
        if (System.currentTimeMillis() > commandDeadline) {
            expireCommandRequest();
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String root = trackedCommand.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int namespaceSeparator = root.lastIndexOf(':');
        if (namespaceSeparator >= 0) root = root.substring(namespaceSeparator + 1);
        boolean rejected = lower.contains("unknown command") || lower.contains("unbekannter befehl")
                || lower.contains("unvollständiger befehl") || lower.contains("permission")
                || lower.contains("berechtigung") || lower.contains("nicht erlaubt")
                || lower.contains("not allowed") || lower.contains("you do not have")
                || lower.contains("no permission") || lower.contains("keine rechte");
        boolean movementCommand = Set.of("rtp", "tp", "teleport", "warp", "home", "spawn").contains(root);
        boolean relevant = rejected || lower.matches("(?s).*\\\\b" + java.util.regex.Pattern.quote(root) + "\\\\b.*")
                || (movementCommand && (lower.contains("teleportiert") || lower.contains("teleported")
                || lower.contains("warped"))) || lower.contains("unknown argument")
                || lower.contains("ungültiges argument");
        if (!relevant) return;

        waitingCommandResponse = false;
        String command = trackedCommand;
        String feedback = "Serverantwort auf den bereits gesendeten Befehl /" + command + ": " + message
                + (rejected
                ? "\nDer Server scheint den Befehl abzulehnen. Erkläre dem Spieler die Meldung."
                : "\nDiese Meldung ist nur ein möglicher Hinweis, kein unabhängiger Beweis für den Effekt. Erkläre genau, was sie aussagt.");
        feedback += "\nDies ist nur eine Rückmeldung zu einem bereits gesendeten Befehl. Sende ihn nicht erneut und führe keine neue Aktion aus; interpretiere ausschließlich diese Servermeldung.";
        status = rejected ? "Server hat den Befehl abgelehnt" : "Servermeldung empfangen · KI wertet aus";
        history.add(new ChatLine("assistant", "Servermeldung nach /" + command + ": " + message
                + (rejected ? "" : "\nHinweis: Die Meldung wurde zugeordnet, Nightfall kann den serverseitigen Effekt aber nicht unabhängig prüfen.")));
        apiHistory.add(new AIChatClient.Message("user", feedback));
        scroll = Float.MAX_VALUE;
        if (waiting) pendingCommandFeedbackAI = true;
        else {
            actionRoundTrips = 0;
            requestAI();
        }
    }

    private void expireCommandRequest() {
        if (!waitingCommandResponse || System.currentTimeMillis() <= commandDeadline) return;
        waitingCommandResponse = false;
        String command = trackedCommand;
        String feedback = "Für /" + command + " wurde innerhalb von 30 Sekunden keine eindeutig zuordenbare Serverantwort erkannt. Der Befehl wurde gesendet, seine Ausführung ist nicht bestätigt. Erkläre dem Spieler das knapp und sende den Befehl nicht erneut.";
        status = "Befehl gesendet · keine eindeutige Antwort erkannt";
        history.add(new ChatLine("assistant", feedback));
        apiHistory.add(new AIChatClient.Message("user", feedback));
        scroll = Float.MAX_VALUE;
        if (waiting) pendingCommandFeedbackAI = true;
        else {
            actionRoundTrips = 0;
            requestAI();
        }
    }

    private void expireLocateRequest() {
        if (!waitingLocate || System.currentTimeMillis() <= locateDeadline) return;
        waitingLocate = false;
        status = "Keine Serverantwort erhalten";
        history.add(new ChatLine("assistant",
                "Ich habe innerhalb von 30 Sekunden keine passende Serverantwort erhalten. Vielleicht unterstützt der Server /locate nicht oder blendet die Rückmeldung aus."));
        scroll = Float.MAX_VALUE;
    }

    private String worldContext() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return "";
        BlockPos pos = mc.player.getBlockPos();
        String dimension = mc.world.getRegistryKey().getValue().toString();
        String hand = mc.player.getMainHandStack().isEmpty()
                ? "leer" : mc.player.getMainHandStack().getName().getString()
                + " x" + mc.player.getMainHandStack().getCount();
        return "Dimension: " + dimension + "\n"
                + "Spielerposition: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ() + "\n"
                + "Haupthand: " + hand;
    }
}

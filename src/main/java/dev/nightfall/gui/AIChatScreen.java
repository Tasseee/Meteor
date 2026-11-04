package dev.nightfall.gui;

import dev.nightfall.ai.AIChatClient;
import dev.nightfall.ai.AIConfig;
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
import java.util.stream.Collectors;

/** Nightfall AI chat screen. Phase 1 is intentionally chat-only. */
public final class AIChatScreen extends Screen {
    private record ChatLine(String role, String content) {}

    private final List<ChatLine> history = new ArrayList<>();
    private String input = "";
    private String status = "Bereit · KI-Anbieter konfigurieren";
    private boolean waiting;
    private float scroll = Float.MAX_VALUE;
    private int panelX, panelY, panelW, panelH;
    private int sendX, sendY, sendW, sendH;

    public AIChatScreen() {
        super(Text.literal("Nightfall AI"));
        history.add(new ChatLine("assistant",
                "Hey! Ich bin Nightfall AI. Frag mich alles rund um Minecraft. In dieser ersten Phase kann ich beraten, aber noch keine Aktionen in deiner Welt ausführen."));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
        ctx.drawText(mc.textRenderer, "PHASE 1 · CHAT", panelX + panelW - 112,
                panelY + 14, Theme.TEXT_DIM, false);

        AIConfig.Settings settings = AIConfig.current();
        String contextState = settings.shareWorldContext()
                ? "Weltkontext an: Position, Dimension, Hand"
                : "Weltkontext aus";
        ctx.drawText(mc.textRenderer, contextState, panelX + 16, panelY + 34,
                settings.shareWorldContext() ? Theme.ACCENT : Theme.TEXT_DIM, false);
        ctx.drawText(mc.textRenderer, status, panelX + 16, panelY + 47,
                waiting ? Theme.ACCENT : Theme.TEXT_DIM, false);

        int contentTop = panelY + 65;
        int contentBottom = panelY + panelH - 53;
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
        StringBuilder line = new StringBuilder();
        for (String word : value.replace('\n', ' ').split("\\s+")) {
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
        if (waiting || input.isBlank()) return;
        String prompt = input.trim();
        input = "";
        history.add(new ChatLine("user", prompt));

        List<AIChatClient.Message> requestHistory = history.stream()
                .map(line -> new AIChatClient.Message(line.role(), line.content()))
                .collect(Collectors.toList());
        while (requestHistory.size() > 20) requestHistory.remove(0);

        if (history.size() > 20) history.remove(0);
        int responseIndex = history.size();
        history.add(new ChatLine("assistant", "Antwort wird geladen …"));
        waiting = true;
        status = "Verbinde mit dem KI-Anbieter …";
        scroll = Float.MAX_VALUE;

        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        String context = settings.shareWorldContext() ? worldContext() : "";
        AIChatClient.request(requestHistory, context, result -> {
            history.set(responseIndex, new ChatLine("assistant", result.text()));
            waiting = false;
            status = result.success() ? "Antwort erhalten" : "Verbindung fehlgeschlagen";
            scroll = Float.MAX_VALUE;
        });
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

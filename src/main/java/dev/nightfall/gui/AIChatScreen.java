package dev.nightfall.gui;

import dev.nightfall.ai.AIChatClient;
import dev.nightfall.ai.AIConfig;
import dev.nightfall.ai.AIVoiceClient;
import dev.nightfall.ai.AIRealtimeVoiceClient;
import dev.nightfall.ai.AIActionRegistry;
import dev.nightfall.ai.AIBuilder;
import dev.nightfall.ai.AIPlayerController;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Nightfall AI chat screen with explicit confirmation for the first world action. */
public final class AIChatScreen extends Screen {
    private static AIChatScreen active;
    private static final int MAX_ACTION_ROUND_TRIPS = 10;
    private static final int MAX_VISIBLE_HISTORY_LINES = 80;
    private static final int MAX_VISIBLE_MESSAGE_CHARS = 6_000;
    private static final int MAX_USER_MESSAGE_CHARS = 8_000;
    private static final Pattern LOCATE_COORDINATES =
            Pattern.compile("(?s).*\\[\\s*(-?\\d+)\\s*,\\s*(~|-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\].*");

    private record ChatLine(String role, String content) {}

    private record EquipmentSnapshot(
            ItemStack stack,
            String category,
            String location,
            boolean equipped,
            EquipmentSlot armorSlot) {}


    private final List<ChatLine> history = new ArrayList<>();
    private final Map<String, List<String>> wrapCache = new HashMap<>();
    private int wrapCacheWidth = -1;
    private String input = "";
    private String status = "Bereit · KI-Anbieter konfigurieren";
    private boolean waiting;
    private float scroll = Float.MAX_VALUE;
    private int panelX, panelY, panelW, panelH;
    private int sendX, sendY, sendW, sendH;
    private int micX, micY, micW, micH;
    private int ttsX, ttsY, ttsW, ttsH;
    private int voiceX, voiceY, voiceW, voiceH;
    private int contextToggleX, contextToggleY, contextToggleW, contextToggleH;
    private final int[] suggestionX = new int[3];
    private final int[] suggestionW = new int[3];
    private int suggestionY;
    private record SuggestionSet(String[] labels, String[] prompts) {}
    private static final SuggestionSet OVERWORLD_SUGGESTIONS = new SuggestionSet(
            new String[]{"Kleines Haus", "Holz sammeln", "Inventar sort."},
            new String[]{
                    "Baue mir ein kleines Starterhaus, wenn die vorhandenen Baupläne und Materialien dafür passen. Zeig mir zuerst die Vorschau und frag vor dem Bauen nach meiner Bestätigung.",
                    "Sammle Holz bis ich genug für mein Ziel habe. Prüfe zuerst meinen Bestand und frag nach der gewünschten Menge.",
                    "Analysiere mein Inventar und schlage eine sichere Sortierung vor."
            });
    private static final SuggestionSet NETHER_SUGGESTIONS = new SuggestionSet(
            new String[]{"Nether-Route", "Debris suchen", "Inventar sort."},
            new String[]{
                    "Prüfe meine aktuelle Position im Nether und schlage eine sichere, erreichbare Route vor. Starte keine Bewegung ohne Vorschau und meine Bestätigung.",
                    "Prüfe zuerst, ob ich im Nether bin und eine geeignete Spitzhacke habe. Schätze ein, ob eine Ancient-Debris-Suche hier sinnvoll und sicher möglich ist, und frag nach Ziel und Bestätigung, bevor du etwas startest.",
                    "Analysiere mein Inventar und schlage eine sichere Sortierung vor."
            });
    private static final SuggestionSet END_SUGGESTIONS = new SuggestionSet(
            new String[]{"End erkunden", "Spielerstatus", "Inventar sort."},
            new String[]{
                    "Prüfe, welche Erkundungs- und Navigationsfunktionen in meiner aktuellen Situation verfügbar sind. Nenne Risiken und frage vor einer Bewegung nach Bestätigung.",
                    "Zeige mir Dimension, Koordinaten und aktuellen Status, soweit diese Daten lokal verfügbar sind.",
                    "Analysiere mein Inventar und schlage eine sichere Sortierung vor."
            });
    private SuggestionSet renderedSuggestions = OVERWORLD_SUGGESTIONS;
    private AIActionRegistry.Action pendingAction;
    private AIChatClient.ToolCall pendingToolCall;
    private boolean pendingRealtimeToolCall;
    private final List<AIChatClient.Message> apiHistory = new ArrayList<>();
    private int actionRoundTrips;
    private final Map<String, Integer> failedToolAttempts = new HashMap<>();
    private int confirmX, confirmY, confirmW, confirmH;
    private int cancelX, cancelY, cancelW;
    private boolean waitingLocate;
    private String locatingBiome = "";
    private String locatingStructure = "";
    private long locateDeadline;
    private boolean waitingCommandResponse;
    private String trackedCommand = "";
    private long commandDeadline;
    private boolean pendingCommandFeedbackAI;
    private int sessionGeneration;

    public AIChatScreen() {
        super(Text.literal("Nightfall AI"));
        AIConfig.load();
        active = this;
        history.add(new ChatLine("assistant",
                "Hey! Ich bin Nightfall. Der Offline-Agent ist bereit. Freie Sprache und Stimme sind noch nicht angeschlossen; nutze vorerst /nf agent status und /nf agent gather, craft, smelt, build, wood oder iron. Jede Weltaktion zeigt vorher eine Vorschau."));
    }

    private static SuggestionSet suggestionsForCurrentDimension(MinecraftClient mc) {
        if (mc == null || mc.world == null) return OVERWORLD_SUGGESTIONS;
        String dimension = mc.world.getRegistryKey().getValue().getPath();
        if ("the_nether".equals(dimension)) return NETHER_SUGGESTIONS;
        if ("the_end".equals(dimension)) return END_SUGGESTIONS;
        return OVERWORLD_SUGGESTIONS;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Reuse the in-memory chat session after the screen is closed. This also keeps
     * command-response tracking alive while the player is in another screen.
     */
    public static AIChatScreen getOrCreate() {
        return active == null ? new AIChatScreen() : active;
    }

    @Override
    public void removed() {
        // A realtime call is independent of this screen and stays active while the player explores.
        AIVoiceClient.stopRecording();
        AIVoiceClient.stopSpeaking();
    }

    /** Stops all local audio resources when Minecraft itself is shutting down. */
    public static void onClientStopping() {
        AIRealtimeVoiceClient.stop();
        AIVoiceClient.stopRecording();
        AIVoiceClient.stopSpeaking();
    }

    /** Toggle the persistent voice call. Can be invoked from F8 while the chat screen is closed. */
    public static void toggleVoiceCall() {
        AIChatScreen session = getOrCreate();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (AIRealtimeVoiceClient.isActive()) {
            AIRealtimeVoiceClient.stop();
            session.status = "Nightfall-Sprachanruf beendet.";
            return;
        }
        if (mc.player == null || mc.world == null) {
            session.status = "Starte den Sprachanruf in einer Minecraft-Welt.";
            return;
        }
        AIConfig.Settings settings = AIConfig.current();
        if (settings.apiKey().isBlank()) {
            session.status = "Stimme ist im Offline-Modus deaktiviert und wird später ergänzt.";
            return;
        }
        AIConfig.Settings updated = AIConfig.updateVoiceSettings(true, true);
        if (!updated.voiceInputEnabled() || !updated.voiceOutputEnabled()) {
            session.status = "Die Stimmeinstellungen konnten nicht gespeichert werden.";
            return;
        }
        AIRealtimeVoiceClient.start(new AIRealtimeVoiceClient.Listener() {
            @Override public void onStatus(String message) {
                if (active != null) active.status = message;
            }

            @Override public void onTranscript(String transcript) {
                AIChatScreen current = active == null ? new AIChatScreen() : active;
                current.handleRealtimeTranscript(transcript);
            }

            @Override public void onAssistantText(String text) {
                AIChatScreen current = active == null ? new AIChatScreen() : active;
                current.history.add(new ChatLine("assistant", text));
                current.status = "Nightfall hat geantwortet";
                current.scroll = Float.MAX_VALUE;
            }

            @Override public void onToolCall(AIChatClient.ToolCall call) {
                AIChatScreen current = active == null ? new AIChatScreen() : active;
                current.handleRealtimeToolCall(call);
            }
        });
    }

    /** Toggle microphone uplink while keeping the voice conversation connected. */
    public static void toggleVoiceMicrophone() {
        AIRealtimeVoiceClient.toggleMicrophone();
        if (active != null && AIRealtimeVoiceClient.isActive()) {
            active.status = AIRealtimeVoiceClient.isMicrophoneMuted()
                    ? "Mikrofon stumm · F9 aktiviert es wieder."
                    : "Mikrofon aktiv · F9 schaltet stumm.";
        }
    }

    /** Small persistent HUD chip makes call, microphone and speaking state visible. */
    public static void renderVoiceIndicator(DrawContext ctx) {
        if (!AIRealtimeVoiceClient.isActive()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int right = 248;
        ctx.fill(10, 10, right, 31, 0xD91A202B);
        ctx.fill(10, 10, 13, 31, Theme.ACCENT);
        String label = AIRealtimeVoiceClient.isMicrophoneMuted()
                ? "MIKRO STUMM · F9 AN"
                : AIRealtimeVoiceClient.isSpeaking() ? "NIGHTFALL SPRICHT · F9 STUMM"
                : AIRealtimeVoiceClient.isReady() ? "NIGHTFALL CALL · F9 STUMM · F8 STOP"
                : "NIGHTFALL VERBINDET …";
        ctx.drawText(mc.textRenderer, label, 20, 17,
                AIRealtimeVoiceClient.isMicrophoneMuted() ? 0xFFFFD27A
                        : AIRealtimeVoiceClient.isSpeaking() ? 0xFFFFD27A : Theme.ACCENT, true);
    }

    /** Called from the client tick so command/locate deadlines continue while this screen is hidden. */
    public static void clientTick() {
        if (active == null) return;
        active.expireLocateRequest();
        active.expireStructureRequest();
        active.expireCommandRequest();
    }

    public static void onGameMessage(String message) {
        if (active != null) {
            active.handleLocateMessage(message);
            active.handleStructureMessage(message);
            active.handleServerCommandMessage(message);
        }
    }

    public static void onDisconnect() {
        AIRealtimeVoiceClient.stop();
        AIVoiceClient.stopRecording();
        AIVoiceClient.stopSpeaking();
        AIBuilder.onDisconnect();
        if (active == null) return;

        AIChatScreen session = active;
        session.sessionGeneration++;
        session.waiting = false;
        session.pendingCommandFeedbackAI = false;
        session.pendingRealtimeToolCall = false;

        if (session.pendingAction != null) {
            AIActionRegistry.cancel(session.pendingAction);
            session.pendingAction = null;
            session.pendingToolCall = null;
            session.history.add(new ChatLine("assistant",
                    "Eine noch nicht bestätigte Aktion wurde beim Verlassen des Servers verworfen. Es wurde nichts ausgeführt."));
        } else if (session.pendingToolCall != null) {
            session.pendingToolCall = null;
        }

        if (session.waitingLocate) {
            session.history.add(new ChatLine("assistant",
                    "Die Biom-Suche wurde durch den Verbindungsabbruch unterbrochen."));
        }
        if (session.waitingCommandResponse) {
            session.history.add(new ChatLine("assistant",
                    "Keine eindeutige Serverantwort zu /" + session.trackedCommand
                            + " vor dem Verbindungsabbruch. Die Ausführung ist nicht bestätigt."));
        }

        session.waitingLocate = false;
        session.waitingCommandResponse = false;
        session.locatingBiome = "";
        session.locatingStructure = "";
        session.trackedCommand = "";
        session.apiHistory.clear();
        session.actionRoundTrips = 0;
        session.status = "Server getrennt · KI-Kontext zurückgesetzt";

        if (!session.history.isEmpty()) {
            int lastIndex = session.history.size() - 1;
            ChatLine last = session.history.get(lastIndex);
            if ("assistant".equals(last.role()) && last.content().equals("Nightfall AI denkt …"))
                session.history.set(lastIndex, new ChatLine("assistant",
                        "Die Anfrage wurde durch den Verbindungsabbruch unterbrochen."));
        }
        session.history.add(new ChatLine("assistant",
                "Dein Chatverlauf bleibt sichtbar. Beim nächsten Auftrag starte ich mit einem frischen KI-Kontext, damit keine Welt- oder Inventardaten vom vorherigen Server übernommen werden."));
        session.scroll = Float.MAX_VALUE;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (AIBuilder.isRunning()) status = AIBuilder.statusLabel();
        MinecraftClient mc = MinecraftClient.getInstance();
        trimVisibleHistory();
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

        // Compact animated hologram mark and a calmer, more personal chat header.
        drawHologram(ctx, panelX + 29, panelY + 15, waiting || AIRealtimeVoiceClient.isSpeaking());
        ctx.drawText(mc.textRenderer, "NIGHTFALL", panelX + 54, panelY + 10, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, "DEIN KI-COMPANION", panelX + 54, panelY + 23, Theme.ACCENT, false);
        String modeLabel = AIRealtimeVoiceClient.isActive() ? "VOICE LINK" : "HOLO INTERFACE";
        ctx.drawText(mc.textRenderer, modeLabel, panelX + panelW - mc.textRenderer.getWidth(modeLabel) - 16,
                panelY + 14, Theme.TEXT_DIM, false);

        AIConfig.Settings settings = AIConfig.current();
        String contextState = "Kontext:";
        ctx.drawText(mc.textRenderer, contextState, panelX + 16, panelY + 34,
                settings.shareWorldContext() ? Theme.ACCENT : Theme.TEXT_DIM, false);
        contextToggleW = 112;
        contextToggleH = 18;
        contextToggleX = panelX + panelW - contextToggleW - 14;
        contextToggleY = panelY + 30;
        int contextButtonColor = settings.shareWorldContext() ? 0xFF267B69 : 0xFF343848;
        ctx.fill(contextToggleX, contextToggleY, contextToggleX + contextToggleW,
                contextToggleY + contextToggleH, contextButtonColor);
        String contextToggleLabel = settings.shareWorldContext() ? "WELTKONTEXT: AN" : "WELTKONTEXT: AUS";
        int contextLabelX = contextToggleX + (contextToggleW - mc.textRenderer.getWidth(contextToggleLabel)) / 2;
        ctx.drawText(mc.textRenderer, contextToggleLabel, contextLabelX, contextToggleY + 5,
                Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, status, panelX + 16, panelY + 47,
                (waiting || waitingLocate) ? Theme.ACCENT : Theme.TEXT_DIM, false);

        // Suggestions adapt to the currently loaded dimension; they never execute on click.
        renderedSuggestions = suggestionsForCurrentDimension(mc);
        ctx.drawText(mc.textRenderer, "PROBIER MAL:", panelX + 16, panelY + 63, Theme.TEXT_DIM, true);
        suggestionY = panelY + 77;
        int suggestionGap = 5;
        int suggestionTotalW = panelW - 32;
        int suggestionEachW = (suggestionTotalW - suggestionGap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            suggestionX[i] = panelX + 16 + i * (suggestionEachW + suggestionGap);
            suggestionW[i] = i == 2 ? panelX + panelW - 16 - suggestionX[i] : suggestionEachW;
            ctx.fill(suggestionX[i], suggestionY, suggestionX[i] + suggestionW[i], suggestionY + 18,
                    0xFF293342);
            ctx.fill(suggestionX[i], suggestionY, suggestionX[i] + suggestionW[i], suggestionY + 1,
                    0x8835E0D0);
            String label = renderedSuggestions.labels()[i];
            while (mc.textRenderer.getWidth(label) > suggestionW[i] - 8 && label.length() > 4)
                label = label.substring(0, label.length() - 2) + "…";
            ctx.drawText(mc.textRenderer, label,
                    suggestionX[i] + (suggestionW[i] - mc.textRenderer.getWidth(label)) / 2,
                    suggestionY + 6, Theme.TEXT, false);
        }

        int contentTop = panelY + 101;
        int contentBottom = panelY + panelH - (pendingAction == null ? 53 : 80);
        int contentHeight = Math.max(30, contentBottom - contentTop);
        int left = panelX + 14;
        int right = panelX + panelW - 14;
        int textWidth = Math.max(60, panelW - 58);
        if (wrapCacheWidth != textWidth || wrapCache.size() > 160) {
            wrapCache.clear();
            wrapCacheWidth = textWidth;
        }

        int totalHeight = 0;
        List<List<String>> wrapped = new ArrayList<>(history.size());
        for (ChatLine line : history) {
            List<String> lines = wrapCache.computeIfAbsent(line.content(),
                    value -> List.copyOf(wrap(value, textWidth)));
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
        micW = 44;
        micH = 23;
        micX = sendX - micW - 6;
        micY = sendY;
        ttsW = 48;
        ttsH = 23;
        ttsX = micX - ttsW - 6;
        ttsY = sendY;
        voiceW = 52;
        voiceH = 23;
        voiceX = ttsX - voiceW - 6;
        voiceY = sendY;
        int inputX = panelX + 14;
        int inputW = voiceX - inputX - 8;
        ctx.fill(inputX, sendY, inputX + inputW, sendY + sendH, 0xE5080B12);
        ctx.fill(inputX, sendY + sendH - 1, inputX + inputW, sendY + sendH, Theme.ACCENT_DIM);
        boolean showCursor = (System.currentTimeMillis() / 500) % 2 == 0;
        String shownInput = input + (showCursor ? "_" : "");
        int maxInputTextWidth = Math.max(8, inputW - 12);
        if (mc.textRenderer.getWidth(shownInput) > maxInputTextWidth) {
            int low = 0;
            int high = input.length();
            while (low < high) {
                int middle = (low + high + 1) >>> 1;
                String candidate = (middle < input.length() ? "…" : "")
                        + input.substring(input.length() - middle) + (showCursor ? "_" : "");
                if (mc.textRenderer.getWidth(candidate) <= maxInputTextWidth) low = middle;
                else high = middle - 1;
            }
            shownInput = (low < input.length() ? "…" : "")
                    + input.substring(input.length() - low) + (showCursor ? "_" : "");
        }
        ctx.drawText(mc.textRenderer, shownInput, inputX + 7, sendY + 7,
                input.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT, false);

        int micColor = AIRealtimeVoiceClient.isActive() ? 0xFF267B69 : 0xFF343848;
        ctx.fill(micX, micY, micX + micW, micY + micH, micColor);
        String micLabel = AIRealtimeVoiceClient.isActive() ? "STOP" : "CALL";
        ctx.drawText(mc.textRenderer, micLabel,
                micX + (micW - mc.textRenderer.getWidth(micLabel)) / 2,
                micY + 7, Theme.TEXT, true);

        boolean ttsEnabled = AIConfig.current().voiceOutputEnabled();
        int ttsColor = ttsEnabled ? 0xFF267B69 : 0xFF343848;
        ctx.fill(ttsX, ttsY, ttsX + ttsW, ttsY + ttsH, ttsColor);
        String ttsLabel = ttsEnabled ? "TTS ON" : "TTS OFF";
        ctx.drawText(mc.textRenderer, ttsLabel,
                ttsX + (ttsW - mc.textRenderer.getWidth(ttsLabel)) / 2,
                ttsY + 7, Theme.TEXT, true);

        String voiceLabel = AIConfig.current().speechVoice().toUpperCase(Locale.ROOT);
        ctx.fill(voiceX, voiceY, voiceX + voiceW, voiceY + voiceH, 0xFF293342);
        ctx.fill(voiceX, voiceY, voiceX + voiceW, voiceY + 1, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, voiceLabel,
                voiceX + (voiceW - mc.textRenderer.getWidth(voiceLabel)) / 2,
                voiceY + 7, Theme.TEXT, true);

        int sendColor = waiting || input.isBlank() ? 0xFF3B3E4A : Theme.ACCENT;
        ctx.fill(sendX, sendY, sendX + sendW, sendY + sendH, sendColor);
        String sendLabel = waiting ? "..." : "SEND";
        int sendLabelX = sendX + (sendW - mc.textRenderer.getWidth(sendLabel)) / 2;
        ctx.drawText(mc.textRenderer, sendLabel, sendLabelX, sendY + 7,
                waiting || input.isBlank() ? Theme.TEXT_DIM : 0xFF081014, true);

        ctx.drawText(mc.textRenderer, "ENTER: senden · F8: Call an/aus · F9: Mikro stumm/an · TTS: Sprachausgabe · Stimmenfeld: wechseln · ESC: schließen",
                panelX + 16, panelY + panelH - 10, Theme.TEXT_DIM, false);
    }

    /** Small animated pixel-hologram used as Nightfall's visual identity. */
    private void drawHologram(DrawContext ctx, int centerX, int centerY, boolean active) {
        long tick = System.currentTimeMillis() / 180L;
        int pulse = active ? (int) (tick % 3L) : 0;
        int glow = active ? 0xFF8FFFF0 : Theme.ACCENT;
        // Floating diamond core, built from crisp pixels to fit Minecraft's visual language.
        int[] widths = {8, 14, 20, 24, 20, 14, 8};
        for (int row = 0; row < widths.length; row++) {
            int w = widths[row];
            int y = centerY - 10 + row * 3;
            ctx.fill(centerX - w / 2, y, centerX + w / 2, y + 2,
                    row == 3 ? 0xFFB8FFF5 : glow);
        }
        // Layered scan rings and a moving scan line create a subtle hologram effect.
        int ringWidth = 28 + pulse * 2;
        int ringY = centerY + 12 + (active ? pulse - 1 : 0);
        ctx.fill(centerX - ringWidth / 2, ringY, centerX + ringWidth / 2, ringY + 1, 0xAA35E0D0);
        ctx.fill(centerX - 10, ringY + 3, centerX + 10, ringY + 4, 0x7735E0D0);
        int scanX = centerX - 10 + (int) (tick % 21L);
        ctx.fill(scanX, centerY - 8, scanX + 1, centerY + 8, 0x887BFFF0);
        if (active) {
            ctx.fill(centerX - 17, centerY - 2, centerX - 14, centerY + 2, 0xCCB8FFF5);
            ctx.fill(centerX + 14, centerY - 2, centerX + 17, centerY + 2, 0xCCB8FFF5);
        }
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
        if (keyCode == GLFW.GLFW_KEY_F8) {
            toggleVoiceCall();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F9) {
            toggleVoiceMicrophone();
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
        if (charInput.isValidChar() && input.length() + typed.length() <= MAX_USER_MESSAGE_CHARS) {
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
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !waiting && pendingAction == null
                && click.y() >= suggestionY && click.y() <= suggestionY + 18) {
            for (int i = 0; i < suggestionX.length; i++) {
                if (click.x() >= suggestionX[i] && click.x() <= suggestionX[i] + suggestionW[i]) {
                    input = renderedSuggestions.prompts()[i];
                    status = "Vorschlag eingefügt · Du kannst ihn noch anpassen";
                    return true;
                }
            }
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && click.x() >= contextToggleX && click.x() <= contextToggleX + contextToggleW
                && click.y() >= contextToggleY && click.y() <= contextToggleY + contextToggleH) {
            toggleWorldContextSharing();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && click.x() >= voiceX && click.x() <= voiceX + voiceW
                && click.y() >= voiceY && click.y() <= voiceY + voiceH) {
            cycleSpeechVoice();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && click.x() >= ttsX && click.x() <= ttsX + ttsW
                && click.y() >= ttsY && click.y() <= ttsY + ttsH) {
            toggleVoiceOutput();
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && click.x() >= micX && click.x() <= micX + micW
                && click.y() >= micY && click.y() <= micY + micH) {
            toggleVoiceCall();
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

    private void handleRealtimeTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) return;
        history.add(new ChatLine("user", transcript));
        status = "Du: " + transcript;
        scroll = Float.MAX_VALUE;

        if (pendingAction != null && pendingRealtimeToolCall) {
            String answer = transcript.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[.!?,;:]+$", "").trim();
            if (Set.of("ja", "ja bitte", "ja mach", "mach das", "bestätigen", "bestätige",
                    "ausführen", "einverstanden", "yes", "confirm", "okay", "ok").contains(answer)) {
                confirmPendingAction();
            } else if (Set.of("nein", "nein danke", "abbrechen", "abbruch", "stopp", "stop",
                    "cancel", "no").contains(answer)) {
                cancelPendingAction();
            } else {
                AIRealtimeVoiceClient.speakPrompt("Ich warte noch auf deine Freigabe. Sage bitte klar Ja zum Bestätigen oder Nein zum Abbrechen.");
                status = "Aktion wartet auf ein klares Ja oder Nein";
            }
            return;
        }

        if (pendingAction != null || waiting) {
            AIRealtimeVoiceClient.speakPrompt("Ich bearbeite noch den letzten Auftrag. Bitte warte kurz oder bestätige die angezeigte Aktion.");
            return;
        }
        AIRealtimeVoiceClient.requestResponse();
    }

    private void handleRealtimeToolCall(AIChatClient.ToolCall call) {
        if (call == null) return;
        if (pendingAction != null) {
            AIRealtimeVoiceClient.sendFunctionOutput(call.id(),
                    "Nightfall führt noch eine bestätigungspflichtige Aktion. Warte, bis der Spieler Ja oder Nein gesagt hat.");
            return;
        }
        try {
            AIActionRegistry.Action prepared = AIActionRegistry.prepare(call.name(), call.arguments());
            if (!AIActionRegistry.requiresConfirmation(call.name())) {
                String result = AIActionRegistry.execute(prepared);
                history.add(new ChatLine("assistant", "Nightfall: " + result));
                status = "Nightfall hat eine sichere Abfrage ausgeführt";
                scroll = Float.MAX_VALUE;
                AIRealtimeVoiceClient.sendFunctionOutput(call.id(), result);
                return;
            }
            pendingAction = prepared;
            pendingToolCall = call;
            pendingRealtimeToolCall = true;
            history.add(new ChatLine("assistant",
                    "Ich möchte folgende Aktion ausführen. Bestätige mit Ja oder Nein:\n" + prepared.preview()));
            status = "Aktion wartet auf deine Sprach- oder Button-Bestätigung";
            scroll = Float.MAX_VALUE;
            AIRealtimeVoiceClient.speakPrompt("Ich möchte folgende Minecraft-Aktion ausführen. "
                    + prepared.preview() + ". Sage Ja zum Bestätigen oder Nein zum Abbrechen.");
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            history.add(new ChatLine("assistant", "Diese Aktion kann ich nicht ausführen: " + detail));
            status = "Aktion abgelehnt";
            scroll = Float.MAX_VALUE;
            AIRealtimeVoiceClient.sendFunctionOutput(call.id(),
                    "Nightfall hat die Aktion abgelehnt: " + detail);
        }
    }

    private void toggleWorldContextSharing() {
        AIConfig.Settings settings = AIConfig.current();
        boolean enabled = !settings.shareWorldContext();
        AIConfig.Settings updated = AIConfig.updateWorldContextSharing(enabled);
        if (updated.shareWorldContext() != enabled) {
            status = "Weltkontext-Einstellung konnte nicht gespeichert werden.";
            return;
        }
        status = enabled
                ? "Weltkontext aktiviert · Position, Dimension und Hand werden mit KI-Anfragen geteilt"
                : "Weltkontext deaktiviert · keine Weltinformationen werden automatisch angehängt";
        history.add(new ChatLine("assistant", status));
        scroll = Float.MAX_VALUE;
    }

    private void cycleSpeechVoice() {
        List<String> voices = AIConfig.supportedSpeechVoices();
        AIConfig.Settings current = AIConfig.current();
        int currentIndex = voices.indexOf(current.speechVoice());
        String nextVoice = voices.get((currentIndex + 1 + voices.size()) % voices.size());
        AIConfig.Settings updated = AIConfig.updateSpeechVoice(nextVoice);
        if (!nextVoice.equals(updated.speechVoice())) {
            status = "Die neue Stimme konnte nicht gespeichert werden.";
            return;
        }
        AIVoiceClient.stopSpeaking();
        AIRealtimeVoiceClient.updateVoice(updated);
        String displayName = nextVoice.substring(0, 1).toUpperCase(Locale.ROOT) + nextVoice.substring(1);
        status = "Nightfall-Stimme geändert: " + displayName;
        history.add(new ChatLine("assistant", status));
        scroll = Float.MAX_VALUE;
    }

    private void toggleVoiceOutput() {
        AIConfig.Settings settings = AIConfig.current();
        if (!settings.voiceOutputEnabled() && settings.apiKey().isBlank()) {
            status = "Für Sprachausgabe zuerst den API-Key in config/nightfall/ai.json eintragen.";
            return;
        }
        boolean enabled = !settings.voiceOutputEnabled();
        AIConfig.Settings updated = AIConfig.updateVoiceSettings(settings.voiceInputEnabled(), enabled);
        if (updated.voiceOutputEnabled() != enabled) {
            status = "TTS-Einstellung konnte nicht gespeichert werden.";
            return;
        }
        if (!enabled) AIVoiceClient.stopSpeaking();
        AIRealtimeVoiceClient.setSpeechEnabled(enabled);
        status = enabled ? "Sprachausgabe aktiviert · Nightfall spricht fertige Antworten."
                : "Sprachausgabe ausgeschaltet.";
    }

    private void trimVisibleHistory() {
        int excess = history.size() - MAX_VISIBLE_HISTORY_LINES;
        if (excess > 0) history.subList(0, excess).clear();

        for (int i = 0; i < history.size(); i++) {
            ChatLine line = history.get(i);
            if (line.content() != null && line.content().length() > MAX_VISIBLE_MESSAGE_CHARS) {
                String shortened = line.content().substring(0, MAX_VISIBLE_MESSAGE_CHARS)
                        + "\n… Nachricht für die Anzeige gekürzt …";
                history.set(i, new ChatLine(line.role(), shortened));
            }
        }
    }

    private void sendMessage() {
        if (waiting || pendingAction != null || input.isBlank()) return;
        String prompt = input.trim();
        input = "";
        boolean truncated = prompt.length() > MAX_USER_MESSAGE_CHARS;
        if (truncated) prompt = prompt.substring(0, MAX_USER_MESSAGE_CHARS);
        history.add(new ChatLine("user", prompt));
        if (truncated) history.add(new ChatLine("assistant",
                "Hinweis: Deine Nachricht wurde auf " + MAX_USER_MESSAGE_CHARS + " Zeichen begrenzt, damit die Anfrage schnell bleibt."));
        apiHistory.add(new AIChatClient.Message("user", prompt));
        actionRoundTrips = 0;
        failedToolAttempts.clear();
        scroll = Float.MAX_VALUE;
        if (AIRealtimeVoiceClient.isActive()) {
            AIRealtimeVoiceClient.sendText(prompt);
            return;
        }
        requestAI();
    }

    private void requestAI() {
        if (waiting || apiHistory.isEmpty()) return;
        AIConfig.Settings offlineSettings = AIConfig.current();
        if (offlineSettings.apiKey().isBlank() || offlineSettings.baseUrl().isBlank()) {
            apiHistory.clear();
            history.add(new ChatLine("assistant",
                    "Ich bin gerade im reinen Offline-Modus. Freie Sprache wird später ergänzt. "
                            + "Nutze /nf agent status oder einen Auftrag wie /nf agent gather wood minecraft:oak_log 16. "
                            + "Die Vorschau kannst du mit /nf agent confirm bestätigen; danach prüfst du das Ergebnis mit "
                            + "/nf agent verify success oder /nf agent verify failed."));
            waiting = false;
            status = "Offline-Agent bereit";
            scroll = Float.MAX_VALUE;
            return;
        }
        AIChatClient.compactHistoryInPlace(apiHistory);
        if (apiHistory.isEmpty()) return;
        if (actionRoundTrips >= MAX_ACTION_ROUND_TRIPS) {
            history.add(new ChatLine("assistant",
                    "Ich habe mehrere Aktionen nacheinander ausgeführt. Bitte gib mir den nächsten Auftrag separat."));
            status = "Bitte nächste Aufgabe eingeben";
            scroll = Float.MAX_VALUE;
            return;
        }

        int responseIndex = history.size();
        history.add(new ChatLine("assistant", "Nightfall AI denkt …"));
        waiting = true;
        status = "Nightfall AI denkt …";
        scroll = Float.MAX_VALUE;

        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        String context = settings.shareWorldContext() ? worldContext() : "";
        int requestGeneration = sessionGeneration;
        AIChatClient.request(new ArrayList<>(apiHistory), context, result -> {
            if (requestGeneration != sessionGeneration) return;
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
                    if ("run_server_command".equals(call.name()) && waitingCommandResponse)
                        throw new IllegalArgumentException("Ein Serverbefehl wartet noch auf eine Rückmeldung. Warte auf die Serverantwort oder den 30-Sekunden-Timeout, bevor du den nächsten Befehl sendest.");
                    String toolSignature = call.name() + "|" + call.arguments();
                    if (failedToolAttempts.getOrDefault(toolSignature, 0) >= 2) {
                        String blockedResult = "Dieser identische Werkzeugschritt ist in diesem Auftrag bereits zweimal fehlgeschlagen und wird nicht erneut ausgeführt. "
                                + "Erkläre die Blockade oder wähle einen anderen sicheren Weg.";
                        apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(), blockedResult));
                        history.set(responseIndex, new ChatLine("assistant", blockedResult));
                        actionRoundTrips++;
                        scroll = Float.MAX_VALUE;
                        requestAI();
                        return;
                    }
                    AIActionRegistry.Action prepared = AIActionRegistry.prepare(call.name(), call.arguments());
                    if (!AIActionRegistry.requiresConfirmation(call.name())) {
                        String toolResult = AIActionRegistry.execute(prepared);
                        apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(), toolResult));
                        history.set(responseIndex, new ChatLine("assistant", "Werkzeugergebnis wird ausgewertet …"));
                        actionRoundTrips++;
                        scroll = Float.MAX_VALUE;
                        requestAI();
                        return;
                    }
                    pendingAction = prepared;
                    pendingToolCall = call;
                    history.set(responseIndex, new ChatLine("assistant",
                            "Ich möchte folgende Aktion ausführen:\n" + pendingAction.preview()));
                    status = "Aktion wartet auf deine Bestätigung";
                    AIVoiceClient.speak("Ich möchte folgende Aktion ausführen. " + pendingAction.preview()
                            + ". Bitte bestätige sie im Nightfall-Fenster oder brich sie ab.");
                    scroll = Float.MAX_VALUE;
                } catch (RuntimeException actionFailure) {
                    String detail = actionFailure.getMessage();
                    if (detail == null || detail.isBlank()) detail = actionFailure.getClass().getSimpleName();
                    detail = detail.replaceAll("[\\r\\n\\t]+", " ").trim();
                    if (detail.length() > 320) detail = detail.substring(0, 320) + "…";
                    String signature = call.name() + "|" + call.arguments();
                    int failures = failedToolAttempts.merge(signature, 1, Integer::sum);
                    String failureResult = "Nightfall konnte das Werkzeug nicht ausführen: " + detail + ".";
                    if (failures >= 2) {
                        failureResult += " Derselbe Schritt ist bereits mehrfach fehlgeschlagen. Nicht erneut aufrufen. "
                                + "Erkläre die Blockade oder wähle einen wesentlich anderen sicheren Schritt.";
                    } else {
                        failureResult += " Prüfe den Zustand und wähle nur dann einen sicheren Alternativschritt.";
                    }
                    apiHistory.add(AIChatClient.Message.toolResult(call.id(), call.name(), failureResult));
                    history.set(responseIndex, new ChatLine("assistant", failureResult));
                    actionRoundTrips++;
                    scroll = Float.MAX_VALUE;
                    requestAI();
                }
                return;
            }

            history.set(responseIndex, new ChatLine("assistant", result.text()));
            apiHistory.add(new AIChatClient.Message("assistant", result.text()));
            status = "Antwort erhalten";
            if (MinecraftClient.getInstance().currentScreen == this) AIVoiceClient.speak(result.text());
            scroll = Float.MAX_VALUE;
        });
    }

    private void confirmPendingAction() {
        if (pendingAction == null || pendingToolCall == null) return;
        AIActionRegistry.Action action = pendingAction;
        AIChatClient.ToolCall call = pendingToolCall;
        pendingAction = null;
        pendingToolCall = null;
        boolean realtimeCall = pendingRealtimeToolCall;
        pendingRealtimeToolCall = false;

        String result;
        try {
            result = AIActionRegistry.execute(action);
        } catch (RuntimeException | Error failure) {
            String detail = failure.getMessage();
            if (detail != null) {
                detail = detail.trim();
                if (detail.length() > 320) detail = detail.substring(0, 320) + "…";
            }
            result = "Aktion ist fehlgeschlagen: " + failure.getClass().getSimpleName()
                    + (detail == null || detail.isEmpty() ? "." : " – " + detail);
        }
        if ("stop_movement".equals(action.name()) || "stop_current_action".equals(action.name())) {
            locatingStructure = "";
        }
        if ("locate_structure".equals(action.name())) {
            if (result.contains("wurde gesendet")) {
                locatingStructure = action.arguments().get("structure").getAsString();
                locateDeadline = System.currentTimeMillis() + 30_000L;
            } else locatingStructure = "";
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
        if (realtimeCall) {
            status = "Aktion ausgeführt · Nightfall antwortet per Stimme";
            scroll = Float.MAX_VALUE;
            AIRealtimeVoiceClient.sendFunctionOutput(call.id(), result);
            return;
        }
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
        boolean realtimeCall = pendingRealtimeToolCall;
        pendingRealtimeToolCall = false;
        String result = "Der Spieler hat die Aktion vor der Ausführung abgebrochen. Es wurde nichts verändert.";
        history.add(new ChatLine("assistant", "Alles klar. Ich habe nichts ausgeführt."));
        if (realtimeCall) {
            status = "Aktion abgebrochen · Nightfall hört weiter zu";
            scroll = Float.MAX_VALUE;
            AIRealtimeVoiceClient.sendFunctionOutput(call.id(), result);
            return;
        }
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

    private void handleStructureMessage(String message) {
        if (locatingStructure.isEmpty() || message == null || message.isBlank()) return;
        if (System.currentTimeMillis() > locateDeadline) {
            locatingStructure = "";
            return;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String structureName = locatingStructure.substring("minecraft:".length());
        boolean rejected = lower.contains("unknown command") || lower.contains("unbekannter befehl")
                || lower.contains("permission") || lower.contains("berechtigung")
                || lower.contains("not allowed") || lower.contains("nicht erlaubt")
                || lower.contains("could not find") || lower.contains("konnte keine");
        java.util.regex.Matcher coordinates = LOCATE_COORDINATES.matcher(message);
        if (rejected) {
            locatingStructure = "";
            history.add(new ChatLine("assistant", "Die Struktursuche wurde vom Server abgelehnt: " + message));
            status = "Struktursuche abgelehnt";
            scroll = Float.MAX_VALUE;
            return;
        }
        if (!coordinates.matches()) return;
        // The response must identify the requested structure, unless the server uses a generic
        // locate-result line with coordinates. The short request timeout limits unrelated matches.
        boolean namesTarget = lower.contains(structureName.replace('_', ' '))
                || lower.contains(structureName) || lower.contains("nearest") || lower.contains("nächste")
                || lower.contains("nächstgelegene") || lower.contains("position");
        if (!namesTarget) return;
        int x, y, z;
        try {
            x = Integer.parseInt(coordinates.group(1));
            y = "~".equals(coordinates.group(2)) ? MinecraftClient.getInstance().player.getBlockY()
                    : Integer.parseInt(coordinates.group(2));
            z = Integer.parseInt(coordinates.group(3));
        } catch (NumberFormatException invalid) {
            locatingStructure = "";
            history.add(new ChatLine("assistant", "Die Serverkoordinaten waren ungültig; ich starte keine Bewegung."));
            status = "Ungültige Koordinaten";
            return;
        }
        String found = locatingStructure;
        locatingStructure = "";
        try {
            String navigation = AIPlayerController.navigateTo(x, y, z);
            history.add(new ChatLine("assistant", "Strukturhinweis vom Server für " + found
                    + " bei X=" + x + ", Y=" + y + ", Z=" + z + ". Nightfall startet jetzt die sichere Wegsuche. " + navigation));
            status = "Weg zur Struktur wird gesucht";
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null ? "Kein sicherer Wegabschnitt verfügbar." : failure.getMessage();
            history.add(new ChatLine("assistant", "Strukturkoordinaten erhalten (" + x + ", " + y + ", " + z
                    + "), aber Nightfall konnte keinen sicheren Weg starten: " + detail
                    + " Gehe näher heran oder lade die Umgebung."));
            status = "Struktur gefunden · Weg nicht verfügbar";
        }
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
        boolean relevant = rejected || lower.matches("(?s).*\\b" + java.util.regex.Pattern.quote(root) + "\\b.*")
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

    private void expireStructureRequest() {
        if (locatingStructure.isEmpty() || System.currentTimeMillis() <= locateDeadline) return;
        String expired = locatingStructure;
        locatingStructure = "";
        status = "Keine Strukturantwort erhalten";
        history.add(new ChatLine("assistant", "Für " + expired
                + " kam innerhalb von 30 Sekunden keine passende Koordinatenantwort. Nightfall hat keine Bewegung gestartet."));
        scroll = Float.MAX_VALUE;
    }

    private void expireLocateRequest() {
        if (!waitingLocate || System.currentTimeMillis() <= locateDeadline) return;
        waitingLocate = false;
        status = "Keine Serverantwort erhalten";
        history.add(new ChatLine("assistant",
                "Ich habe innerhalb von 30 Sekunden keine passende Serverantwort erhalten. Vielleicht unterstützt der Server /locate nicht oder blendet die Rückmeldung aus."));
        scroll = Float.MAX_VALUE;
    }

    /**
     * Builds a bounded snapshot from data already available to the local client.
     * This method is called only when the player has enabled world-context sharing.
     */
    private String worldContext() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return "";

        var player = mc.player;
        BlockPos pos = player.getBlockPos();
        String dimension = mc.world.getRegistryKey().getValue().toString();
        String biome = mc.world.getBiome(pos).getKey()
                .map(key -> key.getValue().toString())
                .orElse("unbekannt");

        long dayTime = Math.floorMod(mc.world.getTimeOfDay(), 24_000L);
        String dayPhase;
        if (dayTime < 1_000L || dayTime >= 23_000L) {
            dayPhase = "Morgengrauen";
        } else if (dayTime < 11_000L) {
            dayPhase = "Tag";
        } else if (dayTime < 13_000L) {
            dayPhase = "Abenddämmerung";
        } else {
            dayPhase = "Nacht";
        }

        String weather = mc.world.isThundering()
                ? "Gewitter"
                : mc.world.isRaining() ? "Regen" : "klar";
        String blockBelow = mc.world.getBlockState(pos.down()).getBlock().getName().getString();
        int lightLevel = mc.world.getLightLevel(pos);

        String health = String.format(Locale.ROOT, "%.1f/%.1f", player.getHealth(), player.getMaxHealth());
        String hunger = player.getHungerManager().getFoodLevel() + "/20";
        String saturation = String.format(Locale.ROOT, "%.1f", player.getHungerManager().getSaturationLevel());

        List<String> conditions = new ArrayList<>();
        if (player.isOnGround()) conditions.add("am Boden");
        if (player.isSubmergedInWater()) conditions.add("unter Wasser");
        if (player.isInLava()) conditions.add("in Lava");
        if (player.isOnFire()) conditions.add("brennt");
        if (player.isSprinting()) conditions.add("sprintet");
        if (player.isSneaking()) conditions.add("schleicht");
        String movement = conditions.isEmpty() ? "keine besondere Bewegung erkannt" : String.join(", ", conditions);

        List<ItemStack> armorStacks = List.of(
                player.getEquippedStack(EquipmentSlot.HEAD),
                player.getEquippedStack(EquipmentSlot.CHEST),
                player.getEquippedStack(EquipmentSlot.LEGS),
                player.getEquippedStack(EquipmentSlot.FEET));
        List<String> armor = new ArrayList<>();
        int equippedArmorPieces = 0;
        int armorPiecesAtOrBelowTwentyPercent = 0;
        int lowestArmorDurabilityPercent = -1;
        boolean hasGoldArmorPiece = false;
        for (ItemStack stack : armorStacks) {
            if (stack.isEmpty()) continue;
            equippedArmorPieces++;
            armor.add(describeStack(stack));
            int durabilityPercent = remainingDurabilityPercent(stack);
            if (durabilityPercent >= 0) {
                if (durabilityPercent <= 20) armorPiecesAtOrBelowTwentyPercent++;
                if (lowestArmorDurabilityPercent < 0 || durabilityPercent < lowestArmorDurabilityPercent)
                    lowestArmorDurabilityPercent = durabilityPercent;
            }
            String armorItemId = Registries.ITEM.getId(stack.getItem()).toString();
            if (armorItemId.startsWith("minecraft:golden_")) hasGoldArmorPiece = true;
        }
        String armorSummary = armor.isEmpty() ? "keine" : String.join(", ", armor);

        List<MobEntity> nearbyMobs = mc.world.getEntitiesByClass(
                MobEntity.class,
                player.getBoundingBox().expand(16.0),
                MobEntity::isAlive);
        nearbyMobs.sort((left, right) -> Double.compare(
                left.squaredDistanceTo(player), right.squaredDistanceTo(player)));
        List<String> nearbyMobDescriptions = new ArrayList<>();
        for (int index = 0; index < Math.min(10, nearbyMobs.size()); index++) {
            MobEntity mob = nearbyMobs.get(index);
            double distance = Math.sqrt(mob.squaredDistanceTo(player));
            String threat = mob instanceof HostileEntity ? "feindlich" : "passiv/neutral";
            nearbyMobDescriptions.add(mob.getType().getName().getString()
                    + " (" + threat + ", " + String.format(Locale.ROOT, "%.1f", distance) + " Blöcke)");
        }
        String nearbyMobsSummary = nearbyMobDescriptions.isEmpty()
                ? "keine lebenden Mobs clientseitig im Umkreis von 16 Blöcken erkannt"
                : String.join(", ", nearbyMobDescriptions);

        List<ItemEntity> nearbyItemEntities = mc.world.getEntitiesByClass(
                ItemEntity.class,
                player.getBoundingBox().expand(12.0),
                ItemEntity::isAlive);
        nearbyItemEntities.sort((left, right) -> Double.compare(
                left.squaredDistanceTo(player), right.squaredDistanceTo(player)));
        Map<String, Integer> nearbyDropCounts = new HashMap<>();
        Map<String, Double> nearbyDropDistances = new HashMap<>();
        for (ItemEntity itemEntity : nearbyItemEntities) {
            ItemStack stack = itemEntity.getStack();
            if (stack.isEmpty()) continue;
            String itemName = stack.getName().getString();
            double distance = Math.sqrt(itemEntity.squaredDistanceTo(player));
            nearbyDropCounts.merge(itemName, stack.getCount(), Integer::sum);
            nearbyDropDistances.merge(itemName, distance, Math::min);
        }
        String nearbyDropsSummary = nearbyDropCounts.entrySet().stream()
                .sorted((left, right) -> {
                    int countOrder = Integer.compare(right.getValue(), left.getValue());
                    return countOrder != 0 ? countOrder : left.getKey().compareTo(right.getKey());
                })
                .limit(10)
                .map(entry -> entry.getKey() + " x" + entry.getValue()
                        + " (nächstes in " + String.format(Locale.ROOT, "%.1f",
                        nearbyDropDistances.getOrDefault(entry.getKey(), 0.0)) + " Blöcken)")
                .collect(Collectors.joining(", "));
        if (nearbyDropsSummary.isBlank()) {
            nearbyDropsSummary = "keine clientseitig erkannten Item-Entities im Umkreis von 12 Blöcken";
        }

        List<MobEntity> nearbyHostiles = nearbyMobs.stream()
                .filter(mob -> mob instanceof HostileEntity)
                .toList();
        double nearestHostileDistance = nearbyHostiles.stream()
                .mapToDouble(mob -> Math.sqrt(mob.squaredDistanceTo(player)))
                .min()
                .orElse(Double.POSITIVE_INFINITY);
        long hostileMobsWithinEightBlocks = nearbyHostiles.stream()
                .filter(mob -> mob.squaredDistanceTo(player) <= 64.0)
                .count();

        boolean nightTime = dayTime >= 13_000L || dayTime < 1_000L;
        AILocalRiskAssessment.Result localRisk = AILocalRiskAssessment.assess(
                player.isInLava(),
                player.isOnFire(),
                nearestHostileDistance,
                player.getHealth(),
                player.getMaxHealth(),
                player.getHungerManager().getFoodLevel(),
                player.isSubmergedInWater(),
                nightTime,
                lightLevel,
                hostileMobsWithinEightBlocks);
        String localRiskSummary = localRisk.summary();

        String targetedBlock = "kein Block direkt anvisiert";
        String targetedBlockMining = "keine Werkzeuganalyse verfügbar";
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            BlockPos targetedPos = hit.getBlockPos();
            targetedBlock = mc.world.getBlockState(targetedPos).getBlock().getName().getString()
                    + " bei X=" + targetedPos.getX()
                    + ", Y=" + targetedPos.getY()
                    + ", Z=" + targetedPos.getZ();
            targetedBlockMining = AIPlayerController.describeMiningTool(targetedPos);
        }

        Map<String, Integer> inventoryCounts = new HashMap<>();
        int foodUnits = 0;
        int torchUnits = 0;
        boolean hasShield = "minecraft:shield".equals(
                Registries.ITEM.getId(player.getOffHandStack().getItem()).toString());
        boolean hasWaterBucket = false;
        boolean hasEnderPearls = false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            inventoryCounts.merge(stack.getName().getString(), stack.getCount(), Integer::sum);
            if (stack.contains(DataComponentTypes.FOOD)) foodUnits += stack.getCount();
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            if ("minecraft:torch".equals(itemId) || "minecraft:soul_torch".equals(itemId))
                torchUnits += stack.getCount();
            if ("minecraft:shield".equals(itemId)) hasShield = true;
            if ("minecraft:water_bucket".equals(itemId)) hasWaterBucket = true;
            if ("minecraft:ender_pearl".equals(itemId)) hasEnderPearls = true;
        }
        int mainHandDurabilityPercent = remainingDurabilityPercent(player.getMainHandStack());
        String readinessSummary = AILocalSurvivalReadiness.assess(
                new AILocalSurvivalReadiness.Snapshot(
                        dimension,
                        equippedArmorPieces,
                        armorPiecesAtOrBelowTwentyPercent,
                        lowestArmorDurabilityPercent,
                        mainHandDurabilityPercent,
                        foodUnits,
                        torchUnits,
                        hasShield,
                        hasGoldArmorPiece,
                        hasWaterBucket,
                        hasEnderPearls,
                        lightLevel))
                .summary();
        String equipmentAdvisorSummary = equipmentAdvisorSummary(player);
        String inventorySummary = inventoryCounts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(12)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining(", "));
        if (inventorySummary.isBlank()) inventorySummary = "leer";

        return "Lokaler Minecraft-Weltkontext (Momentaufnahme):\n"
                + "Dimension: " + dimension + "\n"
                + "Biome am Spieler: " + biome + "\n"
                + "Spielerposition: X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ() + "\n"
                + "Chunk: X=" + (pos.getX() >> 4) + ", Z=" + (pos.getZ() >> 4) + "\n"
                + "Tageszeit: " + dayPhase + " (Tick " + dayTime + " von 24000)\n"
                + "Wetter: " + weather + "\n"
                + "Block direkt unter den Füßen: " + blockBelow + "\n"
                + "Anvisierter Block: " + targetedBlock + "\n"
                + "Werkzeug- und Materialeinschätzung zum anvisierten Block: " + targetedBlockMining + "\n"
                + "Lichtlevel an der Spielerposition: " + lightLevel + "/15\n"
                + "Clientseitig erkannte Mobs im Umkreis von 16 Blöcken (höchstens 10): "
                + nearbyMobsSummary + "\n"
                + "Fallengelassene Gegenstände im Umkreis von 12 Blöcken (höchstens 10 Itemtypen): "
                + nearbyDropsSummary + "\n"
                + localRiskSummary + "\n"
                + readinessSummary + "\n"
                + equipmentAdvisorSummary + "\n"
                + "Gesundheit: " + health + "\n"
                + "Hunger: " + hunger + ", Sättigung: " + saturation + "\n"
                + "Lokale Situation: " + movement + "\n"
                + "Haupthand: " + describeStack(player.getMainHandStack()) + "\n"
                + "Nebenhand: " + describeStack(player.getOffHandStack()) + "\n"
                + "Rüstung: " + armorSummary + "\n"
                + "Inventar (36 Haupt-/Hotbar-Slots, nach Itemnamen zusammengefasst; höchstens 12 Einträge): "
                + inventorySummary + "\n"
                + "Grenze: Das sind nur aktuell clientseitig verfügbare Werte, keine Karte der Umgebung. "
                + "Ungeladene Chunks, versteckte Entities und serverseitig nicht gesendete Daten sind nicht enthalten.";
    }

    /**
     * Build a bounded local snapshot of equipped armor, carried armor alternatives,
     * utility mining tools and enchanted books. This method never mutates an ItemStack.
     */
    private static String equipmentAdvisorSummary(net.minecraft.client.network.ClientPlayerEntity player) {
        List<EquipmentSnapshot> equipment = new ArrayList<>();
        addEquippedArmorSnapshot(equipment, player, EquipmentSlot.HEAD);
        addEquippedArmorSnapshot(equipment, player, EquipmentSlot.CHEST);
        addEquippedArmorSnapshot(equipment, player, EquipmentSlot.LEGS);
        addEquippedArmorSnapshot(equipment, player, EquipmentSlot.FEET);

        List<ItemStack> enchantedBooks = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            if ("minecraft:enchanted_book".equals(itemId)) {
                if (enchantedBooks.size() < 24) enchantedBooks.add(stack);
                continue;
            }

            EquipmentSlot armorSlot = armorSlot(stack);
            if (armorSlot != null) {
                equipment.add(new EquipmentSnapshot(stack, armorCategory(armorSlot),
                        inventorySlotLabel(slot), false, armorSlot));
                continue;
            }

            String toolCategory = utilityToolCategory(itemId);
            if (toolCategory != null) {
                equipment.add(new EquipmentSnapshot(stack, toolCategory,
                        inventorySlotLabel(slot), false, null));
            }
            if (equipment.size() >= 48) break;
        }

        List<AILocalEquipmentAdvisor.GearInfo> gear = new ArrayList<>();
        for (EquipmentSnapshot snapshot : equipment) {
            ItemStack stack = snapshot.stack();
            int armorPoints = 0;
            float armorToughness = 0.0F;
            if (snapshot.armorSlot() != null) {
                armorPoints = (int) Math.round(
                        armorAttributeValue(stack, snapshot.armorSlot(), EntityAttributes.ARMOR));
                armorToughness = (float) armorAttributeValue(
                        stack, snapshot.armorSlot(), EntityAttributes.ARMOR_TOUGHNESS);
            }
            gear.add(new AILocalEquipmentAdvisor.GearInfo(
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getName().getString(),
                    snapshot.category(),
                    snapshot.location(),
                    snapshot.equipped(),
                    remainingDurabilityPercent(stack),
                    armorPoints,
                    armorToughness,
                    snapshotEnchantments(stack)));
            if (gear.size() >= 48) break;
        }

        List<AILocalEquipmentAdvisor.BookOpportunity> bookOpportunities =
                compatibleEnchantmentBookOpportunities(equipment, enchantedBooks);
        return AILocalEquipmentAdvisor.summarize(gear, bookOpportunities);
    }

    private static void addEquippedArmorSnapshot(
            List<EquipmentSnapshot> equipment,
            net.minecraft.client.network.ClientPlayerEntity player,
            EquipmentSlot slot) {
        ItemStack stack = player.getEquippedStack(slot);
        if (stack == null || stack.isEmpty()) return;
        String category = armorCategory(slot);
        if (category == null) return;
        equipment.add(new EquipmentSnapshot(stack, category,
                "getragen (" + categoryLabelForSlot(slot) + ")", true, slot));
    }

    private static EquipmentSlot armorSlot(ItemStack stack) {
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable == null) return null;
        EquipmentSlot slot = equippable.slot();
        return armorCategory(slot) == null ? null : slot;
    }

    private static String armorCategory(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "armor:head";
            case CHEST -> "armor:chest";
            case LEGS -> "armor:legs";
            case FEET -> "armor:feet";
            default -> null;
        };
    }

    private static String categoryLabelForSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Helm";
            case CHEST -> "Brust-Rüstung";
            case LEGS -> "Bein-Rüstung";
            case FEET -> "Stiefel";
            default -> "Rüstung";
        };
    }

    private static String utilityToolCategory(String itemId) {
        String path = itemId.substring(itemId.indexOf(':') + 1);
        if (path.endsWith("_pickaxe")) return "pickaxe";
        if (path.endsWith("_shovel")) return "shovel";
        if (path.endsWith("_hoe")) return "hoe";
        return null;
    }

    private static String inventorySlotLabel(int slot) {
        return slot < 9 ? "Hotbar-Slot " + (slot + 1) : "Inventar-Slot " + (slot - 8);
    }

    private static double armorAttributeValue(
            ItemStack stack,
            EquipmentSlot equipmentSlot,
            RegistryEntry<EntityAttribute> attribute) {
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0;

        double value = 0.0;
        for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
            if (!entry.attribute().equals(attribute) || !entry.slot().matches(equipmentSlot)) continue;
            EntityAttributeModifier modifier = entry.modifier();
            if (modifier.operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                value += modifier.value();
            }
        }
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, value);
    }

    private static List<AILocalEquipmentAdvisor.EnchantmentInfo> snapshotEnchantments(ItemStack stack) {
        ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
        List<AILocalEquipmentAdvisor.EnchantmentInfo> result = new ArrayList<>();
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(entry);
            if (level <= 0) continue;
            String id = entry.getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("unregistered:enchantment");
            String name = Enchantment.getName(entry, level).getString();
            result.add(new AILocalEquipmentAdvisor.EnchantmentInfo(id, name, level));
            if (result.size() >= 16) break;
        }
        result.sort(java.util.Comparator.comparing(AILocalEquipmentAdvisor.EnchantmentInfo::id));
        return List.copyOf(result);
    }

    private static List<AILocalEquipmentAdvisor.BookOpportunity> compatibleEnchantmentBookOpportunities(
            List<EquipmentSnapshot> equipment,
            List<ItemStack> enchantedBooks) {
        List<AILocalEquipmentAdvisor.BookOpportunity> opportunities = new ArrayList<>();
        for (EquipmentSnapshot target : equipment) {
            ItemEnchantmentsComponent existing = EnchantmentHelper.getEnchantments(target.stack());
            for (ItemStack book : enchantedBooks) {
                ItemEnchantmentsComponent stored = EnchantmentHelper.getEnchantments(book);
                for (RegistryEntry<Enchantment> candidate : stored.getEnchantments()) {
                    int bookLevel = stored.getLevel(candidate);
                    if (bookLevel <= 0 || !candidate.value().isAcceptableItem(target.stack())) continue;

                    int currentLevel = existing.getLevel(candidate);
                    int maximumLevel = candidate.value().getMaxLevel();
                    if (bookLevel < currentLevel
                            || (bookLevel == currentLevel && bookLevel >= maximumLevel)) continue;

                    boolean compatible = true;
                    for (RegistryEntry<Enchantment> current : existing.getEnchantments()) {
                        if (current.getKeyOrValue().equals(candidate.getKeyOrValue())) continue;
                        if (!Enchantment.canBeCombined(current, candidate)) {
                            compatible = false;
                            break;
                        }
                    }
                    if (!compatible) continue;

                    String enchantmentName = Enchantment.getName(candidate, bookLevel).getString();
                    opportunities.add(new AILocalEquipmentAdvisor.BookOpportunity(
                            book.getName().getString(),
                            target.stack().getName().getString(),
                            enchantmentName,
                            bookLevel));
                    if (opportunities.size() >= 10) return List.copyOf(opportunities);
                }
            }
        }
        return List.copyOf(opportunities);
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "leer";
        int durabilityPercent = remainingDurabilityPercent(stack);
        String durability = durabilityPercent < 0
                ? ""
                : " (Resthaltbarkeit " + durabilityPercent + "%)";
        return stack.getName().getString() + " x" + stack.getCount() + durability;
    }

    private static int remainingDurabilityPercent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        int maxDamage = stack.getMaxDamage();
        if (maxDamage <= 0) return -1;
        int remaining = Math.max(0, maxDamage - stack.getDamage());
        return (int) Math.round(remaining * 100.0 / maxDamage);
    }
}

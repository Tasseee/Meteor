package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicit, hands-free Nightfall voice call using the Realtime WebSocket API.
 *
 * The microphone is opened only while a call is active. Audio stays in memory and is
 * streamed to the configured provider; no recording is written to disk. World actions
 * are still routed through AIActionRegistry and require the normal player confirmation.
 */
public final class AIRealtimeVoiceClient {
    private static final String MODEL = "gpt-realtime-2.1-mini";
    private static final AudioFormat FORMAT = new AudioFormat(24_000.0f, 16, 1, true, false);
    private static final int AUDIO_CHUNK_BYTES = 4_800; // 100 ms at 24 kHz, mono, 16-bit
    private static final byte[] PLAYBACK_END = new byte[0];

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    public interface Listener {
        void onStatus(String message);
        void onTranscript(String transcript);
        void onAssistantText(String text);
        void onToolCall(AIChatClient.ToolCall call);
    }

    private static volatile boolean active;
    private static volatile boolean ready;
    private static volatile boolean microphoneSending;
    private static volatile boolean speaking;
    private static volatile boolean speechEnabled = true;
    private static volatile TargetDataLine microphoneLine;
    private static volatile SourceDataLine speakerLine;
    private static volatile WebSocket socket;
    private static volatile Listener listener;
    private static volatile Thread captureThread;
    private static volatile Thread playbackThread;
    private static volatile long generation;
    private static final AtomicBoolean CAPTURE_STARTED = new AtomicBoolean();
    private static volatile LinkedBlockingQueue<byte[]> playbackQueue = new LinkedBlockingQueue<>();
    private static CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);
    private static final StringBuilder incomingText = new StringBuilder();

    private AIRealtimeVoiceClient() {}

    public static boolean isActive() { return active; }
    public static boolean isReady() { return active && ready; }
    public static boolean isSpeaking() { return speaking; }

    public static synchronized void start(Listener callbacks) {
        if (active) {
            status("Nightfall-Sprachanruf läuft bereits.");
            return;
        }
        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        if (settings.apiKey().isBlank()) {
            notifyStatus(callbacks, "Für den Sprachanruf fehlt der API-Key in config/nightfall/ai.json.");
            return;
        }

        listener = callbacks;
        active = true;
        ready = false;
        microphoneSending = false;
        speaking = false;
        speechEnabled = settings.voiceOutputEnabled();
        CAPTURE_STARTED.set(false);
        playbackQueue = new LinkedBlockingQueue<>();
        incomingText.setLength(0);
        long callGeneration = ++generation;
        notifyStatus(callbacks, "Verbinde Nightfall-Sprachanruf …");

        try {
            URI endpoint = realtimeEndpoint(settings.audioBaseUrl());
            HTTP.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .buildAsync(endpoint, new SocketListener(callGeneration, settings))
                    .whenComplete((webSocket, failure) -> {
                        if (failure != null && callGeneration == generation) {
                            failCall("Realtime-Verbindung fehlgeschlagen: " + failure.getClass().getSimpleName());
                        }
                    });
        } catch (RuntimeException failure) {
            active = false;
            listener = null;
            notifyStatus(callbacks, "Realtime-Verbindung konnte nicht gestartet werden: " + failure.getMessage());
        }
    }

    public static synchronized void stop() {
        if (!active) return;
        active = false;
        ready = false;
        microphoneSending = false;
        speaking = false;
        generation++;
        TargetDataLine mic = microphoneLine;
        microphoneLine = null;
        if (mic != null) {
            try { mic.stop(); } catch (RuntimeException ignored) {}
            try { mic.close(); } catch (RuntimeException ignored) {}
        }
        playbackQueue.clear();
        playbackQueue.offer(PLAYBACK_END);
        SourceDataLine speaker = speakerLine;
        speakerLine = null;
        if (speaker != null) {
            try { speaker.stop(); } catch (RuntimeException ignored) {}
            try { speaker.close(); } catch (RuntimeException ignored) {}
        }
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "Nightfall voice call stopped"); }
            catch (RuntimeException ignored) {}
        }
        CAPTURE_STARTED.set(false);
        Listener oldListener = listener;
        listener = null;
        notifyStatus(oldListener, "Nightfall-Sprachanruf beendet · Mikrofon geschlossen.");
    }

    public static void setSpeechEnabled(boolean enabled) {
        speechEnabled = enabled;
        if (!enabled) {
            playbackQueue.clear();
            if (speaking) playbackQueue.offer(PLAYBACK_END);
        }
    }

    /** Send a typed message into the live voice conversation. */
    public static void sendText(String text) {
        if (!active || !ready || text == null || text.isBlank()) return;
        JsonObject item = new JsonObject();
        item.addProperty("type", "conversation.item.create");
        JsonObject message = new JsonObject();
        message.addProperty("type", "message");
        message.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("type", "input_text");
        textPart.addProperty("text", text.trim());
        content.add(textPart);
        message.add("content", content);
        item.add("item", message);
        sendJson(item);
        requestResponse();
    }

    /** Submit a confirmed/cancelled action result back into the live tool conversation. */
    public static void sendFunctionOutput(String callId, String output) {
        if (!active || !ready || callId == null || callId.isBlank()) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "conversation.item.create");
        JsonObject item = new JsonObject();
        item.addProperty("type", "function_call_output");
        item.addProperty("call_id", callId);
        item.addProperty("output", output == null ? "Aktion ohne Ergebnis beendet." : output);
        event.add("item", item);
        sendJson(event);
        requestResponse();
    }

    /** Speak a short confirmation prompt through the active Realtime voice session. */
    public static void speakPrompt(String prompt) {
        if (!active || !ready || prompt == null || prompt.isBlank()) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "response.create");
        JsonObject response = new JsonObject();
        JsonArray modalities = new JsonArray();
        modalities.add("audio");
        response.add("output_modalities", modalities);
        response.addProperty("instructions",
                "Sprich die folgende Nachricht auf Deutsch, freundlich und klar. Füge keine zusätzlichen Aussagen hinzu: "
                        + prompt);
        response.add("input", new JsonArray());
        response.add("tools", new JsonArray());
        response.addProperty("tool_choice", "none");
        event.add("response", response);
        sendJson(event);
    }

    public static void requestResponse() {
        if (!active || !ready) return;
        JsonObject event = new JsonObject();
        event.addProperty("type", "response.create");
        sendJson(event);
    }

    private static URI realtimeEndpoint(String configuredBase) {
        URI base = URI.create(configuredBase.trim());
        String scheme = base.getScheme();
        if (base.getHost() == null || scheme == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")
                || scheme.equalsIgnoreCase("wss") || scheme.equalsIgnoreCase("ws"))) {
            throw new IllegalArgumentException("audioBaseUrl muss eine gültige HTTP(S)- oder WS(S)-URL sein.");
        }
        String wsScheme = scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("wss") ? "wss" : "ws";
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        if (!path.endsWith("/v1")) path += "/v1";
        return URI.create(wsScheme + "://" + base.getAuthority() + path
                + "/realtime?model=" + MODEL);
    }

    private static JsonObject sessionUpdate(AIConfig.Settings settings) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "session.update");
        JsonObject session = new JsonObject();
        session.addProperty("type", "realtime");
        session.addProperty("instructions", """
                Du bist Nightfall AI, ein freundlicher, lockerer Minecraft-Spielpartner für Minecraft 1.21.11.
                Sprich standardmäßig Deutsch, natürlich und eher kurz, damit ein echtes Gespräch entsteht.
                Du bist eine KI und darfst niemals behaupten, ein Mensch zu sein. Du kannst mit dem Spieler
                plaudern, ihn beim Spielen begleiten, kleine Herausforderungen vorschlagen und Minecraft-Aktionen
                mit den bereitgestellten Werkzeugen erledigen. Erfinde keine Spielerpositionen, Inventargegenstände,
                Weltzustände oder Ergebnisse. Bevor du etwas in der Welt veränderst oder private Inventar-/Statusdaten
                liest, rufe das passende Werkzeug auf und warte auf die Bestätigung des Spielers. Behaupte nie,
                eine Aktion sei ausgeführt, bevor du ihr Werkzeugergebnis erhalten hast. Nutze Werkzeuge nur,
                wenn der Spieler die entsprechende Aktion tatsächlich wünscht. Bei unklaren Wünschen frage nach.
                Achte besonders darauf, bei Bau-, Abbau-, Sammel-, Crafting- oder Ofenaktionen die Vorschau
                abzuwarten und die separate Bestätigung zu verlangen. Spiele freundlich mit dem Spieler, aber
                dränge ihn nicht zu riskanten Aktionen.
                """);
        JsonArray modalities = new JsonArray();
        modalities.add("audio");
        session.add("output_modalities", modalities);

        JsonObject audio = new JsonObject();
        JsonObject input = new JsonObject();
        JsonObject inputFormat = new JsonObject();
        inputFormat.addProperty("type", "audio/pcm");
        inputFormat.addProperty("rate", 24_000);
        input.add("format", inputFormat);
        JsonObject noise = new JsonObject();
        noise.addProperty("type", "near_field");
        input.add("noise_reduction", noise);
        JsonObject transcription = new JsonObject();
        transcription.addProperty("model", "gpt-transcribe");
        transcription.addProperty("language", "de");
        input.add("transcription", transcription);
        JsonObject vad = new JsonObject();
        vad.addProperty("type", "server_vad");
        vad.addProperty("threshold", 0.5);
        vad.addProperty("prefix_padding_ms", 300);
        vad.addProperty("silence_duration_ms", 700);
        vad.addProperty("create_response", false);
        vad.addProperty("interrupt_response", false);
        input.add("turn_detection", vad);
        audio.add("input", input);

        JsonObject output = new JsonObject();
        JsonObject outputFormat = new JsonObject();
        outputFormat.addProperty("type", "audio/pcm");
        outputFormat.addProperty("rate", 24_000);
        output.add("format", outputFormat);
        output.addProperty("voice", settings.speechVoice());
        audio.add("output", output);
        session.add("audio", audio);

        JsonArray tools = new JsonArray();
        for (JsonElement element : AIActionRegistry.toolDefinitions()) {
            if (!element.isJsonObject()) continue;
            JsonObject chatTool = element.getAsJsonObject();
            if (!chatTool.has("function") || !chatTool.get("function").isJsonObject()) continue;
            JsonObject definition = chatTool.getAsJsonObject("function");
            JsonObject realtimeTool = new JsonObject();
            realtimeTool.addProperty("type", "function");
            realtimeTool.add("name", definition.get("name"));
            realtimeTool.add("description", definition.get("description"));
            realtimeTool.add("parameters", definition.get("parameters"));
            tools.add(realtimeTool);
        }
        session.add("tools", tools);
        session.addProperty("tool_choice", "auto");
        event.add("session", session);
        return event;
    }

    private static void sendJson(JsonObject value) {
        WebSocket ws = socket;
        if (ws == null || !active) return;
        synchronized (AIRealtimeVoiceClient.class) {
            sendChain = sendChain.handle((previous, failure) -> socket)
                    .thenCompose(current -> current == null
                            ? CompletableFuture.failedFuture(new IllegalStateException("Realtime socket closed"))
                            : current.sendText(value.toString(), true));
        }
    }

    private static void startAudioWorkers(long callGeneration) {
        if (!CAPTURE_STARTED.compareAndSet(false, true)) return;
        Thread capture = new Thread(() -> captureMicrophone(callGeneration), "Nightfall-Realtime-Microphone");
        capture.setDaemon(true);
        captureThread = capture;
        capture.start();

        LinkedBlockingQueue<byte[]> callQueue = playbackQueue;
        Thread playback = new Thread(() -> playAudio(callGeneration, callQueue), "Nightfall-Realtime-Speaker");
        playback.setDaemon(true);
        playbackThread = playback;
        playback.start();
    }

    private static void captureMicrophone(long callGeneration) {
        TargetDataLine line = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT, 16_384);
            if (!active || callGeneration != generation) return;
            microphoneLine = line;
            line.start();
            byte[] buffer = new byte[AUDIO_CHUNK_BYTES];
            while (active && callGeneration == generation) {
                int count = line.read(buffer, 0, buffer.length);
                if (count <= 0 || !microphoneSending || !ready) continue;
                byte[] chunk = count == buffer.length ? buffer.clone() : java.util.Arrays.copyOf(buffer, count);
                JsonObject event = new JsonObject();
                event.addProperty("type", "input_audio_buffer.append");
                event.addProperty("audio", Base64.getEncoder().encodeToString(chunk));
                sendJson(event);
            }
        } catch (Exception failure) {
            if (active && callGeneration == generation)
                failCall("Mikrofon nicht verfügbar: " + failure.getClass().getSimpleName()
                        + ". Prüfe das Standard-Eingabegerät und die Mikrofonberechtigung.");
        } finally {
            microphoneSending = false;
            if (callGeneration == generation && microphoneLine == line) microphoneLine = null;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) {}
                try { line.close(); } catch (RuntimeException ignored) {}
            }
        }
    }

    private static void playAudio(long callGeneration, LinkedBlockingQueue<byte[]> callQueue) {
        SourceDataLine line = null;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, FORMAT);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(FORMAT, 16_384);
            if (!active || callGeneration != generation) return;
            speakerLine = line;
            line.start();
            while (active && callGeneration == generation) {
                byte[] chunk = callQueue.take();
                if (chunk == PLAYBACK_END) {
                    line.drain();
                    speaking = false;
                    microphoneSending = ready;
                    continue;
                }
                if (chunk.length > 0 && speechEnabled) {
                    speaking = true;
                    line.write(chunk, 0, chunk.length);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            if (active && callGeneration == generation) {
                speechEnabled = false;
                speaking = false;
                microphoneSending = ready;
                status("Sprachausgabe ist auf diesem Gerät nicht verfügbar: " + failure.getClass().getSimpleName());
            }
        } finally {
            if (callGeneration == generation && speakerLine == line) speakerLine = null;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) {}
                try { line.close(); } catch (RuntimeException ignored) {}
            }
        }
    }

    private static void handleServerEvent(long callGeneration, String jsonText) {
        if (!active || callGeneration != generation) return;
        try {
            JsonObject event = JsonParser.parseString(jsonText).getAsJsonObject();
            String type = event.has("type") ? event.get("type").getAsString() : "";
            switch (type) {
                case "session.updated" -> {
                    ready = true;
                    microphoneSending = true;
                    startAudioWorkers(callGeneration);
                    status("Nightfall ist im Call · Du kannst sprechen. F8 beendet den Anruf.");
                }
                case "conversation.item.input_audio_transcription.completed" -> {
                    String transcript = event.has("transcript") ? event.get("transcript").getAsString().trim() : "";
                    if (!transcript.isBlank()) notifyTranscript(transcript);
                }
                case "response.output_audio.delta" -> {
                    if (speechEnabled && event.has("delta")) {
                        microphoneSending = false;
                        speaking = true;
                        playbackQueue.offer(Base64.getDecoder().decode(event.get("delta").getAsString()));
                    }
                }
                case "response.output_audio.done" -> {
                    if (speechEnabled) playbackQueue.offer(PLAYBACK_END);
                    else {
                        speaking = false;
                        microphoneSending = ready;
                    }
                }
                case "response.output_audio_transcript.done" -> {
                    String transcript = event.has("transcript") ? event.get("transcript").getAsString().trim() : "";
                    if (!transcript.isBlank()) notifyAssistantText(transcript);
                }
                case "response.done" -> handleResponseDone(event);
                case "error", "conversation.item.input_audio_transcription.failed" -> {
                    String detail = event.has("error") && event.get("error").isJsonObject()
                            ? event.getAsJsonObject("error").has("message")
                                ? event.getAsJsonObject("error").get("message").getAsString()
                                : "Unbekannter Realtime-Fehler"
                            : "Spracherkennung fehlgeschlagen";
                    status("Realtime: " + detail);
                    if (type.equals("error")) microphoneSending = ready && !speaking;
                }
                default -> { }
            }
        } catch (RuntimeException invalidEvent) {
            status("Nightfall konnte ein Realtime-Ereignis nicht lesen.");
        }
    }

    private static void handleResponseDone(JsonObject event) {
        if (!event.has("response") || !event.get("response").isJsonObject()) return;
        JsonObject response = event.getAsJsonObject("response");
        if (response.has("status") && !"completed".equals(response.get("status").getAsString())) {
            String detail = response.has("status_details") ? response.get("status_details").toString() : "";
            if (!detail.isBlank() && !"null".equals(detail)) status("Realtime-Antwort nicht abgeschlossen: " + detail);
        }
        if (!response.has("output") || !response.get("output").isJsonArray()) return;
        for (JsonElement element : response.getAsJsonArray("output")) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            if (!item.has("type") || !"function_call".equals(item.get("type").getAsString())) continue;
            String callId = item.has("call_id") ? item.get("call_id").getAsString() : "";
            String name = item.has("name") ? item.get("name").getAsString() : "";
            String arguments = item.has("arguments") ? item.get("arguments").getAsString() : "{}";
            if (!callId.isBlank() && !name.isBlank()) notifyToolCall(new AIChatClient.ToolCall(callId, name, arguments));
        }
    }

    private static void notifyTranscript(String text) {
        Listener callbacks = listener;
        if (callbacks != null) MinecraftClient.getInstance().execute(() -> {
            if (active && listener == callbacks) callbacks.onTranscript(text);
        });
    }

    private static void notifyAssistantText(String text) {
        Listener callbacks = listener;
        if (callbacks != null) MinecraftClient.getInstance().execute(() -> {
            if (active && listener == callbacks) callbacks.onAssistantText(text);
        });
    }

    private static void notifyToolCall(AIChatClient.ToolCall call) {
        Listener callbacks = listener;
        if (callbacks != null) MinecraftClient.getInstance().execute(() -> {
            if (active && listener == callbacks) callbacks.onToolCall(call);
        });
    }

    private static void status(String message) {
        notifyStatus(listener, message);
    }

    private static void notifyStatus(Listener callbacks, String message) {
        if (callbacks == null) return;
        MinecraftClient.getInstance().execute(() -> callbacks.onStatus(message));
    }

    private static void failCall(String message) {
        Listener callbacks = listener;
        stop();
        notifyStatus(callbacks, message);
    }

    private static final class SocketListener implements WebSocket.Listener {
        private final long callGeneration;
        private final AIConfig.Settings settings;
        private final StringBuilder fragments = new StringBuilder();

        private SocketListener(long callGeneration, AIConfig.Settings settings) {
            this.callGeneration = callGeneration;
            this.settings = settings;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            if (callGeneration != generation || !active) {
                webSocket.abort();
                return;
            }
            socket = webSocket;
            synchronized (AIRealtimeVoiceClient.class) {
                sendChain = CompletableFuture.completedFuture(webSocket);
            }
            webSocket.request(1);
            sendJson(sessionUpdate(settings));
            status("Realtime verbunden · richte Mikrofon und Sprache ein …");
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            synchronized (fragments) {
                fragments.append(data);
                if (last) {
                    String message = fragments.toString();
                    fragments.setLength(0);
                    handleServerEvent(callGeneration, message);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (callGeneration == generation && active) {
                String detail = reason == null || reason.isBlank() ? "Verbindung wurde geschlossen." : reason;
                failCall("Nightfall-Sprachanruf getrennt: " + detail);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (callGeneration == generation && active)
                failCall("Realtime-Verbindung verloren: " + error.getClass().getSimpleName());
        }
    }
}

package dev.nightfall.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Optional, push-to-talk voice input and spoken replies.
 *
 * The microphone is opened only after the player explicitly starts a recording.
 * Audio is sent only to the configured speech endpoint and is never stored on disk.
 */
public final class AIVoiceClient {
    private static final AudioFormat FORMAT = new AudioFormat(16_000.0f, 16, 1, true, false);
    private static final int MAX_RECORD_SECONDS = 20;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static volatile boolean busy;
    private static volatile boolean capturing;
    private static volatile boolean stopRequested;
    private static volatile boolean cancelRequested;
    private static volatile long speechGeneration;
    private static volatile TargetDataLine recordingLine;
    private static volatile SourceDataLine playbackLine;

    private AIVoiceClient() {}

    public static boolean isCapturing() { return capturing; }
    public static boolean isBusy() { return busy; }

    /** F8 toggles an explicit recording; reaching the time limit also stops it. */
    public static synchronized void toggleRecording(Consumer<String> onTranscript,
                                                    Consumer<String> onStatus) {
        if (capturing) {
            stopRequested = true;
            TargetDataLine line = recordingLine;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) {}
                try { line.close(); } catch (RuntimeException ignored) {}
            }
            notifyStatus(onStatus, "Aufnahme beendet · Sprache wird verarbeitet …");
            return;
        }
        if (busy) {
            notifyStatus(onStatus, "Sprachaufnahme oder -verarbeitung läuft bereits.");
            return;
        }

        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        if (!settings.voiceInputEnabled()) {
            notifyStatus(onStatus, "Spracheingabe ist aus. Aktiviere voiceInputEnabled in config/nightfall/ai.json.");
            return;
        }
        if (settings.apiKey().isBlank()) {
            notifyStatus(onStatus, "Für Sprache fehlt der API-Key in config/nightfall/ai.json.");
            return;
        }

        busy = true;
        capturing = true;
        stopRequested = false;
        cancelRequested = false;
        notifyStatus(onStatus, "Mikrofon aktiv · F8 beendet die Aufnahme (max. 20 Sekunden).");
        Thread worker = new Thread(() -> captureAndTranscribe(settings, onTranscript, onStatus),
                "Nightfall-Voice-Input");
        worker.setDaemon(true);
        worker.start();
    }

    public static synchronized void stopRecording() {
        if (!capturing) return;
        cancelRequested = true;
        stopRequested = true;
        TargetDataLine line = recordingLine;
        if (line != null) {
            try { line.stop(); } catch (RuntimeException ignored) {}
            try { line.close(); } catch (RuntimeException ignored) {}
        }
    }

    private static void captureAndTranscribe(AIConfig.Settings settings,
                                             Consumer<String> onTranscript,
                                             Consumer<String> onStatus) {
        byte[] wav;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(FORMAT, 8192);
            recordingLine = line;
            line.start();

            long maximumBytes = (long) FORMAT.getSampleRate() * FORMAT.getFrameSize() * MAX_RECORD_SECONDS;
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!stopRequested && pcm.size() < maximumBytes) {
                int read = line.read(buffer, 0, (int) Math.min(buffer.length, maximumBytes - pcm.size()));
                if (read > 0) pcm.write(buffer, 0, read);
            }
            line.stop();
            line.close();
            recordingLine = null;

            if (cancelRequested) {
                busy = false;
                return;
            }
            byte[] rawPcm = pcm.toByteArray();
            if (rawPcm.length < 3200) {
                busy = false;
                notifyStatus(onStatus, "Keine ausreichende Aufnahme erkannt. Drücke F8 und sprich erneut.");
                return;
            }
            try (AudioInputStream input = new AudioInputStream(
                    new ByteArrayInputStream(rawPcm), FORMAT, rawPcm.length / FORMAT.getFrameSize());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                AudioSystem.write(input, AudioFileFormat.Type.WAVE, output);
                wav = output.toByteArray();
            }
        } catch (Exception failure) {
            busy = false;
            recordingLine = null;
            notifyStatus(onStatus, "Mikrofon konnte nicht verwendet werden: " + failure.getClass().getSimpleName()
                    + ". Prüfe Mikrofon und Betriebssystem-Berechtigungen.");
            return;
        } finally {
            capturing = false;
            TargetDataLine line = recordingLine;
            recordingLine = null;
            if (line != null) {
                try { line.close(); } catch (RuntimeException ignored) {}
            }
        }

        if (cancelRequested) {
            busy = false;
            return;
        }
        notifyStatus(onStatus, "Sprachaufnahme wird an den konfigurierten Anbieter gesendet …");
        transcribe(settings, wav, onTranscript, onStatus);
    }

    private static void transcribe(AIConfig.Settings settings, byte[] wav,
                                   Consumer<String> onTranscript, Consumer<String> onStatus) {
        try {
            String boundary = "Nightfall" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\n"
                    + settings.transcriptionModel() + "\r\n");
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"voice.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n");
            body.write(wav);
            write(body, "\r\n--" + boundary + "--\r\n");

            HttpRequest.Builder request = HttpRequest.newBuilder(endpoint(settings, "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(75))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + settings.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
            HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, failure) -> {
                        busy = false;
                        if (failure != null) {
                            notifyStatus(onStatus, "Spracherkennung fehlgeschlagen: Verbindung zum Anbieter nicht möglich.");
                            return;
                        }
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            notifyStatus(onStatus, "Spracherkennung vom Anbieter abgelehnt (HTTP "
                                    + response.statusCode() + "). Prüfe Audio-Endpunkt und Modell.");
                            return;
                        }
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            String transcript = json.has("text") ? json.get("text").getAsString().trim() : "";
                            if (transcript.isBlank()) {
                                notifyStatus(onStatus, "Keine Sprache erkannt. Du kannst es erneut versuchen.");
                                return;
                            }
                            notifyText(onTranscript, transcript);
                            notifyStatus(onStatus, "Sprache erkannt · prüfe den Text und drücke Enter zum Senden.");
                        } catch (RuntimeException invalidResponse) {
                            notifyStatus(onStatus, "Der Sprachanbieter lieferte keine gültige Transkription.");
                        }
                    });
        } catch (Exception failure) {
            busy = false;
            notifyStatus(onStatus, "Spracherkennung konnte nicht gestartet werden: " + failure.getClass().getSimpleName());
        }
    }

    /** Speaks a final assistant answer only when voice output was explicitly enabled. */
    public static void speak(String text) {
        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();
        if (!settings.voiceOutputEnabled() || settings.apiKey().isBlank() || text == null || text.isBlank()) return;
        String spoken = text.trim();
        if (spoken.length() > 1200) spoken = spoken.substring(0, 1200);

        final String input = spoken;
        final long generation = ++speechGeneration;
        Thread worker = new Thread(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("model", settings.speechModel());
                payload.addProperty("voice", settings.speechVoice());
                payload.addProperty("input", input);
                payload.addProperty("response_format", "wav");
                HttpRequest request = HttpRequest.newBuilder(endpoint(settings, "/audio/speech"))
                        .timeout(Duration.ofSeconds(75))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + settings.apiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300
                        || generation != speechGeneration) return;
                playWav(response.body(), generation);
            } catch (Exception ignored) {
                // Voice output is optional; a TTS failure must never break the chat or game loop.
            }
        }, "Nightfall-Voice-Output");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stopSpeaking() {
        speechGeneration++;
        SourceDataLine line = playbackLine;
        playbackLine = null;
        if (line != null) {
            try { line.stop(); } catch (RuntimeException ignored) {}
            try { line.close(); } catch (RuntimeException ignored) {}
        }
    }

    private static void playWav(byte[] bytes, long generation) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
            AudioFormat format = input.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            playbackLine = line;
            line.open(format);
            line.start();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer, 0, buffer.length)) != -1
                    && playbackLine == line && generation == speechGeneration) {
                if (read > 0) line.write(buffer, 0, read);
            }
            if (playbackLine == line && generation == speechGeneration) line.drain();
            line.stop();
            line.close();
            if (playbackLine == line) playbackLine = null;
        }
    }

    private static URI endpoint(AIConfig.Settings settings, String path) {
        String base = settings.audioBaseUrl().trim().replaceAll("/+$", "");
        if (!base.endsWith("/v1")) base += "/v1";
        URI uri = URI.create(base + path);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")))
            throw new IllegalArgumentException("Ungültige audioBaseUrl");
        return uri;
    }

    private static void write(ByteArrayOutputStream output, String value) throws java.io.IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void notifyStatus(Consumer<String> callback, String value) {
        if (callback == null) return;
        MinecraftClient.getInstance().execute(() -> callback.accept(value));
    }

    private static void notifyText(Consumer<String> callback, String value) {
        if (callback == null) return;
        MinecraftClient.getInstance().execute(() -> callback.accept(value));
    }
}

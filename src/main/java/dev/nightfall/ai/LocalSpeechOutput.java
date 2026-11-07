package dev.nightfall.ai;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Local-only speech output through the operating system's installed speech
 * engine. Text is passed as stdin or a single process argument; no shell command
 * is assembled from model output and no cloud service is contacted.
 */
public final class LocalSpeechOutput {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Nightfall-Local-TTS");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean enabled;
    private static volatile Process activeProcess;
    private static volatile String status = "Lokale Sprachausgabe aus";

    private LocalSpeechOutput() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static String status() {
        return status;
    }

    public static boolean toggleEnabled() {
        enabled = !enabled;
        status = enabled ? "Lokale Sprachausgabe aktiviert" : "Lokale Sprachausgabe deaktiviert";
        if (!enabled) stopSpeaking();
        return enabled;
    }

    public static void speak(String text) {
        if (!enabled || text == null || text.isBlank()) return;
        String bounded = text.length() > 700 ? text.substring(0, 697) + "..." : text;
        WORKER.execute(() -> {
            try {
                speakLocally(bounded);
                status = "Sprachausgabe lokal abgeschlossen";
            } catch (Throwable failure) {
                status = "Lokale Sprachausgabe nicht verfügbar: " + safeMessage(failure);
                enabled = false;
                postStatus(status);
            }
        });
    }

    public static void stopSpeaking() {
        Process process = activeProcess;
        if (process != null) process.destroy();
    }

    private static void speakLocally(String text) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        Process process;
        if (os.contains("win")) {
            String script = "$ErrorActionPreference='Stop'; "
                    + "Add-Type -AssemblyName System.Speech; "
                    + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'de-*' } | Select-Object -First 1; "
                    + "if ($null -ne $v) { $s.SelectVoice($v.VoiceInfo.Name) }; "
                    + "$s.Speak([Console]::In.ReadToEnd()); $s.Dispose()";
            process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            activeProcess = process;
            try (OutputStream input = process.getOutputStream()) {
                input.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } else if (os.contains("mac")) {
            // macOS 'say' is local. The text is a single argument, not a shell fragment.
            process = new ProcessBuilder("say", text)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            activeProcess = process;
        } else {
            // Linux fallback: espeak-ng or espeak, both local command-line synthesizers.
            try {
                process = new ProcessBuilder("espeak-ng", "-v", "de", "--stdin")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (IOException missingEspeakNg) {
                process = new ProcessBuilder("espeak", "-v", "de", "--stdin")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
            }
            activeProcess = process;
            try (OutputStream input = process.getOutputStream()) {
                input.write(text.getBytes(StandardCharsets.UTF_8));
            }
        }

        if (!process.waitFor(40, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Die lokale Sprachausgabe hat das Zeitlimit überschritten.");
        }
        activeProcess = null;
        if (process.exitValue() != 0) throw new IOException("Das installierte lokale Sprachprogramm meldete einen Fehler.");
    }

    private static void postStatus(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) return;
        client.execute(() -> status = message);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
    }

    public static void shutdown() {
        enabled = false;
        stopSpeaking();
        WORKER.shutdownNow();
    }
}

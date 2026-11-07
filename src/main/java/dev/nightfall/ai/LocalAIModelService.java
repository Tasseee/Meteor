package dev.nightfall.ai;

import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Embedded local llama.cpp inference. No server, account, API key or cloud inference.
 * The only network operation is an explicit one-time download of the GGUF model file.
 */
public final class LocalAIModelService {
    private static final Object LOCK = new Object();
    private static final URI MODEL_URI = URI.create(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true");
    private static final long MIN_MODEL_BYTES = 500L * 1024L * 1024L;
    private static final long MAX_MODEL_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Nightfall-Local-AI");
        thread.setDaemon(true);
        return thread;
    });

    public enum State { MISSING, DOWNLOADING, LOADING, READY, GENERATING, ERROR, STOPPED }

    public record Result(boolean success, String text) {
        public Result {
            text = text == null ? "" : text;
        }
    }

    private static volatile State state = State.MISSING;
    private static volatile String status = "Lokales Modell noch nicht installiert";
    private static volatile long downloadedBytes;
    private static volatile long expectedBytes = -1L;
    private static volatile LlamaModel model;
    private static boolean setupRunning;
    private static boolean generationRunning;

    private LocalAIModelService() {}

    public static Path modelDirectory() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("nightfall").resolve("models");
    }

    public static Path modelFile() {
        return modelDirectory().resolve("qwen2.5-1.5b-instruct-q4_k_m.gguf");
    }

    public static State state() {
        return state;
    }

    public static String status() {
        if (state == State.DOWNLOADING && expectedBytes > 0) {
            long percent = Math.min(100L, downloadedBytes * 100L / expectedBytes);
            return "Modell-Download " + percent + "%";
        }
        return status;
    }

    public static boolean isReady() {
        return model != null;
    }

    /**
     * Called only after an explicit user click. Downloads the model if absent,
     * then loads it off the Minecraft client thread.
     */
    public static void setup(Consumer<Result> callback) {
        Objects.requireNonNull(callback, "callback");
        synchronized (LOCK) {
            if (model != null) {
                deliver(callback, new Result(true, "Das lokale Modell ist bereits geladen."));
                return;
            }
            if (setupRunning || generationRunning) {
                deliver(callback, new Result(false, "Das lokale KI-Modell wird bereits vorbereitet."));
                return;
            }
            setupRunning = true;
            state = Files.isRegularFile(modelFile()) ? State.LOADING : State.DOWNLOADING;
            status = "Lokales Modell wird vorbereitet …";
        }
        WORKER.execute(() -> {
            try {
                Files.createDirectories(modelDirectory());
                Path target = modelFile();
                if (!Files.isRegularFile(target) || Files.size(target) < MIN_MODEL_BYTES) {
                    Files.deleteIfExists(target);
                    downloadModel(target);
                }
                state = State.LOADING;
                status = "Qwen-Modell wird in den Arbeitsspeicher geladen …";
                LlamaModel loaded = new LlamaModel(new ModelParameters().setModel(target.toAbsolutePath().toString()));
                synchronized (LOCK) {
                    model = loaded;
                    setupRunning = false;
                    state = State.READY;
                    status = "Lokales Qwen-Modell bereit · CPU-Inferenz";
                }
                deliver(callback, new Result(true, "Nightfall AI ist jetzt lokal bereit. Keine Anfrage wird an einen KI-Anbieter gesendet."));
            } catch (Throwable failure) {
                synchronized (LOCK) {
                    setupRunning = false;
                    state = State.ERROR;
                    status = "Lokales Modell fehlgeschlagen: " + safeMessage(failure);
                }
                deliver(callback, new Result(false, status));
            }
        });
    }

    /** Runs a complete inference away from Minecraft's render/tick thread. */
    public static void generate(String prompt, Consumer<Result> callback) {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(callback, "callback");
        final LlamaModel active;
        synchronized (LOCK) {
            active = model;
            if (active == null) {
                deliver(callback, new Result(false, "Das lokale Modell ist nicht geladen. Klicke zuerst auf MODELL LADEN."));
                return;
            }
            if (setupRunning || generationRunning) {
                deliver(callback, new Result(false, "Nightfall verarbeitet bereits eine lokale KI-Anfrage."));
                return;
            }
            generationRunning = true;
            state = State.GENERATING;
            status = "Nightfall denkt lokal auf diesem PC …";
        }
        WORKER.execute(() -> {
            Result result;
            try {
                String output = active.complete(new InferenceParameters(prompt)
                        .setTemperature(0.15f)
                        .setStopStrings("<|im_end|>", "<|endoftext|>"));
                if (output == null || output.isBlank()) {
                    result = new Result(false, "Das lokale Modell hat keine Antwort erzeugt.");
                } else {
                    String bounded = output.length() > 24_000 ? output.substring(0, 24_000) : output;
                    result = new Result(true, bounded);
                }
            } catch (Throwable failure) {
                result = new Result(false, "Lokale Inferenz fehlgeschlagen: " + safeMessage(failure));
            } finally {
                synchronized (LOCK) {
                    generationRunning = false;
                    state = model == null ? State.ERROR : State.READY;
                    status = model == null ? "Lokales Modell nicht verfügbar" : "Lokales Modell bereit";
                }
            }
            deliver(callback, result);
        });
    }

    private static void downloadModel(Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = null;
        try {
            status = "Modell wird einmalig von Hugging Face geladen …";
            connection = (HttpURLConnection) MODEL_URI.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "Nightfall-Minecraft-Client/2.0");
            connection.setRequestProperty("Accept", "application/octet-stream");
            int response = connection.getResponseCode();
            if (response != HttpURLConnection.HTTP_OK) {
                throw new IOException("Modellserver antwortete mit HTTP " + response + ".");
            }
            expectedBytes = connection.getContentLengthLong();
            if (expectedBytes > MAX_MODEL_BYTES) {
                throw new IOException("Die Modell-Datei ist größer als das erlaubte Download-Limit.");
            }
            downloadedBytes = 0L;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Modell-Download wurde unterbrochen.");
                    }
                    downloadedBytes += read;
                    if (downloadedBytes > MAX_MODEL_BYTES) {
                        throw new IOException("Die Modell-Datei überschreitet das Download-Limit.");
                    }
                    output.write(buffer, 0, read);
                    if (downloadedBytes % (8L * BUFFER_SIZE) < BUFFER_SIZE) {
                        status = expectedBytes > 0
                                ? "Modell-Download " + Math.min(100L, downloadedBytes * 100L / expectedBytes) + "%"
                                : "Modell-Download · " + (downloadedBytes / (1024L * 1024L)) + " MiB";
                    }
                }
            }
            if (downloadedBytes < MIN_MODEL_BYTES) {
                throw new IOException("Die heruntergeladene Datei ist ungewöhnlich klein und wird nicht als Modell akzeptiert.");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (connection != null) connection.disconnect();
            Files.deleteIfExists(temporary);
        }
    }

    private static void deliver(Consumer<Result> callback, Result result) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) callback.accept(result);
        else client.execute(() -> callback.accept(result));
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() > 240 ? message.substring(0, 237) + "..." : message;
    }

    /** Frees native llama.cpp memory after queued inference has finished. */
    public static void shutdown() {
        state = State.STOPPED;
        status = "Lokales Modell wird beendet …";
        WORKER.execute(() -> {
            LlamaModel current;
            synchronized (LOCK) {
                current = model;
                model = null;
                setupRunning = false;
                generationRunning = false;
            }
            if (current != null) {
                try { current.close(); } catch (Throwable ignored) { }
            }
        });
        WORKER.shutdown();
    }
}

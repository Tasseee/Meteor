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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HexFormat;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Embedded local llama.cpp inference. No server, account, API key or cloud inference.
 * The only network operation is an explicit one-time download of the GGUF model file.
 */
public final class LocalAIModelService {
    private static final Object LOCK = new Object();
    // Immutable Hugging Face revision; SHA-256 and byte length are checked before loading.
    private static final URI MODEL_URI = URI.create(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/dd26da440ef0330c47919d1ecae0966d24022222/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true");
    private static final long MODEL_SIZE_BYTES = 1_117_320_736L;
    private static final String MODEL_SHA256 =
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e";
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
                Path directory = modelDirectory();
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Der Modellordner darf kein symbolischer Link sein.");
                }
                Files.createDirectories(directory);
                Path target = modelFile();
                if (Files.isSymbolicLink(target)) {
                    throw new IOException("Die lokale Modell-Datei darf kein symbolischer Link sein.");
                }
                if (!hasExpectedModelContent(target)) {
                    Files.deleteIfExists(target);
                    downloadModel(target);
                }
                state = State.LOADING;
                status = "Qwen-Modell wird in den Arbeitsspeicher geladen …";
                int availableProcessors = Runtime.getRuntime().availableProcessors();
                int inferenceThreads = Math.max(1, Math.min(6, availableProcessors - 1));
                ModelParameters parameters = new ModelParameters()
                        .setModel(target.toAbsolutePath().toString())
                        .setThreads(inferenceThreads)
                        .setThreadsBatch(Math.max(1, Math.min(4, inferenceThreads)))
                        .setCtxSize(8_192)
                        .setPredict(1_024);
                LlamaModel loaded = new LlamaModel(parameters);
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

    private static boolean hasExpectedModelContent(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) != MODEL_SIZE_BYTES) return false;
        return MODEL_SHA256.equals(sha256(path));
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 ist auf dieser Java-Laufzeit nicht verfügbar.", impossible);
        }
    }

    private static void downloadModel(Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpURLConnection connection = null;
        try {
            if (Files.isSymbolicLink(temporary)) {
                throw new IOException("Die temporäre Modell-Datei darf kein symbolischer Link sein.");
            }
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Files.delete(temporary);

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
            if (expectedBytes >= 0 && expectedBytes != MODEL_SIZE_BYTES) {
                throw new IOException("Die Modell-Datei hat eine unerwartete Größe und wird abgelehnt.");
            }
            downloadedBytes = 0L;
            MessageDigest digest = newSha256();
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(temporary,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IOException("Modell-Download wurde unterbrochen.");
                    }
                    downloadedBytes += read;
                    if (downloadedBytes > MODEL_SIZE_BYTES) {
                        throw new IOException("Die Modell-Datei überschreitet die erwartete Größe.");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                    if (downloadedBytes % (8L * BUFFER_SIZE) < BUFFER_SIZE) {
                        status = "Modell-Download "
                                + Math.min(100L, downloadedBytes * 100L / MODEL_SIZE_BYTES) + "%";
                    }
                }
            }
            if (downloadedBytes != MODEL_SIZE_BYTES) {
                throw new IOException("Der Modell-Download ist unvollständig; die Datei wird verworfen.");
            }
            String actualHash = HexFormat.of().formatHex(digest.digest());
            if (!MODEL_SHA256.equals(actualHash)) {
                throw new IOException("Die SHA-256-Prüfung des Modells ist fehlgeschlagen; die Datei wird verworfen.");
            }
            if (Files.isSymbolicLink(target)) {
                throw new IOException("Die Modell-Zieldatei darf kein symbolischer Link sein.");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
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

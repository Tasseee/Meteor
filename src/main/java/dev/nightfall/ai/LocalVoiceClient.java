package dev.nightfall.ai;

import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Toggle-controlled local speech recognition. Microphone audio is streamed directly
 * into Vosk and is never written to disk or transmitted to a network service.
 */
public final class LocalVoiceClient {
    private static final Object LOCK = new Object();
    private static final URI MODEL_URI = URI.create(
            "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip");
    private static final String MODEL_SHA256 =
            "b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43";
    private static final long MAX_ARCHIVE_BYTES = 80L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 140L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 8_000;
    private static final int SAMPLE_RATE = 16_000;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            SAMPLE_RATE, 16, 1, true, false);
    private static final int BUFFER_SIZE = 8_192;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Nightfall-Local-Voice");
        thread.setDaemon(true);
        return thread;
    });

    public enum State { MISSING, DOWNLOADING, LOADING, READY, LISTENING, ERROR, STOPPED }

    private static final AtomicBoolean requested = new AtomicBoolean(false);
    private static volatile State state = State.MISSING;
    private static volatile String status = "Lokale Spracherkennung noch nicht eingerichtet";
    private static volatile Model model;
    private static volatile TargetDataLine activeLine;
    private static volatile Consumer<String> transcriptConsumer = ignored -> {};
    private static volatile Consumer<String> statusConsumer = ignored -> {};
    private static volatile boolean startupQueued;
    private static volatile boolean shutdown;

    private LocalVoiceClient() {}

    public static State state() {
        return state;
    }

    public static String status() {
        return status;
    }

    public static boolean isListening() {
        return state == State.LISTENING;
    }

    public static void toggleListening(
            Consumer<String> onTranscript,
            Consumer<String> onStatus) {
        if (isListening() || startupQueued) {
            stopListening();
        } else {
            startListening(onTranscript, onStatus);
        }
    }

    public static void startListening(
            Consumer<String> onTranscript,
            Consumer<String> onStatus) {
        Objects.requireNonNull(onTranscript, "onTranscript");
        Objects.requireNonNull(onStatus, "onStatus");
        synchronized (LOCK) {
            if (shutdown || state == State.STOPPED) {
                deliverStatus(onStatus, "Die lokale Spracherkennung wurde bereits beendet.");
                return;
            }
            if (startupQueued || isListening()) {
                deliverStatus(onStatus, "Das Mikrofon ist bereits aktiv oder wird vorbereitet.");
                return;
            }
            startupQueued = true;
            requested.set(true);
            transcriptConsumer = onTranscript;
            statusConsumer = onStatus;
            state = model == null ? State.DOWNLOADING : State.LOADING;
            status = "Deutsches Sprachmodell wird vorbereitet …";
        }
        deliverStatus(onStatus, status);
        WORKER.execute(LocalVoiceClient::runRecognitionSession);
    }

    public static void stopListening() {
        requested.set(false);
        TargetDataLine line = activeLine;
        if (line != null) {
            try { line.close(); } catch (RuntimeException ignored) {}
        }
        if (state != State.STOPPED) {
            state = model == null ? State.MISSING : State.READY;
            status = "Mikrofon aus · Audio wurde nicht gespeichert";
        }
        deliverStatus(statusConsumer, status);
    }

    private static void runRecognitionSession() {
        TargetDataLine line = null;
        try {
            Model activeModel = model;
            if (activeModel == null) {
                ensureModelInstalled();
                if (!requested.get() || shutdown) return;
                state = State.LOADING;
                status = "Deutsches Sprachmodell wird lokal geladen …";
                deliverStatus(statusConsumer, status);
                activeModel = new Model(modelDirectory().toAbsolutePath().toString());
                model = activeModel;
            }
            if (!requested.get() || shutdown) return;

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                throw new IOException("Dein Mikrofon unterstützt das benötigte 16-kHz-Mono-Audioformat nicht.");
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(AUDIO_FORMAT);
            line.start();
            activeLine = line;
            state = State.LISTENING;
            status = "Mikrofon aktiv · Deutsch · nur lokal";
            deliverStatus(statusConsumer, status);

            try (Recognizer recognizer = new Recognizer(activeModel, SAMPLE_RATE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (requested.get()) {
                    int bytesRead;
                    try {
                        bytesRead = line.read(buffer, 0, buffer.length);
                    } catch (RuntimeException closed) {
                        if (!requested.get()) break;
                        throw closed;
                    }
                    if (bytesRead <= 0) {
                        if (!requested.get()) break;
                        continue;
                    }
                    // Only final Vosk segments are submitted to chat. Partial guesses
                    // are not commands and never trigger game actions.
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        submitRecognizedText(recognizer.getResult());
                    }
                }
                if (requested.get()) submitRecognizedText(recognizer.getFinalResult());
            }
        } catch (Throwable failure) {
            if (requested.get()) {
                state = State.ERROR;
                status = "Lokale Spracherkennung fehlgeschlagen: " + safeMessage(failure);
                deliverStatus(statusConsumer, status);
            }
        } finally {
            requested.set(false);
            activeLine = null;
            if (line != null) {
                try { line.stop(); } catch (RuntimeException ignored) {}
                try { line.close(); } catch (RuntimeException ignored) {}
            }
            synchronized (LOCK) {
                startupQueued = false;
                if (state != State.ERROR && state != State.STOPPED) {
                    state = model == null ? State.MISSING : State.READY;
                    status = "Mikrofon aus · Audio wurde nicht gespeichert";
                    deliverStatus(statusConsumer, status);
                }
            }
        }
    }

    private static void submitRecognizedText(String json) {
        if (json == null || json.isBlank()) return;
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            String text = root.has("text") ? root.get("text").getAsString().trim() : "";
            if (text.isBlank() || text.length() > 2_000) return;
            Consumer<String> callback = transcriptConsumer;
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> callback.accept(text));
        } catch (RuntimeException invalidResult) {
            // Invalid or empty decoder output is ignored, never interpreted as a command.
        }
    }

    private static Path voiceDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("nightfall").resolve("voice");
    }

    private static Path modelDirectory() {
        return voiceDirectory().resolve("vosk-model-small-de-0.15");
    }

    private static void ensureModelInstalled() throws IOException {
        Path directory = voiceDirectory();
        if (Files.isSymbolicLink(directory)) throw new IOException("Der Sprachmodell-Ordner darf kein symbolischer Link sein.");
        Files.createDirectories(directory);

        Path installed = modelDirectory();
        if (Files.isSymbolicLink(installed)) throw new IOException("Das Sprachmodell darf kein symbolischer Link sein.");
        if (isValidModel(installed)) return;
        deleteTree(installed);

        Path archive = directory.resolve("vosk-model-small-de-0.15.zip");
        Path temporaryArchive = directory.resolve("vosk-model-small-de-0.15.zip.part");
        Path temporaryDirectory = directory.resolve("vosk-model-small-de-0.15.extracting");
        if (Files.exists(temporaryArchive, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(temporaryArchive)) throw new IOException("Temporäre Sprachmodell-Datei ist ein symbolischer Link.");
            Files.delete(temporaryArchive);
        }
        if (Files.isSymbolicLink(archive)) throw new IOException("Sprachmodell-Archiv darf kein symbolischer Link sein.");
        if (Files.exists(archive, LinkOption.NOFOLLOW_LINKS)) Files.delete(archive);
        deleteTree(temporaryDirectory);

        downloadModelArchive(temporaryArchive);
        Files.createDirectories(temporaryDirectory);
        try {
            extractModel(temporaryArchive, temporaryDirectory);
            if (!isValidModel(temporaryDirectory)) {
                throw new IOException("Das Sprachmodell-Archiv enthält nicht alle erwarteten Vosk-Dateien.");
            }
            try {
                Files.move(temporaryDirectory, installed, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporaryDirectory, installed);
            }
        } finally {
            Files.deleteIfExists(temporaryArchive);
            deleteTree(temporaryDirectory);
        }
    }

    private static boolean isValidModel(Path directory) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return false;
        return Files.isRegularFile(directory.resolve("am").resolve("final.mdl"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve("conf").resolve("model.conf"), LinkOption.NOFOLLOW_LINKS);
    }

    private static void downloadModelArchive(Path target) throws IOException {
        HttpURLConnection connection = null;
        try {
            status = "Deutsches Sprachmodell wird einmalig heruntergeladen …";
            deliverStatus(statusConsumer, status);
            connection = (HttpURLConnection) MODEL_URI.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(60_000);
            connection.setRequestProperty("User-Agent", "Nightfall-Minecraft-Client/2.0");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Der Sprachmodell-Server hat den Download abgelehnt.");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_ARCHIVE_BYTES) throw new IOException("Das Sprachmodell-Archiv ist zu groß.");
            MessageDigest digest = newSha256();
            long total = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(target,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_ARCHIVE_BYTES) throw new IOException("Das Sprachmodell-Archiv überschreitet das Limit.");
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            if (!MODEL_SHA256.equals(HexFormat.of().formatHex(digest.digest()))) {
                throw new IOException("Die SHA-256-Prüfung des Sprachmodells ist fehlgeschlagen.");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void extractModel(Path archive, Path targetDirectory) throws IOException {
        int entries = 0;
        long expandedBytes = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IOException("Das Sprachmodell-Archiv enthält zu viele Dateien.");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    throw new IOException("Unsicherer Pfad im Sprachmodell-Archiv.");
                }
                String prefix = "vosk-model-small-de-0.15/";
                if (!name.startsWith(prefix)) throw new IOException("Unerwarteter Pfad im Sprachmodell-Archiv.");
                String relativeName = name.substring(prefix.length());
                if (relativeName.isBlank()) continue;
                Path output = targetDirectory.resolve(relativeName).normalize();
                if (!output.startsWith(targetDirectory.normalize()) || relativeName.contains(":")) {
                    throw new IOException("Ungültiger Dateipfad im Sprachmodell-Archiv.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Path parent = output.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    try (OutputStream stream = Files.newOutputStream(output,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        byte[] buffer = new byte[32 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            expandedBytes += read;
                            if (expandedBytes > MAX_EXTRACTED_BYTES) {
                                throw new IOException("Das entpackte Sprachmodell überschreitet das Größenlimit.");
                            }
                            stream.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolische Links werden beim Löschen von Modellordnern abgewiesen.");
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (var children = Files.list(path)) {
                for (Path child : children.toList()) deleteTree(child);
            }
        }
        Files.deleteIfExists(path);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 ist auf dieser Java-Laufzeit nicht verfügbar.", impossible);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) message = failure.getClass().getSimpleName();
        message = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return message.length() <= 200 ? message : message.substring(0, 197) + "...";
    }

    private static void deliverStatus(Consumer<String> consumer, String message) {
        if (consumer == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.isOnThread()) consumer.accept(message);
        else client.execute(() -> consumer.accept(message));
    }

    public static void shutdown() {
        if (shutdown) return;
        shutdown = true;
        stopListening();
        state = State.STOPPED;
        status = "Lokale Spracherkennung beendet";
        WORKER.execute(() -> {
            Model current = model;
            model = null;
            if (current != null) {
                try { current.close(); } catch (RuntimeException ignored) {}
            }
        });
        WORKER.shutdown();
    }
}

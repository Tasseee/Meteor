package dev.nightfall.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local settings for the optional OpenAI-compatible Nightfall AI endpoint. */
public final class AIConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("nightfall").resolve("ai.json");
    private static volatile Settings settings = Settings.defaults();

    private AIConfig() {}

    public record Settings(String baseUrl, String model, String apiKey, boolean shareWorldContext,
                           String audioBaseUrl, String transcriptionModel, String speechModel,
                           String speechVoice, boolean voiceInputEnabled, boolean voiceOutputEnabled) {
        static Settings defaults() {
            return new Settings("https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini", "", false,
                    "https://api.openai.com/v1", "gpt-4o-mini-transcribe", "gpt-4o-mini-tts", "alloy", false, false);
        }
    }

    public static Path file() {
        return FILE;
    }

    public static Settings current() {
        return settings;
    }

    /** Loads settings and creates a safe, empty template on first launch. */
    public static synchronized void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) {
                writeTemplate();
                settings = Settings.defaults();
                return;
            }

            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            Settings defaults = Settings.defaults();
            String baseUrl = readString(root, "baseUrl", defaults.baseUrl()).trim();
            String model = readString(root, "model", defaults.model()).trim();
            String apiKey = readString(root, "apiKey", "").trim();
            boolean shareContext = readBoolean(root, "shareWorldContext", defaults.shareWorldContext());
            String audioBaseUrl = readString(root, "audioBaseUrl", defaults.audioBaseUrl()).trim();
            String transcriptionModel = readString(root, "transcriptionModel", defaults.transcriptionModel()).trim();
            String speechModel = readString(root, "speechModel", defaults.speechModel()).trim();
            String speechVoice = readString(root, "speechVoice", defaults.speechVoice()).trim();
            boolean voiceInput = readBoolean(root, "voiceInputEnabled", defaults.voiceInputEnabled());
            boolean voiceOutput = readBoolean(root, "voiceOutputEnabled", defaults.voiceOutputEnabled());

            if (baseUrl.length() > 512) baseUrl = defaults.baseUrl();
            if (model.isBlank() || model.length() > 128) model = defaults.model();
            if (apiKey.length() > 4096) apiKey = "";
            if (audioBaseUrl.isBlank() || audioBaseUrl.length() > 512) audioBaseUrl = defaults.audioBaseUrl();
            if (transcriptionModel.isBlank() || transcriptionModel.length() > 128) transcriptionModel = defaults.transcriptionModel();
            if (speechModel.isBlank() || speechModel.length() > 128) speechModel = defaults.speechModel();
            if (!speechVoice.matches("[A-Za-z0-9_-]{1,32}")) speechVoice = defaults.speechVoice();
            settings = new Settings(baseUrl, model, apiKey, shareContext, audioBaseUrl,
                    transcriptionModel, speechModel, speechVoice, voiceInput, voiceOutput);
        } catch (Exception e) {
            settings = Settings.defaults();
            Nightfall.LOGGER.warn("Nightfall AI-Konfiguration konnte nicht gelesen werden. Defaults werden verwendet: {}",
                    e.getClass().getSimpleName());
        }
    }

    /** Persist explicit push-to-talk and spoken-reply preferences. */
    public static synchronized Settings updateVoiceSettings(boolean voiceInputEnabled, boolean voiceOutputEnabled) {
        load();
        Settings current = settings;
        Settings updated = new Settings(current.baseUrl(), current.model(), current.apiKey(),
                current.shareWorldContext(), current.audioBaseUrl(), current.transcriptionModel(),
                current.speechModel(), current.speechVoice(), voiceInputEnabled, voiceOutputEnabled);
        try {
            writeSettings(updated);
            settings = updated;
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Nightfall-Stimmeinstellungen konnten nicht gespeichert werden: {}",
                    failure.getClass().getSimpleName());
        }
        return settings;
    }

    /** Persist the player's explicit world-context sharing choice. */
    public static synchronized Settings updateWorldContextSharing(boolean enabled) {
        load();
        Settings current = settings;
        Settings updated = new Settings(current.baseUrl(), current.model(), current.apiKey(),
                enabled, current.audioBaseUrl(), current.transcriptionModel(), current.speechModel(),
                current.speechVoice(), current.voiceInputEnabled(), current.voiceOutputEnabled());
        try {
            writeSettings(updated);
            settings = updated;
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Nightfall-Weltkontext-Einstellung konnte nicht gespeichert werden: {}",
                    failure.getClass().getSimpleName());
        }
        return settings;
    }

    private static void writeSettings(Settings value) throws IOException {
        Files.createDirectories(FILE.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("baseUrl", value.baseUrl());
        root.addProperty("model", value.model());
        root.addProperty("apiKey", value.apiKey());
        root.addProperty("shareWorldContext", value.shareWorldContext());
        root.addProperty("audioBaseUrl", value.audioBaseUrl());
        root.addProperty("transcriptionModel", value.transcriptionModel());
        root.addProperty("speechModel", value.speechModel());
        root.addProperty("speechVoice", value.speechVoice());
        root.addProperty("voiceInputEnabled", value.voiceInputEnabled());
        root.addProperty("voiceOutputEnabled", value.voiceOutputEnabled());
        Files.writeString(FILE, root.toString());
    }

    private static boolean readBoolean(JsonObject root, String key, boolean fallback) {
        try {
            return root.has(key) ? root.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException invalid) {
            return fallback;
        }
    }

    private static String readString(JsonObject root, String key, String fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()
                || !root.getAsJsonPrimitive(key).isString()) return fallback;
        return root.get(key).getAsString();
    }

    private static void writeTemplate() throws IOException {
        JsonObject root = new JsonObject();
        Settings defaults = Settings.defaults();
        root.addProperty("baseUrl", defaults.baseUrl());
        root.addProperty("model", defaults.model());
        root.addProperty("apiKey", "");
        root.addProperty("shareWorldContext", defaults.shareWorldContext());
        root.addProperty("audioBaseUrl", defaults.audioBaseUrl());
        root.addProperty("transcriptionModel", defaults.transcriptionModel());
        root.addProperty("speechModel", defaults.speechModel());
        root.addProperty("speechVoice", defaults.speechVoice());
        root.addProperty("voiceInputEnabled", defaults.voiceInputEnabled());
        root.addProperty("voiceOutputEnabled", defaults.voiceOutputEnabled());
        Files.writeString(FILE, root.toString().replace(",", ",\n  ").replace("{", "{\n  ").replace("}", "\n}"));
    }
}

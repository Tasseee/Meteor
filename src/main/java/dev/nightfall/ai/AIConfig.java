package dev.nightfall.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline-only settings; remote AI and voice provider configuration is intentionally disabled. */
public final class AIConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("nightfall").resolve("ai.json");
    private static volatile Settings settings = Settings.defaults();

    private static final List<String> SUPPORTED_SPEECH_VOICES = List.of(
            "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse"
    );

    private AIConfig() {}

    public record Settings(String baseUrl, String model, String apiKey, boolean shareWorldContext,
                           String audioBaseUrl, String transcriptionModel, String speechModel,
                           String speechVoice, boolean voiceInputEnabled, boolean voiceOutputEnabled) {
        static Settings defaults() {
            // Nightfall is local-only: no remote AI provider, API key, or cloud voice endpoint.
            return new Settings("", "offline", "", false,
                    "", "disabled", "disabled", "alloy", false, false);
        }
    }

    public static Path file() {
        return FILE;
    }

    public static Settings current() {
        return settings;
    }

    /** Enforces offline defaults and removes legacy remote provider credentials/endpoints. */
    public static synchronized void load() {
        // Intentionally overwrite legacy provider settings. Old API keys and remote
        // endpoints are removed from ai.json so the retired cloud clients cannot send data.
        settings = Settings.defaults();
        try {
            Files.createDirectories(FILE.getParent());
            writeSettings(settings);
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Lokale Nightfall-AI-Konfiguration konnte nicht gespeichert werden: {}",
                    failure.getClass().getSimpleName());
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

    /** Persist a voice supported by both the speech and realtime audio endpoints. */
    public static synchronized Settings updateSpeechVoice(String voice) {
        if (voice == null || !SUPPORTED_SPEECH_VOICES.contains(voice.toLowerCase(java.util.Locale.ROOT))) {
            return settings;
        }
        load();
        Settings current = settings;
        String selected = voice.toLowerCase(java.util.Locale.ROOT);
        Settings updated = new Settings(current.baseUrl(), current.model(), current.apiKey(),
                current.shareWorldContext(), current.audioBaseUrl(), current.transcriptionModel(),
                current.speechModel(), selected, current.voiceInputEnabled(), current.voiceOutputEnabled());
        try {
            writeSettings(updated);
            settings = updated;
        } catch (IOException failure) {
            Nightfall.LOGGER.warn("Nightfall-Stimme konnte nicht gespeichert werden: {}",
                    failure.getClass().getSimpleName());
        }
        return settings;
    }

    public static List<String> supportedSpeechVoices() {
        return SUPPORTED_SPEECH_VOICES;
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

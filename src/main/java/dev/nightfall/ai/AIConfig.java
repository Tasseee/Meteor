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

    public record Settings(String baseUrl, String model, String apiKey, boolean shareWorldContext) {
        static Settings defaults() {
            return new Settings("https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini", "", true);
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
            boolean shareContext = root.has("shareWorldContext")
                    ? root.get("shareWorldContext").getAsBoolean() : defaults.shareWorldContext();

            if (baseUrl.length() > 512) baseUrl = defaults.baseUrl();
            if (model.isBlank() || model.length() > 128) model = defaults.model();
            if (apiKey.length() > 4096) apiKey = "";
            settings = new Settings(baseUrl, model, apiKey, shareContext);
        } catch (Exception e) {
            settings = Settings.defaults();
            Nightfall.LOGGER.warn("Nightfall AI-Konfiguration konnte nicht gelesen werden. Defaults werden verwendet: {}",
                    e.getClass().getSimpleName());
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
        Files.writeString(FILE, root.toString().replace(",", ",\n  ").replace("{", "{\n  ").replace("}", "\n}"));
    }
}

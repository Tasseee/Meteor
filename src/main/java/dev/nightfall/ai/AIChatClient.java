package dev.nightfall.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Asynchronous chat-completions client. It never executes Minecraft actions. */
public final class AIChatClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static final String SYSTEM_PROMPT = """
            Du bist Nightfall AI, ein hilfreicher Minecraft-Companion für Minecraft 1.21.11.
            Antworte standardmäßig auf Deutsch und passe dich der Sprache des Spielers an.
            Du bist in dieser Version ausschließlich ein Chat-Assistent: Du besitzt keine Werkzeuge,
            um Blöcke zu platzieren, Spieler anzugreifen, Befehle auszuführen oder die Welt zu verändern.
            Behaupte niemals, du hättest eine Aktion im Spiel ausgeführt. Gib klare, praktische Tipps
            und weise darauf hin, wenn eine Aktion Serverrechte oder bestimmte Spielmodi benötigt.
            """;

    private AIChatClient() {}

    public record Message(String role, String content) {}
    public record Result(boolean success, String text) {}

    public static void request(List<Message> history, String worldContext, Consumer<Result> callback) {
        MinecraftClient client = MinecraftClient.getInstance();
        AIConfig.load();
        AIConfig.Settings settings = AIConfig.current();

        try {
            URI endpoint = URI.create(settings.baseUrl());
            String scheme = endpoint.getScheme();
            if (endpoint.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                complete(client, callback, new Result(false,
                        "Ungültige baseUrl. Bitte eine vollständige HTTP(S)-URL eintragen."));
                return;
            }
            if (settings.apiKey().contains("\r") || settings.apiKey().contains("\n")) {
                complete(client, callback, new Result(false, "Der API-Key enthält ungültige Zeilenumbrüche."));
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("model", settings.model());
            payload.addProperty("stream", false);
            JsonArray messages = new JsonArray();

            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", SYSTEM_PROMPT);
            messages.add(system);

            if (worldContext != null && !worldContext.isBlank()) {
                JsonObject context = new JsonObject();
                context.addProperty("role", "system");
                context.addProperty("content", "Aktueller, vom Spieler freigegebener Weltkontext (nur zur Beratung verwenden):\n"
                        + worldContext);
                messages.add(context);
            }

            for (Message message : history) {
                if (!"user".equals(message.role()) && !"assistant".equals(message.role())) continue;
                JsonObject item = new JsonObject();
                item.addProperty("role", message.role());
                item.addProperty("content", message.content());
                messages.add(item);
            }
            payload.add("messages", messages);

            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
            if (!settings.apiKey().isBlank()) {
                builder.header("Authorization", "Bearer " + settings.apiKey());
            }

            HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, failure) -> {
                        if (failure != null) {
                            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                    ? failure.getCause() : failure;
                            complete(client, callback, new Result(false,
                                    "Verbindungsfehler: " + cause.getClass().getSimpleName()
                                            + ". Prüfe Internetverbindung und baseUrl."));
                            return;
                        }
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            String detail = switch (response.statusCode()) {
                                case 401, 403 -> "API-Key oder Anbieter-Berechtigung prüfen.";
                                case 404 -> "Endpoint oder Modellname prüfen.";
                                case 429 -> "Rate-Limit erreicht. Bitte kurz warten.";
                                default -> "HTTP " + response.statusCode() + " vom KI-Anbieter.";
                            };
                            complete(client, callback, new Result(false, detail));
                            return;
                        }

                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray choices = json.getAsJsonArray("choices");
                            if (choices == null || choices.isEmpty()) throw new IllegalStateException("Keine Antwort vorhanden");
                            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                            JsonElement content = message == null ? null : message.get("content");
                            if (content == null || content.isJsonNull() || !content.isJsonPrimitive()) {
                                throw new IllegalStateException("Antwortformat wird nicht unterstützt");
                            }
                            String answer = content.getAsString().trim();
                            if (answer.isEmpty()) throw new IllegalStateException("Leere Antwort");
                            complete(client, callback, new Result(true, answer));
                        } catch (RuntimeException parseFailure) {
                            complete(client, callback, new Result(false,
                                    "Die Antwort des KI-Anbieters hatte ein unbekanntes Format."));
                        }
                    });
        } catch (RuntimeException invalidRequest) {
            complete(client, callback, new Result(false,
                    "KI-Anfrage konnte nicht vorbereitet werden. Prüfe die AI-Konfiguration."));
        }
    }

    private static void complete(MinecraftClient client, Consumer<Result> callback, Result result) {
        client.execute(() -> callback.accept(result));
    }
}

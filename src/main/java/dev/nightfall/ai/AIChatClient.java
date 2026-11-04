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

/** OpenAI-compatible chat client with a restricted function/tool interface. */
public final class AIChatClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static final String SYSTEM_PROMPT = """
            Du bist Nightfall AI, ein hilfreicher Minecraft-Companion für Minecraft 1.21.11.
            Antworte standardmäßig auf Deutsch und passe dich der Sprache des Spielers an.
            Du kannst die unten bereitgestellten Nightfall-Werkzeuge verwenden, um konkrete Wünsche
            des Spielers zu erfüllen. Rufe ein passendes Werkzeug auf, statt zu behaupten, du hättest
            etwas bereits erledigt. Nightfall zeigt vor jeder Weltänderung oder Datenfreigabe eine
            Bestätigung. Warte auf das Werkzeugergebnis und erkläre danach ehrlich, was geklappt hat.
            Für normale vom Spieler gewünschte Minecraft-/Plugin-Befehle kannst du run_server_command
            verwenden. Erstelle genau einen passenden Befehl, wenn Syntax und Serverfunktion bekannt sind;
            frage nach, wenn ein Server-Plugin-Befehl unklar ist. Behaupte nie, ein Befehl sei erfolgreich,
            bevor der Server dies bestätigt. Keine Serververwaltung, keine Befehlsverkettung und keine
            destruktiven Welteditierbefehle.
            Für "bau", "baue" oder "errichte" kannst du build_starter_shelter verwenden. Das ist derzeit
            der erste feste Bauplan, kein frei erfundener Blockplan. Der Bau setzt echte Blöcke aus der
            Hotbar, prüft Material und Baufläche und kann pausieren, wenn der Spieler näher herangehen muss.
            Erfinde niemals Werkzeuge, Befehle, Koordinaten oder Ergebnisse. Es gibt kein Shell-, Code-
            oder beliebiges Serverkommando-Werkzeug. Wenn keine passende Fähigkeit verfügbar ist,
            erkläre das offen und schlage eine sichere Alternative vor.
            Führe pro Antwort höchstens eine Werkzeugaktion an. Baue oder kämpfe nicht eigenmächtig.
            """;

    private AIChatClient() {}

    public record Message(String role, String content, String toolCallId, String name,
                          String rawAssistantMessage) {
        public Message(String role, String content) {
            this(role, content, null, null, null);
        }

        public static Message assistantToolCall(String rawMessage) {
            return new Message("assistant", null, null, null, rawMessage);
        }

        public static Message toolResult(String toolCallId, String name, String content) {
            return new Message("tool", content, toolCallId, name, null);
        }
    }

    public record ToolCall(String id, String name, String arguments) {}
    public record Result(boolean success, String text, ToolCall toolCall, String rawAssistantMessage) {}

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
                        "Ungültige baseUrl. Bitte eine vollständige HTTP(S)-URL eintragen.", null, null));
                return;
            }
            if (settings.apiKey().contains("\r") || settings.apiKey().contains("\n")) {
                complete(client, callback, new Result(false,
                        "Der API-Key enthält ungültige Zeilenumbrüche.", null, null));
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("model", settings.model());
            payload.addProperty("stream", false);
            payload.addProperty("tool_choice", "auto");
            payload.addProperty("parallel_tool_calls", false);

            JsonArray messages = new JsonArray();
            JsonObject system = new JsonObject();
            system.addProperty("role", "system");
            system.addProperty("content", SYSTEM_PROMPT);
            messages.add(system);

            if (worldContext != null && !worldContext.isBlank()) {
                JsonObject context = new JsonObject();
                context.addProperty("role", "system");
                context.addProperty("content",
                        "Vom Spieler freigegebener Weltkontext (nur zur Beratung verwenden):\n" + worldContext);
                messages.add(context);
            }

            for (Message message : history) {
                if (message.rawAssistantMessage() != null) {
                    messages.add(JsonParser.parseString(message.rawAssistantMessage()));
                    continue;
                }
                if (!"user".equals(message.role()) && !"assistant".equals(message.role())
                        && !"tool".equals(message.role())) continue;
                JsonObject item = new JsonObject();
                item.addProperty("role", message.role());
                if (message.content() != null) item.addProperty("content", message.content());
                if (message.toolCallId() != null) item.addProperty("tool_call_id", message.toolCallId());
                if (message.name() != null) item.addProperty("name", message.name());
                messages.add(item);
            }
            payload.add("messages", messages);
            payload.add("tools", AIActionRegistry.toolDefinitions());

            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
            if (!settings.apiKey().isBlank()) builder.header("Authorization", "Bearer " + settings.apiKey());

            HTTP.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, failure) -> {
                        if (failure != null) {
                            Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                                    ? failure.getCause() : failure;
                            complete(client, callback, new Result(false,
                                    "Verbindungsfehler: " + cause.getClass().getSimpleName()
                                            + ". Prüfe Internetverbindung und baseUrl.", null, null));
                            return;
                        }
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            String detail = switch (response.statusCode()) {
                                case 400 -> "Der KI-Anbieter hat die Anfrage abgelehnt. Prüfe, ob dein Modell Function Calling unterstützt.";
                                case 401, 403 -> "API-Key oder Anbieter-Berechtigung prüfen.";
                                case 404 -> "Endpoint oder Modellname prüfen.";
                                case 429 -> "Rate-Limit erreicht. Bitte kurz warten.";
                                default -> "HTTP " + response.statusCode() + " vom KI-Anbieter.";
                            };
                            complete(client, callback, new Result(false, detail, null, null));
                            return;
                        }

                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            JsonArray choices = json.getAsJsonArray("choices");
                            if (choices == null || choices.isEmpty())
                                throw new IllegalStateException("Keine Antwort vorhanden");
                            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                            if (message == null) throw new IllegalStateException("Nachricht fehlt");

                            JsonArray calls = message.getAsJsonArray("tool_calls");
                            if (calls != null && !calls.isEmpty()) {
                                if (calls.size() != 1) {
                                    complete(client, callback, new Result(false,
                                            "Nightfall unterstützt aktuell nur eine Aktion pro Antwort. Bitte formuliere die Aufgaben einzeln.",
                                            null, null));
                                    return;
                                }
                                JsonObject call = calls.get(0).getAsJsonObject();
                                JsonObject function = call.getAsJsonObject("function");
                                String id = call.get("id").getAsString();
                                String name = function.get("name").getAsString();
                                String arguments = function.has("arguments")
                                        ? function.get("arguments").getAsString() : "{}";
                                complete(client, callback, new Result(true, "", new ToolCall(id, name, arguments),
                                        message.toString()));
                                return;
                            }

                            JsonElement content = message.get("content");
                            if (content == null || content.isJsonNull() || !content.isJsonPrimitive()) {
                                throw new IllegalStateException("Antwortformat wird nicht unterstützt");
                            }
                            String answer = content.getAsString().trim();
                            if (answer.isEmpty()) throw new IllegalStateException("Leere Antwort");
                            complete(client, callback, new Result(true, answer, null, null));
                        } catch (RuntimeException parseFailure) {
                            complete(client, callback, new Result(false,
                                    "Die Antwort des KI-Anbieters hatte ein unbekanntes Format.", null, null));
                        }
                    });
        } catch (RuntimeException invalidRequest) {
            complete(client, callback, new Result(false,
                    "KI-Anfrage konnte nicht vorbereitet werden. Prüfe die AI-Konfiguration.", null, null));
        }
    }

    private static void complete(MinecraftClient client, Consumer<Result> callback, Result result) {
        client.execute(() -> callback.accept(result));
    }
}

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
            Nutze verfügbare Nightfall-Werkzeuge, um konkrete Wünsche zu erfüllen, statt Aktionen zu behaupten.
            Vor jeder Weltänderung oder Datenfreigabe zeigt Nightfall eine Vorschau und verlangt Bestätigung.
            Warte immer auf das Werkzeugergebnis und erkläre ehrlich, was tatsächlich passiert ist.

            Für normale, vom Spieler gewünschte Minecraft- oder Plugin-Befehle verwende run_server_command.
            Es darf immer nur ein Serverbefehl gleichzeitig auf eine Rückmeldung warten. Sende keinen zweiten,
            solange Nightfall noch auf die Antwort oder den 30-Sekunden-Timeout wartet. Erstelle genau einen
            Befehl, wenn Syntax und Zweck bekannt sind; frage nach, wenn sie unklar sind.
            Behaupte nicht, ein Befehl sei erfolgreich, nur weil er gesendet wurde. Servermeldungen sind
            maßgeblich, und uneindeutige Meldungen müssen als unbestätigt gekennzeichnet werden.
            Keine Serververwaltung, keine Befehlsverkettung und keine destruktiven Welteditierbefehle.

            Bei "pausiere den Bau" verwende pause_build; bei "stopp den Bau" verwende stop_build.
            Diese beiden sicheren Steueraktionen dürfen ohne Bestätigung sofort ausgeführt werden.
            Bei "weiterbauen" oder "fortsetzen" verwende resume_build; das erneute Platzieren von Blöcken
            muss Nightfall vorher mit einer Vorschau bestätigen lassen. Bereits gesetzte Blöcke bleiben
            auch beim Stoppen bestehen.
            Wenn der Spieler nach dem aktuellen Baufortschritt fragt, verwende get_build_status.
            Diese reine Statusabfrage benötigt keine Bestätigung und zählt ausschließlich serverseitig
            bestätigte Blockzustände. Bei einer Pause erkläre den Grund; bei fehlender Reichweite bitte
            den Spieler, näher heranzugehen. stop_build bleibt eine bestätigungspflichtige Aktion.

            Für kurze Bewegungswünsche wie „geh vorwärts“ verwende walk_forward mit einer
            begrenzten Dauer von 1 bis 100 Ticks. Das ist nur eine kontrollierte Geradeausbewegung,
            keine Wegfindung: verspreche niemals, Hindernisse zu umgehen oder ein Ziel selbstständig
            zu erreichen. Bei „stopp“ oder „halt“ verwende stop_movement sofort.
            Bei einem ausdrücklichen Ziel mit Koordinaten verwende navigate_to mit x/y/z auf Fußhöhe.
            Die lokale A*-Wegsuche nutzt nur geladene Chunks und hat einen Suchradius von 48 Blöcken.
            Bei weiter entfernten Zielen bitte um Zwischenziele oder verwende mehrere bestätigte Teilstrecken.
            Behaupte niemals, dass ein nicht gefundener oder nicht erreichbarer Weg funktioniert.
            Bei „baue diesen Block ab“ oder „abbaue den Block, den ich anschaue“ verwende mine_looked_block,
            wenn das Fadenkreuz direkt auf dem gewünschten Block liegt. Verwende mine_block mit Koordinaten,
            wenn der Spieler ausdrücklich einen anderen Block bezeichnet. Erfinde niemals Koordinaten.
            Nightfall verwendet das aktuell gehaltene Werkzeug, prüft Reichweite und Sichtlinie und wartet
            auf eine bestätigte Weltänderung. Blöcke mit Inventar/Block-Entity und unzerstörbare Blöcke
            werden abgelehnt. Erfinde keine Koordinaten: frage nach oder bitte den Spieler, den Block anzusehen.
            Bei „iss etwas“ oder „füttere mich“ verwende eat_food. Nur wenn die Hungerleiste nicht voll ist
            und essbares Essen in der Hotbar liegt, startet die normale Minecraft-Nahrungsaufnahme.
            Bei „greif den nächsten Gegner an“ verwende attack_nearest_hostile. Es werden nur feindliche
            HostileEntity-Mobs angegriffen, keine Spieler und keine neutralen Tiere. Der Kampf verfolgt
            ein nahes Ziel mit lokaler Navigation und endet spätestens nach 12 Angriffen oder 45 Sekunden.
            „Stopp“, „halt“ oder „brich alles ab“ soll stop_current_action verwenden, damit Bewegung,
            Abbauen, Essen und Kampf sofort beendet werden.
            Diese Weltaktionen brauchen jeweils eine Vorschau und Bestätigung. Warte auf das Ergebnis.
            Führe Bewegung nur auf ausdrücklichen Wunsch aus und erkläre, dass der Spieler jederzeit
            abbrechen kann. Wenn der Spieler nach einer Farm, Farmmechanik oder Bauanleitung fragt,
            verwende zuerst search_blueprints. Erkläre den Status und die Voraussetzungen des gefundenen
            Eintrags. Nur Einträge mit automationStatus=SUPPORTED_TEMPLATE und einem ausdrücklich
            genannten Build-Werkzeug dürfen automatisch als Nightfall-Vorlage angefordert werden.
            GUIDE_ONLY-Einträge sind Wissen und Anleitungen, keine ausführbaren Baupläne: Verwandle sie
            nicht in erfundene Blocklisten und behaupte nicht, komplexe Farmmechanik bauen zu können.
            Erfinde keine bereits vorhandenen Fähigkeiten.

            Für einfache Bauwünsche verwende build_structure:
            starter_shelter (54 Blöcke), large_house (130 Blöcke), watchtower (74 Blöcke).
            Wenn der Spieler Material nennt, übergib seine Minecraft-Item-ID oder den Blocknamen als
            optionales Feld material. Verwende nur Material, das Nightfall in der Hotbar bestätigt.

            Wenn Material oder Hotbar-Bestand für einen Bauplan unbekannt sind, kannst du zuerst
            get_inventory anfordern. Das Werkzeug benötigt eine eigene Bestätigung und liefert exakte
            Item-IDs; verwende zum Bauen nur dort als Hotbar gelistete Materialien. Das restliche Inventar
            kann der Spieler vor dem Bau erst in die Hotbar legen.
            Für andere ausdrücklich gewünschte Gebäude verwende build_custom_structure.
            Erzeuge einen konkreten, kompakten Blockplan mit höchstens 200 Blöcken, Koordinaten als
            nichtnegative ganzzahlige Offsets in X/Z 0–15 und Y 0–10. Jeder Block muss über eine
            Seitenfläche mit einem anderen Planblock verbunden sein oder direkt an einem bestehenden
            festen Block anliegen; es darf keine schwebenden oder getrennten Teile geben.
            Plane zuerst Fundament/Boden, dann Stützen/Wände, zum Schluss Dach und Details. Du kannst
            pro Block optional ein material-Feld mit einer Minecraft-Item-ID oder einem Blocknamen setzen,
            wenn ein Material für diesen Teil sinnvoll ist. Verwende keine Materialnamen, deren Verfügbarkeit
            du nicht kennst. Nightfall prüft die verfügbaren Mengen je Material und bestätigt den Plan,
            bevor ein Block gesetzt wird. Erfinde keine Werkzeuge oder nicht vorhandenen Weltkoordinaten.

            Erfinde niemals Werkzeuge, Befehle, Koordinaten oder Ergebnisse. Es gibt keine Shell- oder
            Codeausführung und keinen uneingeschränkten Zugriff auf die Serverkonsole. Wenn keine passende Fähigkeit verfügbar ist,
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

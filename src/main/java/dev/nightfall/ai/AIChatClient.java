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
            Sei ein freundlicher, lockerer Minecraft-Spielpartner: antworte natürlich und so, dass man dich gern
            auch laut hört. Bei normalem Smalltalk darfst du einfach plaudern, nach dem Spielziel fragen oder
            kleine gemeinsame Herausforderungen vorschlagen. Behaupte nie, ein Mensch zu sein.
            Nightfall steuert den aktuellen Spieler-Avatar über normale Minecraft-Aktionen; es ist kein
            separat gespawnter NPC und kann nicht als zweiter Charakter neben dem Spieler herumlaufen.
            Spiele mit dem Spieler, indem du gewünschte Bewegungs-, Abbau-, Kampf-, Sammel-, Crafting- und
            Bauaufgaben mit den freigegebenen Werkzeugen übernimmst. Starte Weltaktionen niemals ungefragt.
            Nutze verfügbare Nightfall-Werkzeuge, um konkrete Wünsche zu erfüllen, statt Aktionen zu behaupten.
            Nightfall kann auch sein eigenes ClickGUI-Menü steuern: Bei „was kann ich einstellen?“ oder
            „zeige mir die Nightfall-Einstellungen“ zuerst get_module_settings verwenden. Bei „mach Modul X an/aus“
            set_module_state verwenden. Für konkrete Optionen wie Reichweite, Geschwindigkeit, Farben oder Modi
            erst get_module_settings aufrufen, den exakten Modul- und Einstellungsnamen sowie erlaubte Werte lesen,
            dann set_module_setting mit dem gewünschten Wert verwenden. Diese Änderung wird vor dem Speichern
            angezeigt und benötigt die Bestätigung des Spielers. Niemals Einstellungsnamen oder Werte erfinden.
            open_nightfall_menu öffnet auf ausdrücklichen Wunsch das Nightfall-Menü; es ändert selbst nichts.
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
            Bei "weiterbauen" oder "fortsetzen" verwende resume_build für einen laufenden, manuell pausierten Bau. Wenn Minecraft neu gestartet wurde oder der Spieler erneut verbunden ist, verwende resume_saved_build, sofern Nightfall einen passenden lokal gespeicherten Bauplan gefunden hat. Das erneute Platzieren muss Nightfall vorher mit einer Vorschau bestätigen lassen. Bereits gesetzte Blöcke bleiben
            auch beim Stoppen bestehen.
            Wenn der Spieler nach dem aktuellen Baufortschritt fragt, verwende get_build_status.
            Diese reine Statusabfrage benötigt keine Bestätigung und zählt ausschließlich serverseitig
            bestätigte Blockzustände. Bei einer Pause erkläre den Grund; bei fehlender Reichweite bitte
            den Spieler, näher heranzugehen. stop_build bleibt eine bestätigungspflichtige Aktion.

            Für kurze Bewegungswünsche wie „geh vorwärts“ verwende walk_forward mit einer
            begrenzten Dauer von 1 bis 100 Ticks. Das ist nur eine kontrollierte Geradeausbewegung,
            keine Wegfindung: verspreche niemals, Hindernisse zu umgehen oder ein Ziel selbstständig
            zu erreichen. Bei „stopp“ oder „halt“ verwende stop_movement sofort.
            Wenn der Spieler möchte, dass Nightfall selbstständig losläuft und Strukturen auf einem
            Multiplayer-Server ohne /locate oder besondere Rechte sucht, verwende search_structures.
            Die Suche läuft in einer begrenzten Quadratspirale über sichere A*-Wegabschnitte und prüft
            ausschließlich bereits geladene Chunks auf typische Blockmuster. Unterstützte Heuristiken:
            Ancient City, Trial Chambers, Nether Fortress, Bastion und Stronghold; "any" prüft alle.
            Das ist eine Mustererkennung und kein sicherer Strukturbeweis: melde einen Treffer ausdrücklich
            als möglichen Hinweis mit ungefähren Spielerkoordinaten und bitte um Prüfung vor Ort.
            Behaupte niemals, Dörfer, Mansions oder andere nicht unterstützte Strukturen erkennen zu können.
            Die Suche endet spätestens nach fünf Minuten, bei Gefahr, fehlendem sicheren Weg oder auf "Stopp".
            Für eine ausdrücklich gewünschte Serverbefehls-Suche (wenn der Spieler /locate verlangt und der
            Server es erlaubt) darf locate_structure verwendet werden. Verwechsle diese beiden Sucharten nicht.
            Der Spieler kann jederzeit „Stopp“ sagen.
            Bei einem ausdrücklichen Ziel mit Koordinaten verwende navigate_to mit x/y/z auf Fußhöhe.
            Die lokale A*-Wegsuche nutzt nur geladene Chunks und durchsucht pro Abschnitt maximal 48 Blöcke.
            Nightfall plant 32-Block-Abschnitte automatisch neu, sobald der aktuelle Abschnitt erreicht ist.
            So sind längere Strecken möglich, sofern die nächsten Chunks geladen werden und sichere Wege existieren.
            Behaupte niemals, dass ein nicht gefundener oder nicht erreichbarer Weg funktioniert.
            Bei „baue diesen Block ab“ oder „abbaue den Block, den ich anschaue“ verwende mine_looked_block,
            wenn das Fadenkreuz direkt auf dem gewünschten Block liegt. Verwende mine_block mit Koordinaten,
            wenn der Spieler ausdrücklich einen anderen Block bezeichnet. Erfinde niemals Koordinaten.
            Nightfall wählt das geeignetste Werkzeug aus dem gesamten Spielerinventar (bevorzugt ein Werkzeug,
            das die Drops korrekt erntet), holt es bei Bedarf per normalem Inventar-SWAP in die Hotbar,
            prüft Reichweite und Sichtlinie und wartet auf eine bestätigte Weltänderung. Nach dem Abbau
            stellt es die vorherige Hotbar-Belegung wieder her, sofern sie unverändert geblieben ist. Blöcke mit Inventar/Block-Entity und unzerstörbare Blöcke
            werden abgelehnt. Erfinde keine Koordinaten: frage nach oder bitte den Spieler, den Block anzusehen.
            Wenn rohe Erze oder antiker Schrott zu Barren/Netherit-Schrott verarbeitet werden sollen, nutze nach bestätigtem gather_ore-Ergebnis `smelt_item`: raw_iron→iron_ingot, raw_gold→gold_ingot, raw_copper→copper_ingot, ancient_debris→netherite_scrap. count ist die Anzahl der Rohmaterialien in dieser einzelnen Charge (1–16). Der Spieler muss einen leeren normalen Ofen oder Hochofen direkt unter dem Fadenkreuz ansehen. Nightfall braucht bereits Kohle/Holzkohle im Inventar; ein normaler Ofen benötigt eine Brennstoffeinheit je 8 Items, ein Hochofen je 16. Fehlt Brennstoff, kann gather_ore Kohle nur dann sammeln, wenn passende Kohleerze sichtbar/erreichbar sind; Holzkohle kann nicht automatisch hergestellt werden. Bestehende Ofeninhalte werden niemals angerührt. Jede Schmelzcharge braucht eine eigene Bestätigung. Warte auf das bestätigte Schmelzergebnis und behaupte keine Barren, bevor sie im Inventar liegen.
            Bei einem ausdrücklichen Wunsch, Erz oder Erz-Drops zu sammeln, verwende gather_ore und übergib die gewünschte Drop-Item-ID (nicht die Erzblock-ID): minecraft:coal, raw_iron, raw_copper, raw_gold, redstone, lapis_lazuli, diamond, emerald, quartz, gold_nugget oder ancient_debris. count ist die gewünschte Gesamtmenge dieses Drops im Inventar (1–64), nicht die zusätzliche Menge. Die Suche scannt nur geladene Chunks im Umkreis von 16 horizontalen Blöcken, 24 Blöcken nach unten und 12 nach oben. Für Ancient Debris darf Nightfall ausschließlich im Nether einen kurzen horizontalen 1×2-Stollen von höchstens 8 Schritten durch Netherrack, Basalt oder Schwarzstein graben (höchstens 16 Tunnelblöcke), wenn ein sicherer Vollblock-Untergrund vorhanden und in den direkt angrenzenden Zellen keine Flüssigkeit/Lava, kein fallender Block, keine Block-Entity und kein anderer unsicherer Zustand erkannt wird. Es gräbt nicht nach unten, nicht durch unbekannte Blöcke, nicht durch Bedrock und verwendet keine TNT-/Bett-Explosionen. Andere Erze werden nur direkt sichtbar/erreichbar abgebaut. Bei Ancient Debris muss Minecrafts isSuitableFor-Prüfung eine Diamant- oder Netherit-Spitzhacke bestätigen. Der Spieler soll möglichst nahe Y=16 im Nether beginnen; das ist ein Suchhinweis, keine Garantie. Die passende Spitzhacken-Stufe wird über Minecrafts eigene isSuitableFor-Prüfung verlangt: Holz für Kohle/Quarz/Nethergold, Stein für Eisen/Kupfer/Lapis, Eisen für Gold/Redstone/Diamant/Emerald, Diamant für Ancient Debris. Pro Auftrag höchstens 32 Erzblöcke; Fortune kann mehr Drops liefern. Fehlt die korrekte Spitzhacke oder ein erreichbarer Erzblock, melde das und erfinde keine Funde.
                        Bei einem ausdrücklichen Wunsch, Holz zu sammeln oder Stämme zu holen, verwende gather_wood. Frage nach der Holzart, wenn sie nicht klar ist; rate keine Item-ID. Für eine ausdrücklich angefragte oder im Materialmangel gelistete Vanilla-Block-ID aus der sicheren Selbst-Drop-Liste verwende gather_block (z. B. dirt, cobblestone, granite, tuff, sandstone, netherrack oder stone_bricks). Für cobblestone kann das Werkzeug Stein oder Bruchstein abbauen; für cobbled_deepslate Tiefenschiefer oder Bruchtiefenschiefer. Es behauptet niemals, dass Stein ohne Silk Touch als stone droppt. Beide Werkzeuge arbeiten nur in bereits geladenen Chunks im Nahbereich und verwenden count als gewünschte Gesamtmenge dieses Items im Inventar (1–64), nicht als zusätzliche Menge. Pro Auftrag werden höchstens 32 zusätzliche Blöcke abgebaut. gather_block lehnt fallende Blöcke, unbekannte IDs und toolpflichtige Blöcke ohne geeignetes Werkzeug ab; rate keine IDs und warte auf das Werkzeugergebnis.
            Bei „iss etwas“ oder „füttere mich“ verwende eat_food. Nur wenn die Hungerleiste nicht voll ist
            und essbares Essen in der Hotbar liegt, startet die normale Minecraft-Nahrungsaufnahme.
            Bei „greif den nächsten Gegner an“ verwende attack_nearest_hostile. Es werden nur feindliche
            HostileEntity-Mobs angegriffen, keine Spieler und keine neutralen Tiere. Der Kampf verfolgt
            ein nahes Ziel mit lokaler Navigation und endet spätestens nach 12 Angriffen oder 45 Sekunden.
            „Stopp“, „halt“ oder „brich alles ab“ soll stop_current_action verwenden, damit Bewegung,
            Abbauen, Holz-/Rohstoffsammlung, Crafting, Essen und Kampf sofort beendet werden.
            Diese Weltaktionen brauchen jeweils eine Vorschau und Bestätigung. Warte auf das Ergebnis.
            Führe Bewegung nur auf ausdrücklichen Wunsch aus und erkläre, dass der Spieler jederzeit
            abbrechen kann. Wenn der Spieler nach einer Farm, Farmmechanik oder Bauanleitung fragt,
            verwende zuerst search_blueprints. Erkläre den Status und die Voraussetzungen des gefundenen
            Eintrags. Nur Einträge mit automationStatus=SUPPORTED_TEMPLATE und einem ausdrücklich
            genannten Build-Werkzeug dürfen automatisch als Nightfall-Vorlage angefordert werden.
            GUIDE_ONLY-Einträge sind Wissen und Anleitungen, keine ausführbaren Baupläne: Verwandle sie
            nicht in erfundene Blocklisten und behaupte nicht, komplexe Farmmechanik bauen zu können.
            Erfinde keine bereits vorhandenen Fähigkeiten.
            Wenn der Spieler eine heruntergeladene Community-Schematic verwenden will, nutze list_schematics,
            um lokale Dateien und kuratierte Originalquellen anzuzeigen. Nightfall lädt nicht automatisch
            fremde Dateien herunter und umgeht keine Account-/Download-Sperren. Bitte den Spieler, die Datei
            auf der Originalseite herunterzuladen und in config/nightfall/schematics abzulegen.
            Nutze inspect_schematic vor jedem Bau, um Format, Abmessungen, Materialliste und ausgeschlossene
            Blöcke zu prüfen. Nur nach der Vorschau und ausdrücklicher Bestätigung darf build_schematic
            starten. Nightfall übernimmt gespeicherte BlockStates und versucht Ausrichtung/Platzierung mit Minecrafts normaler BlockItem-Logik exakt wiederherzustellen. Block-Entity-Blöcke wie Hopper und Truhen dürfen mit frischen Standarddaten platziert werden; ihre Inventarinhalte und Sonderdaten werden nicht kopiert.
            Container-Inhalte, Entities, Villager und Wasser/Lava werden nicht übernommen. Orientierte Blöcke werden nur gesetzt, wenn Minecrafts normale Platzierlogik den exakten gespeicherten Zustand erzeugen kann. Dynamische Redstone-/Nachbarschaftszustände müssen gesondert geprüft werden. Sage klar, dass es sich um einen automatisch gebauten Teilplan handelt, wenn die Datei manuelle Restblöcke enthält oder Nightfall einen gespeicherten Zustand nicht reproduzieren kann. Behaupte niemals, dass eine unvollständige Farm
            funktionsfähig ist.
\n            Materialmangel bei einer Schematic: Lies die exakten minecraft:-Item-IDs sowie die Mengen benötigt/vorhanden/fehlen. Wenn die fehlende Item-ID selbst in der sicheren gather_block-Liste enthalten ist, verwende gather_block mit item=exakte ID und count=benötigte Gesamtmenge (nicht nur fehlende Menge); es ermittelt den aktuellen Bestand selbst. Für minecraft:cobblestone sind Stein oder Bruchstein gültige Quellen, für minecraft:cobbled_deepslate Tiefenschiefer oder Bruchtiefenschiefer; minecraft:stone selbst ist ohne Silk Touch nicht unterstützt. Wenn z. B. minecraft:oak_planks fehlen, sammle minecraft:oak_log mit gather_wood (count ist Zielbestand der Logs; berechne ihn aus der Rezept-Ausbeute und dem vorhandenen Bestand; bei unbekanntem Log-Bestand zuerst get_inventory verwenden), dann rufe craft_item mit item=minecraft:oak_planks und count=fehlende Bretter auf (count bedeutet zusätzliche Ausgabemenge; Rezept muss im freigeschalteten 2×2-Rezeptbuch existieren). Für andere Holzarten verwende nur eine eindeutig passende Vanilla-Log-ID oder einen passenden Nether-Stem. Warte auf das Ergebnis jeder Aktion und rufe build_schematic danach erneut auf, damit die Vorschau den aktuellen Bestand prüft. Jeder Sammel-, Crafting- und Bauauftrag benötigt eine separate Spielerbestätigung. Wenn Material nicht durch gather_wood/gather_block unterstützt oder nicht craftbar ist, nenne die exakte fehlende ID und bitte um manuelle Beschaffung. Behaupte niemals, ein Material gesammelt oder gecraftet zu haben, ohne ein erfolgreiches Werkzeugergebnis.\n
            Für einfache Bauwünsche verwende build_structure:
            starter_shelter (54 Blöcke), large_house (130 Blöcke), watchtower (74 Blöcke).
            Wenn der Spieler Material nennt, übergib seine Minecraft-Item-ID oder den Blocknamen als
            optionales Feld material. Verwende nur Material, das Nightfall im gesamten Spielerinventar bestätigt.

            Wenn Material oder Inventarbestand für einen Bauplan unbekannt sind, kannst du zuerst
            get_inventory anfordern. Das Werkzeug benötigt eine eigene Bestätigung und liefert exakte
            Item-IDs; verwende zum Bauen alle dort gelisteten Materialien. Nightfall verschiebt benötigte Stacks bei Bedarf
            mit einem normalen Inventar-SWAP in die Hotbar; der Spieler muss die Stacks nicht vorher manuell verschieben.
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

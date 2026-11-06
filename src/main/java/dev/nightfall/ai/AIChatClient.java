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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** OpenAI-compatible chat client with a restricted function/tool interface. */
public final class AIChatClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .build();

    private static final String SYSTEM_PROMPT = """
            Du bist Nightfall AI, der entspannte, aufmerksame Minecraft-Companion des Spielers für Minecraft Java 1.21.11.
            Antworte in der Sprache des Spielers, standardmäßig auf Deutsch. Klinge wie ein lockerer, hilfsbereiter Mitspieler:
            natürlich, warm, direkt und gelegentlich humorvoll – nicht wie ein Handbuch, Kundendienst oder Roboter.
            Greife den Ton des Spielers auf, ohne ihn zu übertreiben. Nutze kurze, natürliche Formulierungen und passende
            kleine Reaktionen. Stelle Rückfragen locker und gezielt, wenn etwas wirklich unklar ist. Vermeide unnötige
            Aufzählungen bei einfachen Gesprächen. Bei komplexen Aufgaben bleibst du strukturiert und verständlich.
            Sei freundlich, aber nicht aufdringlich oder übertrieben begeistert. Erfinde keine persönlichen Erlebnisse,
            Gefühle oder Erinnerungen. Behaupte nicht, ein Mensch zu sein. Sei ehrlich, wenn du etwas nicht weißt.
            Du steuerst den Avatar des Spielers; du bist kein zusätzlicher NPC und kannst nicht separat neben ihm laufen.

            WERKZEUGE
            Die übermittelten Werkzeuge sind passend zur letzten Nachricht gefiltert. Nutze ausschließlich Werkzeuge,
            die in dieser Anfrage tatsächlich vorhanden sind. Erfinde keine Tool-Namen und behaupte keine nicht
            implementierten Fähigkeiten. Lies Beschreibung und Schema jeder Funktion und verwende nur belegte IDs,
            Werte und Koordinaten. Wenn ein nötiges Werkzeug fehlt oder Angaben fehlen, erkläre das oder frage nach.
            Führe pro Antwort höchstens einen Tool-Aufruf aus. Warte auf dessen Ergebnis, bevor du den nächsten
            Schritt auswählst. Behaupte niemals, eine Aktion sei erfolgreich, bevor das Werkzeug dies bestätigt.

            BESTÄTIGUNG UND KONTROLLE
            Aktionen, die die Welt, Bewegung, Kampf, Inventar/Spielerdaten, Serverbefehle, Moduleinstellungen,
            lokale Markierungen oder Baufortschritt verändern bzw. Daten an den KI-Anbieter senden, benötigen die
            vom Client angezeigte Vorschau und ausdrückliche Bestätigung. Starte sie niemals eigenmächtig.
            Führe keine Bestätigung selbst aus und behandle einen Tool-Aufruf nicht als Zustimmung des Spielers.
            Folge der jeweiligen Werkzeugbeschreibung; reine lokale Statusabfragen dürfen nur dann direkt laufen,
            wenn sie als read-only gekennzeichnet sind. Der Client validiert Aktionen und Berechtigungen.
            Bei „Stopp“, „halt“ oder „abbrechen“ verwende stop_current_action bzw. das passende Stopp-Werkzeug.
            Eine Pause ist kein Abschluss. Bereits gesetzte Blöcke bleiben beim Stoppen bestehen.
            Nutze clear_saved_task nur bei einem ausdrücklichen Wunsch, den gespeicherten Sammel- oder
            Navigationsauftrag zu löschen. Zeige die Vorschau und warte auf die Spielerbestätigung.
            Das Werkzeug löscht nur den Aufgaben-Checkpoint der aktuellen Server-/Dimensionskombination;
            es stoppt keine laufende Aktion und löscht keine gespeicherten Baufortschritte.

            WELT- UND MODULBEOBACHTUNGEN
            Nutze get_module_observations für echte Ergebnisse vorhandener Nightfall-Module und
            get_module_settings für tatsächliche Modulnamen, Einstellungen, Werte und erlaubte Modi.
            Bei Fragen zu PlayerESP/Spielern, EntityESP/Mobs, ItemESP, StorageESP, BaseFinder, ChunkESP,
            Intelligence, StaffWatch oder AntiVanishHints lies die passenden Modulbeobachtungen aus.
            Ist ein benötigtes Modul deaktiviert, sage das und frage vor dem Aktivieren mit set_module_state.
            Aktiviere nie mehrere Module ungefragt. Nach dem Aktivieren lies die Beobachtungen erneut aus.
            Für Einstellungsänderungen erst die exakten Werte lesen, dann set_module_setting verwenden.
            Erfinde keine Modul- oder Einstellungsnamen und keine erlaubten Werte.
            Alle ESP-/Scanner-Daten stammen ausschließlich aus bereits geladenen und vom Server an den Client
            gesendeten Chunks/Entities. Nicht geladene Chunks und serverseitig maskierte Daten sind unsichtbar.
            Fehlende Treffer beweisen nichts. BaseFinder-SUS ist eine Heuristik, kein Beweis für eine Spielerbasis.
            StaffWatch/AntiVanishHints liefern lokale Tab-/Entity-Stichproben, keinen Vanish-, Staff- oder
            Rechtebeweis. Zeige nur die vom Spieler gepflegten Beobachtungsnamen.

            FREIGEGEBENER WELTKONTEXT
            Wenn der Spieler das Teilen des Weltkontexts aktiviert hat, enthält die Anfrage eine lokale
            Momentaufnahme: Dimension, Biom an der Spielerposition, Koordinaten und Chunk-Koordinaten,
            Tagesphase, Wetter, Block unter dem Spieler, aktuell anvisierter Block, Lichtlevel, Gesundheit,
            Hunger/Sättigung, Bewegungssituation, Haupt-/Nebenhand und Rüstung inklusive Resthaltbarkeit,
            einen lokalen Ausrüstungs- und Vorratscheck mit Rüstungsslots, kritischer Rüstungshaltbarkeit,
            Haltbarkeit der Haupthand, erkannter Nahrung und Fackelanzahl, Schild, Goldrüstung sowie
            Wassereimer-/Enderperlen-Hinweisen, eine auf höchstens zwölf Einträge begrenzte Zusammenfassung
            der 36 normalen Inventar-/Hotbar-Slots, clientseitig erkannte lebende
            Mobs im Umkreis von 16 Blöcken (höchstens zehn, nach Nähe sortiert) sowie fallengelassene
            Item-Entities im Umkreis von 12 Blöcken (höchstens zehn Itemtypen mit Menge und nächster Distanz).
            Behandle diese Werte als Momentaufnahme und nicht als dauerhaft gültige Tatsachen. Inventarwerte
            sind eine gekürzte Übersicht und kein vollständiges Inventarprotokoll. Die Mob-Liste enthält nur
            lokal erkannte Wesen; eine leere Liste beweist nicht, dass die Umgebung vollständig frei ist.
            Fallengelassene Gegenstände sind nur aktuell sichtbare Item-Entities, keine vollständige
            Ressourcenkarte. Sie können aufgehoben, despawnt oder durch andere Spieler verändert werden.
            Die Werkzeug- und Materialeinschätzung für den anvisierten Block vergleicht die aktuelle Haupthand
            mit allen 36 normalen Inventar-/Hotbar-Slots anhand derselben lokalen Eignungs- und
            Mining-Speed-Auswahl wie der Abbaucontroller. Sie ist read-only: dabei wird kein Slot gewechselt
            und keine Aktion gestartet. „Geeignet“ beschreibt die lokale Vanilla-Werkzeugprüfung, keine
            Garantie für Drops oder Abbauzeit; behaupte keine exakte Dauer. Wenn kein geeignetes Werkzeug
            erkannt wird, weise darauf hin und starte keinen Abbau ohne Vorschau und Bestätigung.
            Frage bei Bedarf nach einer aktuellen, vollständigen Abfrage und nutze dafür das passende Werkzeug;
            beachte dessen Vorschau- und Bestätigungsregeln.
            Der Kontext beschreibt nur die aktuelle Spielerposition und lokal verfügbare Daten. Er enthält
            keine vollständige Karte, keine garantierten Informationen über Nachbar-Chunks, keine versteckten
            Entities und keine serverseitig nicht an den Client gesendeten Daten. Behaupte niemals, solche
            Bereiche gesehen oder geprüft zu haben. Wenn eine Information fehlt, sage klar, dass sie nicht
            im freigegebenen Snapshot enthalten ist.

            LOKALE GEFAHRENEINSCHÄTZUNG
            Wenn der freigegebene Weltkontext eine lokale Gefahreneinschätzung enthält, nutze sie als
            begrenzten Hinweis für deine Beratung. HOCH bedeutet, dass mindestens ein direkt sichtbares
            akutes Signal wie Lava, Brennen oder ein sehr naher feindlicher Mob erkannt wurde.
            ERHÖHT bedeutet, dass mindestens ein Vorsichtssignal vorliegt, zum Beispiel geringe Gesundheit,
            niedriger Hunger, Unterwasserzustand, Dunkelheit in der Nacht oder ein naher feindlicher Mob.
            KEINE AKUTE GEFAHR IN DEN ERFASSTEN DATEN ERKANNT bedeutet ausdrücklich nicht, dass die Umgebung
            sicher ist. Die Einschätzung prüft nur den lokalen Snapshot und kann sich im nächsten Tick ändern.
            Nenne bei HOCH zuerst das erkannte Signal und rate zu einer vorsichtigen Unterbrechung riskanter
            Aktionen. Starte niemals selbstständig Bewegung, Abbau, Kampf oder andere Weltaktionen; dafür
            gelten weiterhin Vorschau und ausdrückliche Bestätigung.
            Unterscheide bestätigte Beobachtungen von Schlussfolgerungen. Erfinde keine Gegner, Gefahren,
            Entfernungen, Luftwerte oder Sicherheit über nicht geladene Bereiche.

            SURVIVAL INTELLIGENCE UND VORBEREITUNG
            Nutze den lokalen Ausrüstungs- und Vorratscheck, wenn der Spieler wissen möchte, ob er für
            Erkundung, Höhlen, Nether oder End vorbereitet ist. Er beschreibt ausschließlich ausgerüstete
            Rüstungsteile und ihre lokal auslesbare Haltbarkeit, den Haltbarkeitsprozentsatz des Gegenstands
            in der Haupthand sowie Nahrung und Fackeln in den 36 normalen Inventar-/Hotbar-Slots.
            Ein Schild wird zusätzlich in den normalen Slots und der Nebenhand gesucht. Goldrüstung bedeutet
            nur, dass ein goldenes Rüstungsteil aktuell getragen wird; daraus folgt nicht, dass Piglins in
            der Nähe sind. Im Nether ist das ein Piglin-Hinweis, keine Aussage über tatsächlich gesehene Mobs.
            Im End wird nur gemeldet, ob Wassereimer und Enderperlen im normalen Inventar erkannt wurden;
            fehlende Gegenstände sind eine mögliche Vorbereitungslücke, keine zwingende Voraussetzung.
            Bei niedriger lokaler Lichtstufe ohne Fackeln kann Licht als Vorbereitung empfohlen werden.
            Ein fehlender Schild ist ein optionaler Verbesserungsvorschlag und kein Beweis für unzureichende
            Ausrüstung. Behandle „VORBEREITUNG VERBESSERN“ und „EINIGE LÜCKEN ERKANNT“ als Hinweise aus
            genau diesen begrenzten Kategorien, nicht als vollständiges Ausrüstungsurteil.
            Wenn der Weltkontext in der aktuellen Anfrage fehlt oder deaktiviert ist, behaupte keine
            Bereitschaftsprüfung und leite nichts aus älteren Chat-Nachrichten ab. Bitte den Spieler,
            den Weltkontext-Schalter „WELTKONTEXT: AN“ für eine aktuelle Prüfung zu aktivieren.
            Der Check ist read-only; führe nie eigenständig Ausrüstungs-, Inventar- oder Weltaktionen aus.

            BEWEGUNG, SUCHE UND LANGAUFTRÄGE
            Nutze list_waypoints, wenn der Spieler nach gespeicherten Wegpunkten, Zielen oder markierten Orten fragt.
            Die Liste ist lokal und auf den aktuellen Server begrenzt; übernimm Dimension und Koordinaten exakt.
            Ein gelisteter Wegpunkt startet niemals automatisch eine Navigation. Nutze navigate_to erst nach
            einem klaren Navigationswunsch und mit den exakten Koordinaten aus der Liste.
            Nutze navigate_to nur mit Koordinaten, die der Spieler genannt oder ein Werkzeug zurückgegeben hat.
            Erfinde nie Positionen oder Fundorte. A*-Navigation verwendet geladene Chunks und sichere Wege und
            kann an Hindernissen, fehlenden Chunks oder fehlendem Fortschritt stoppen. Die automatische
            Blickdrehung ist geglättet; behaupte trotzdem keine perfekte Wegfindung.
            walk_forward bleibt eine kurze Geradeausbewegung, keine Wegfindung. search_structures ist eine
            Muster-Heuristik, kein sicherer Strukturfund. locate_structure/locate_biome nutzen normale
            Serverbefehle; der Server muss sie erlauben. Nightfall teleportiert bei der Suche nicht.
            Verwende für Abbau und Sammlung die passenden Werkzeuge. Wenn der Spieler eine Laufzeit nennt,
            nutze bei gather_wood/gather_block/gather_ore das Feld duration_seconds in Sekunden (maximal 1200)
            und setze ein hohes, aber gültiges Mengenziel, damit die Aktion bis zum Zeitlimit weiterlaufen kann.
            Wenn er eine genaue Menge nennt, verwende count als Gesamtziel im Inventar; kombiniere beides,
            wenn sowohl Menge als auch Zeit gewünscht sind. Für eine ausdrücklich genannte Sammelhöhe nutze
            bei gather_ore target_y. Das ist die gewünschte Fußhöhe, keine Garantie, dass ein sicherer Weg
            dorthin existiert. Die Sammelroutine scannt nur geladene Chunks, wiederholt lokale Scans,
            verfolgt bereits versuchte Ziele und nutzt nach erschöpften lokalen Treffern eine begrenzte
            quadratische Spiralroute um den Startbereich, damit weitere Nachbarchunks geladen werden.
            Sie stoppt bei vollem Inventar, Zeitlimit, unsicheren Wegen oder fehlenden Treffern.
            Maximal 20 Minuten, 2048 Zielblöcke und 64 sichere Spiralabschnitte pro Einzelauftrag.
            Behaupte nie, dass die gesamte Welt oder ungeladene Chunks abgesucht wurden.
            Keine Explosivstoffe, kein Graben nach unten, in Flüssigkeiten oder durch unbekanntes Gelände
            und keine erfundenen Drops.
            Kampfwerkzeuge dürfen ausschließlich nahe vanilla-feindliche Mobs angreifen, niemals Spieler,
            passive oder neutrale Mobs. Die jeweiligen Werkzeuglimits gelten.

            MINECRAFT-WISSEN
            Beantworte normale Wissensfragen über Minecraft direkt, ohne eine Weltaktion zu starten.
            Beziehe dich auf Minecraft Java 1.21.11, wenn der Spieler keine andere Version nennt.
            Für Vanilla Java 1.18+ liegt die höchste natürliche Diamanterz-Dichte ungefähr auf Y=-59;
            Y=-54 kann beim Branch-Mining ein Kompromiss mit weniger Begegnungen an sehr tiefen
            Lavabereichen sein. Ancient Debris hat im Nether seinen Häufigkeitsgipfel um Y=15.
            Kennzeichne Versions-, Server- und Modpack-Abweichungen und gib keine feste Höhe als Garantie aus.

            SERVERBEFEHLE
            Verwende run_server_command nur für einen normalen Befehl, den der Spieler ausdrücklich verlangt.
            Zeige exakt einen Befehl zur Bestätigung; keine Verkettung, keine Serververwaltung und keine
            destruktiven World-Edit-Befehle. Sende keinen zweiten Befehl, solange eine Serverantwort aussteht.
            Serverberechtigungen bleiben maßgeblich. „Gesendet“ bedeutet nicht „erfolgreich“; warte auf die
            Servermeldung und kennzeichne uneindeutige Ergebnisse als unbestätigt.

            BAUEN, SCHEMATICS UND FARMEN
            Bei Fragen zu Farmen/Bauanleitungen zuerst search_blueprints nutzen. Unterscheide klar zwischen
            SUPPORTED_TEMPLATE und GUIDE_ONLY. GUIDE_ONLY ist Wissen/Anleitung und darf niemals in einen
            erfundenen automatischen Bauplan umgewandelt werden.
            Für lokale .schem/.litematic-Dateien zuerst list_schematics und inspect_schematic verwenden.
            Analysiere vor jedem Build die gesamte Schematic-Zusammenfassung: Abmessungen, Regionen,
            Materialliste, unterstützte/nicht unterstützte Blöcke, Entities, Block-Entities, Wasser/Lava,
            Aushub- und Abschnittsvolumen. Berechne die Automatisierungsabdeckung aus den gemeldeten
            Zählwerten. Wenn der Spieler ausdrücklich eine Bereinigung/Reparatur verlangt, nutze
            repair_schematic erst nach Vorschau und Bestätigung. Es erstellt eine neue Sponge-Kopie mit
            unterstützten Blockzuständen; das Original bleibt unverändert. Untersuchte die neue Datei danach
            erneut mit inspect_schematic. Unsupported Blocks werden Luft; Block-Entity-NBT/Inventare,
            Entities und Farmmechaniken werden nicht rekonstruiert. Nenne Risiken und Verbesserungen als
            Vorschläge und behaupte nie, eine Farmfunktion getestet zu haben.
            Für die drei festen Nightfall-Vorlagen (starter_shelter, large_house, watchtower) nutze
             plan_build_materials, wenn der Spieler Materialbedarf, vorhandene Mengen oder Bau-Bereitschaft
             wissen möchte. Die Abfrage zählt nur geeignete feste Full-Block-Items in den 36 normalen
             Inventar-/Hotbar-Slots, zeigt Fehlmengen und Alternativen und ist lokal read-only. Da Inventardaten
             an den konfigurierten KI-Anbieter übermittelt werden, bleibt die Vorschau bestätigungspflichtig.
             Die Abfrage prüft weder Bauplatz noch Untergrund und bereitet keinen Bau vor. Vor dem tatsächlichen
             Vorlagenbau nutze sie zur Bestandsprüfung; build_structure zeigt danach eine eigene Bauvorschau und
             benötigt weiterhin eine getrennte ausdrückliche Bestätigung. Herstellbare Alternativen basieren
             ausschließlich auf tatsächlich im synchronisierten Rezeptbuch sichtbaren Rezepten und den aktuell
             vorhandenen Zutaten. Nenne bei vollständigen Ketten Rezeptschritte, Zutatenverbrauch und ob eine
             3×3-Crafting-Table erforderlich ist. Bei unvollständigen Ketten darfst du nur die ausdrücklich
             ausgewiesenen fehlenden Zutaten samt Mengen und die untersuchte Rezeptvariante nennen; stelle sie
             niemals als ausführbar oder craftbar dar. Die Diagnose ist begrenzt und kann nicht untersuchte
             Rezeptvarianten auslassen. Starte craft_item niemals automatisch aus der Materialanalyse heraus;
             jede Crafting-Kette benötigt ihre eigene Vorschau und Bestätigung. Bei Sammelaufträgen nennt die
             Sammelvorschau Bestand, Zielmenge, Fehlmenge, freien Inventarplatz und lokal gefundene Ziele;
             behaupte keine Ressourcen außerhalb der geladenen Bereiche.
             Große Vorlagen werden abschnittsweise gebaut. Folge dem gespeicherten Anker und dem gemeldeten
            Zugangs-/Fußpunkt; navigiere dorthin, bevor du einen Abschnitt vorbereitest. Baue von unten nach
            oben in aufsteigender part_y-Reihenfolge und nutze get_schematic_progress, um fertige Abschnitte
            nicht zu wiederholen. Jeder Abschnitt benötigt seine eigene Vorschau/Bestätigung. Melde den
            Gesamtbau erst als abgeschlossen, wenn alle Abschnitte bestätigt sind. Danach kannst du
            verify_schematic_section pro gebautem Abschnitt als reine Lesekontrolle nutzen. Trenne dabei
            fehlende/unterschiedliche Blöcke, dynamische Blockstate-Unterschiede und nicht geladene Chunks.
            Diese Prüfung beweist keine Farmfunktion oder Redstone-Zuverlässigkeit.
            Block-Entity-Inhalte/Inventare, Entities, Villager, Lava und fließende Flüssigkeiten werden nicht
            vollständig aus Schematics übernommen. Redstone-Verbindungen und Signalzustände werden von
            Minecraft dynamisch berechnet. Behaupte nie, dass ein Redstone-System, seine Zeiten oder eine
            Farm funktionieren, nur weil Blöcke platziert wurden. In-Game-Prüfung bleibt nötig.
            sort_inventory ordnet normale Gegenstände in den 36 Inventar-/Hotbar-Slots nach Kategorie und
            Item-ID. Stackgrößen und Komponenten bleiben unverändert; kampforientierte Gegenstände bleiben
            in ihren Slots. Rüstung, Offhand, Crafting-Felder und Cursor werden nicht umgeräumt.
            craft_item kann freigeschaltete 2×2- und 3×3-Rezepte als begrenzte Kette planen. Es prüft
            Zwischenprodukte rekursiv, stellt jeden Rezeptschritt einzeln her und wartet auf bestätigte Drops.
            Benötigt die Kette 3×3, muss der Spieler vorher eine Crafting-Table öffnen. Grenzen: 32 Schritte,
            Tiefe 8, 256 Chargen und 5 Minuten. Gesperrte oder nicht im Rezeptbuch vorhandene Rezepte,
            fehlende Zutaten und nicht unterstützte Anzeigen werden abgelehnt. Kampf-/Explosivgegenstände
            bleiben von der KI-Crafting-Automation ausgeschlossen. Ein garantierter Vollautomatik-Farmbau
            ist nicht implementiert.

            GESPRÄCHSSTIL
            Führe normale Gespräche wie ein entspannter Mitspieler: Begrüßungen, Witze, Meinungen,
            Erlebnisse des Spielers und lockere Nachfragen verdienen eine direkte menschlich klingende,
            aber ehrlich als KI erkennbare Antwort – keine automatische Aufgabenliste und keinen Werkzeugaufruf.
            Beziehe kurze Rückfragen wie „und dann?“, „warum?“ oder „mach weiter“ auf den bisherigen Chatkontext,
            solange der Bezug eindeutig ist. Frage nur nach, wenn eine wichtige Information wirklich fehlt.
            Wiederhole nicht bei jeder Nachricht deine Vorstellung oder deine Fähigkeiten. Du musst nicht jede
            Nachricht in eine Minecraft-Aufgabe verwandeln. Bei Minecraft-Fragen hilfst du direkt und verständlich.
            Wenn der Spieler einen Erfolg teilt, reagiere passend und kurz. Wenn etwas schiefgeht, bleib ruhig,
            hilf beim Eingrenzen und gib nicht dem Spieler die Schuld. Verwende höchstens gelegentlich ein Emoji,
            nur wenn es natürlich passt. Nenne den Spieler nicht ungefragt beim Namen.
            Antworte so kurz wie möglich und so ausführlich wie nötig. Erkläre Grenzen offen, trenne Beobachtung
            von Vermutung und fasse Ergebnisse nach jedem Werkzeugaufruf ehrlich zusammen.
            """;

    private AIChatClient() {}

    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_HISTORY_CHARS = 24_000;

    /**
     * Keep complete recent user turns. Starting at a user message preserves
     * assistant tool-call / tool-result pairs required by OpenAI-compatible APIs.
     */
    private static List<Message> compactHistory(List<Message> history) {
        if (history == null || history.isEmpty()) return List.of();

        List<Integer> turnStarts = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            Message message = history.get(i);
            if (message != null && "user".equals(message.role())) turnStarts.add(i);
        }
        if (turnStarts.isEmpty()) return List.of();

        int firstTurn = Math.max(0, turnStarts.size() - MAX_HISTORY_TURNS);
        while (firstTurn < turnStarts.size() - 1
                && historySizeFrom(history, turnStarts.get(firstTurn)) > MAX_HISTORY_CHARS) {
            firstTurn++;
        }
        return new ArrayList<>(history.subList(turnStarts.get(firstTurn), history.size()));
    }

    /** Compact the caller's stored transcript as well as the outgoing request copy. */
    public static void compactHistoryInPlace(List<Message> history) {
        if (history == null || history.isEmpty()) return;
        List<Message> compacted = compactHistory(history);
        if (compacted.size() == history.size()) return;
        history.clear();
        history.addAll(compacted);
    }

    private static long historySizeFrom(List<Message> history, int start) {
        long size = 0;
        for (int i = start; i < history.size(); i++) {
            Message message = history.get(i);
            if (message == null) continue;
            if (message.content() != null) size += message.content().length();
            if (message.rawAssistantMessage() != null) size += message.rawAssistantMessage().length();
            if (message.name() != null) size += message.name().length();
            if (message.toolCallId() != null) size += message.toolCallId().length();
            size += 32;
        }
        return size;
    }

    /**
     * Expose only tools relevant to the latest user request. This cuts repeated
     * schema tokens and gives the model fewer unrelated choices. A compact
     * read-only/stop set remains available for ordinary questions and recovery.
     */
    private static JsonArray toolsFor(List<Message> history) {
        // Keep the intent from the last few user turns so short follow-ups such as
        // "and the next one?" can still use the tools selected for the original task.
        StringBuilder recentUserText = new StringBuilder();
        int includedTurns = 0;
        for (int i = history.size() - 1; i >= 0 && includedTurns < 3; i--) {
            Message message = history.get(i);
            if (message == null || !"user".equals(message.role()) || message.content() == null) continue;
            if (recentUserText.length() > 0) recentUserText.insert(0, " ");
            recentUserText.insert(0, message.content());
            includedTurns++;
        }

        String query = recentUserText.toString().toLowerCase(Locale.ROOT);
        Set<String> selected = new LinkedHashSet<>();

        if (hasAny(query, "status", "position", "koordinat", "gesundheit", "health", "hp",
                "leben", "herz", "heart", "where am i", "wo bin ich", "how much health",
                "my health", "mein leben")) {
            addTools(selected, "get_player_status");
        }
        if (hasAny(query, "inventar", "inventory", "hotbar", "rüstung", "ruestung",
                "armor", "ausrüstung", "ausruestung", "items", "item count", "how many items")) {
            addTools(selected, "get_inventory");
        }
        if (hasAny(query, "stopp", "stop", "halt", "abbrechen", "notfall", "cancel action",
                "stop everything")) {
            addTools(selected, "stop_current_action", "stop_movement", "pause_build", "stop_build");
        }

        if (isClearSavedTaskRequest(query)) {
            addTools(selected, "clear_saved_task");
        }

        if (isResumeRequest(query)) {
            addTools(selected, "resume_saved_task", "resume_saved_build", "resume_build",
                    "get_player_status", "stop_current_action");
        }

        if (hasAny(query, "esp", "spieler", "player", "players", "online player", "mob", "mobs",
                "entity", "entities", "creeper", "zombie", "cow", "sheep", "animal", "animals",
                "item-drop", "dropped", "ground item", "items on ground", "items nearby", "gedroppt",
                "kiste", "truhe", "chest", "container", "hopper", "fass", "barrel", "storage",
                "blockentity", "staffwatch", "staff watch", "vanish", "tab-list", "tabliste",
                "intelligence scanner", "scanner result")) {
            addTools(selected, "get_module_observations");
        }

        boolean baseTopic = hasAny(query, "basis", "base", "versteck", "geheim", "verdächtig",
                "verdachtig", "sus", "chunk", "decoy", "fakebase", "fake base", "fake", "decoy");
        if (baseTopic) {
            addTools(selected, "get_base_findings", "get_module_observations");
            if (hasAny(query, "such", "find a", "find the", "find me", "find base", "find bases",
                    "search for", "search base", "search bases", "look for", "scan for", "scan base",
                    "scan nearby", "scann", "prüf", "pruef", "check for", "locate")) {
                addTools(selected, "search_bases");
            }
            if (hasAny(query, "markier", "mark as", "mark it", "mark this", "set as", "kennzeichn",
                    "create fake", "add fake", "save as fake", "als fake markieren")) {
                addTools(selected, "mark_fake_base");
            }
            if (hasAny(query, "vorlage", "template", "learn", "gelernt", "beispiel", "example")) {
                addTools(selected, "list_fake_base_templates");
            }
        }

        if (hasAny(query, "modul", "module", "setting", "settings", "einstellung", "option",
                "clickgui", "menü", "menu", "reichweite", "range", "geschwindigkeit", "speed",
                "farbe", "color", "colour", "modus", "mode", "keybind", "bind ", "toggle",
                "enable ", "disable ", "turn on", "turn off", "aktivier", "deaktivier",
                "einschalt", "ausschalt", "mach an", "mach aus", "schalte an", "schalte aus")) {
            addTools(selected, "get_module_settings", "set_module_state", "set_module_setting",
                    "open_nightfall_menu", "get_module_observations");
        }

        if (hasAny(query, "bau", "baue", "bauen", "bauplan", "haus", "hütte", "huette", "turm",
                "farm", "schematic", "litematic", ".schem", "blueprint", "vorlage", "reparier",
                "bereinig", "repair schematic", "build", "house", "shelter", "tower", "structure build",
                "bau das", "build this")) {
            addTools(selected, "plan_build_materials", "build_structure", "build_starter_shelter", "build_custom_structure",
                    "search_blueprints", "list_schematics", "inspect_schematic", "build_schematic",
                    "get_schematic_progress", "repair_schematic", "verify_schematic_section",
                    "get_build_status", "resume_saved_build", "resume_build",
                    "pause_build", "stop_build", "navigate_to", "get_inventory", "gather_block",
                    "gather_wood", "gather_ore", "craft_item", "eat_food");
        }
        boolean minecraftKnowledgeQuestion = hasAny(query, "beste höhe", "beste hoehe", "welche höhe", "welche hoehe",
                "auf welcher höhe", "auf welcher hoehe", "wie tief", "wie hoch", "wo findet man", "wo findet sich",
                "best height", "which height", "what y", "what level", "where do i find")
                && !hasAny(query, "mine ", "farme", "sammel", "sammle", "abbau", "abbaue",
                        "abbauen", "minuten", "minute", "for ", "für ", "fuer ");
        if (!minecraftKnowledgeQuestion && hasAny(query, "abbau", "abbaue", "abbauen", "mining", "mine ", "holz", "holzfarm", "erz", "rohstoff",
                "sammel", "sammle", "sammelt", "farm ", "farme", "gather", "collect", "grabe", "dig ", "schürf", "schuerf", "ore",
                "diamond", "diamant", "ancient", "ancent", "debris", "netherite", "schmelz", "smelt",
                "minute", "minuten", "mins", " min", "sekunden", "dauer", "für ", "fuer ",
                "block abbauen", "wood", "log", "stone", "cobblestone")) {
            addTools(selected, "mine_block", "mine_looked_block", "gather_wood", "gather_block",
                    "gather_ore", "smelt_item", "get_inventory", "navigate_to", "stop_current_action");
        }
        if (hasAny(query, "inventar", "inventory") && hasAny(query, "sort", "sortier", "ordne",
                "aufräum", "aufräumen", "aufräumen", "organis", "organize", "tidy")) {
            addTools(selected, "sort_inventory");
        }
        if (hasAny(query, "craft", "crafting", "herstell", "rezept", "recipe",
                "make ", "create ", "mach mir", "stell mir", "craft me")) {
            addTools(selected, "craft_item", "get_inventory");
        }
        if (hasAny(query, "essen", "iss ", "hunger", "hungry", "food", "eat ", "feed",
                "fütter", "fuetter", "i need food")) {
            addTools(selected, "eat_food", "get_inventory", "get_player_status");
        }
        if (hasAny(query, "angreif", "attack", "kämpf", "kaempf", "kampf", "fight", "gegner",
                "feind", "hostile", "beschütz", "beschuetz", "protect me", "defend", "guard")) {
            addTools(selected, "attack_nearest_hostile", "guard_nearby_hostiles",
                    "stop_current_action", "stop_movement", "get_player_status");
        }
        if (hasAny(query, "geh ", "gehe ", "lauf", "walk", "move ", "go to ", "head to ",
                "beweg", "navigate", "navigation", "koordinat", "wegpunkt", "waypoint", "route",
                "teleport", "rtp", "bring me to", "take me to")) {
            addTools(selected, "walk_forward", "navigate_to", "stop_movement",
                    "stop_current_action", "add_waypoint", "list_waypoints", "random_teleport",
                    "resume_saved_build", "pause_build", "resume_build");
        }

        // Structure/biome tools are selected only when the requested target is a
        // supported structure or biome, not for every generic "find/search" phrase.
        if (hasAny(query, "village", "dorf", "stronghold", "bastion", "festung", "fortress",
                "ancient city", "trial chamber", "trial chambers", "mansion", "waldanwesen",
                "dschungel", "jungle", "wüste", "wueste", "desert", "taiga", "sumpf", "swamp",
                "badlands", "cherry grove", "kirschhain", "mushroom fields", "pilzfelder",
                "mangrove", "savanne", "savanna", "ocean biome", "schneeebene", "snowy plains")) {
            addTools(selected, "search_structures", "locate_structure", "locate_biome",
                    "navigate_to", "add_waypoint");
        }
        if (hasAny(query, "befehl", "command", "serverbefehl", "trigger", "/")) {
            addTools(selected, "run_server_command", "get_player_status");
        }
        if (hasAny(query, "rtp", "random teleport", "zufällig teleport", "zufaellig teleport")) {
            addTools(selected, "random_teleport");
        }

        return AIActionRegistry.toolDefinitions(selected);
    }

    static boolean isClearSavedTaskRequest(String query) {
        if (query == null || query.isBlank()) return false;
        String normalized = query.toLowerCase(Locale.ROOT);
        boolean clearIntent = hasAny(normalized, "lösch", "loesch", "entfern", "vergiss",
                "lösche", "loesche", "delete", "clear", "forget", "remove");
        boolean savedTaskReference = hasAny(normalized, "gespeicherte aufgabe",
                "gespeicherten auftrag", "gespeicherter auftrag", "gespeicherten sammelauftrag",
                "gespeicherte sammelaufgabe", "saved task", "saved gathering task",
                "task resume checkpoint");
        return clearIntent && savedTaskReference;
    }

    static boolean isResumeRequest(String query) {
        if (query == null || query.isBlank()) return false;
        String normalized = query.toLowerCase(Locale.ROOT);
        return hasAny(normalized, "fortsetzen", "fortsetze", "setz fort", "setze fort",
                "weiter machen", "weitermachen", "mach weiter", "mach da weiter",
                "weiterführen", "weiterfuehren", "wieder aufnehmen", "wiederaufnehmen",
                "nimm wieder auf", "weiter geht's", "weiter gehts",
                "resume", "continue", "nach reconnect", "nach verbindungsabbruch");
    }

    private static boolean hasAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private static void addTools(Set<String> selected, String... names) {
        for (String name : names) selected.add(name);
    }

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
        // AIChatScreen refreshes the on-disk config once before building this request.
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

            List<Message> recentHistory = compactHistory(history);
            for (Message message : recentHistory) {
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
            JsonArray availableTools = toolsFor(recentHistory);
            if (!availableTools.isEmpty()) {
                payload.add("tools", availableTools);
                payload.addProperty("tool_choice", "auto");
                payload.addProperty("parallel_tool_calls", false);
            }

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

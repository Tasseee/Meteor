# BaseFinder

- **Status:** FakeBase-Frühprüfung, Musterindizes und gebündelte Persistenz ergänzt; Build erfolgreich, In-Game-Test offen
- **Java-Klasse / Anzeigename:** `BaseFinder`
- **Kategorie:** `BASEFINDER`
- **Aktueller Quellcode-SHA:** `4e61aea6f7f878edfcd7fbfab68027d46b06b405`
- **Datenklassen:** `ChunkResult`, interne `Mark`- und `FakePattern`-Datensätze
- **Speicher-Hilfsklasse:** `Storage`

## Zweck und tatsächliches Verhalten

BaseFinder bewertet bereits geladene Chunks im eingestellten Chunk-Radius anhand verschiedener clientseitig sichtbarer Block-, Block-Entity- und Entity-Signale. Die Ergebnisse werden intern als `CLEAN`, `SUS` oder `GONE` geführt. Die sichtbaren Top-Ergebnisse und Markierungen werden zusätzlich nach Fake-Pattern-Zuordnung gefiltert.

Der Client lädt durch diesen Modulcode keine zusätzlichen Chunks. Der Scan-Queue werden nur Chunks hinzugefügt, die der Client bereits geladen hat.

**Hinweis zum Erkennungsverhalten:** Kisten (Chest-Block-Entities) ziehen jetzt mit einem eigenen einstellbaren Gewicht Punkte vom Score ab. Kisten-Chunks können außerdem unabhängig vom Mindestscore in den wiederholten FakePattern-Fingerabdruckvergleich eingehen, sofern mindestens zwei Signaltypen und zwei Fake-Signale vorliegen. Eine Übereinstimmung wird standardmäßig ab drei Chunks als FakePattern bestätigt; mindestens 60 % der gespeicherten Kistenpositionen (mindestens zwei) und mindestens 50 % der ursprünglichen Block-Entity-Signale müssen erhalten sein. Zusätzlich wird ein bereits gelerntes Kisten-Pattern sofort dauerhaft bestätigt, wenn eine passende Kopie beim Scan mehr als 50 horizontale Blöcke vom Spieler entfernt liegt. Danach werden alle bekannten Vorkommen und alle künftigen Chunks mit diesem Pattern als Fake behandelt. Die Bestätigung wird in der server-/dimensionsbezogenen Fake-Pattern-Datei gespeichert. Das ist eine heuristische Bewertung: einzelne zufällig ähnliche Kistenlayouts können falsch eingestuft werden und müssen im Spiel getestet werden.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Range (Chunks) | 32 | 2–48; Scanbereich in Chunk-Distanz |
| Budget ms/Tick | 2,5 | 0,2–5,0 ms; Zeitbudget zwischen Chunk-Scans |
| Rescan (s) | 12 | 2–120 Sekunden |
| Min Score | 6 | 3–100 |
| Min. Signaltypen | 2 | 1–10 |
| Gleiche SUS-Scores auf FakeBase pruefen | an | Boolesch; aktiviert den wiederholten Score-Fingerprintvergleich |
| Min. gleiche SUS-Chunks | 4 | 3–12; erforderliche Anzahl unterschiedlicher Chunks mit gleicher Score- und Signalsignatur |
| Gewicht Storage | 1,0 | 0,1–5,0 |
| Gewicht Kisten-Abzug | 1,5 | 0–5; wird pro erkannter Kiste vom Score abgezogen |
| Gewicht Hopper | 1,5 | 0,1–5,0 |
| Gewicht Furnace | 0,7 | 0,1–5,0 |
| Gewicht Redstone | 1,2 | 0,1–5,0 |
| Gewicht Spawner | 2,0 | 0,1–10,0 |
| Mob-Spawner erkennen | an | Boolesch |
| Mob-Cluster erkennen | an | Boolesch |
| Gewicht Rotated Deepslate | 1,0 | 0–6 |
| Gewicht Lichtquellen | 0,8 | 0–6 |
| Gewicht Redstone-Muster | 1,0 | 0–6 |
| Gewicht Farm-Struktur | 1,0 | 0–6 |
| Gewicht Mob-Cluster | 1,0 | 0–6 |
| Gewicht Lagerzone | 1,5 | 0–5 |
| Gewicht Amethyst-Cluster | 0,25 | 0–2 |
| Min. Amethyst-Cluster | 6 | 1–64 |
| Gewicht Sculk | 1,2 | 0–8 |
| Min. Tunnel-Länge | 6 | 5–12 |
| Gewicht Tunnel | 1,5 | 0–5 |
| Chat-Meldung | an | Boolesch |

## Technische Prüfung

### Registrierung und Lifecycle

- Registrierung erfolgt einmal über `ModuleManager.init()`.
- `onWorldLeave()` und `onDisable()` rufen `clear()` auf.
- `clear()` speichert zuerst Markierungen und Fake-Pattern-Daten für den aktuellen Server-/Dimensionskontext, danach wird der Arbeitsspeicherzustand geleert.
- Beim ersten Tick einer neuen Welt wird der Kontext aus `Storage.server()` und `Storage.dim()` gebildet und passende Datensätze werden geladen.
- Pause-Zustand ist ein Feld des Moduls und wird durch `clearData()` nicht zurückgesetzt.

### Scanablauf und Status

- Queue wird höchstens alle 20 Clientticks neu gefüllt, wenn sie leer ist.
- Kandidaten werden auf bereits geladene Chunks begrenzt und nach Chunk-Distanz sortiert.
- Bereits gescannte Chunks werden abhängig von `Rescan (s)` erst nach Ablauf des Alters erneut berücksichtigt.
- Der Tick-Loop beendet weitere Chunk-Scans, sobald das Zeitbudget erreicht ist. Ein einzelner `scan()`-Aufruf ist jedoch nicht unterbrechbar; ein einzelner Chunk-Scan kann das konfigurierte Budget überschreiten.
- Mob-Cluster-Zählung wird separat alle 40 Ticks (ca. zwei Sekunden) aus aktuell geladenen Nicht-Spieler-LivingEntities aufgebaut.
- Fake-Pattern-Übereinstimmungen werden bei der Ergebnislistenbildung aus `suspects()` und `marks()` ausgeschlossen.
- Kisten (Chest-Block-Entities) werden getrennt von Fässern gezählt und mit `Gewicht Kisten-Abzug` negativ bewertet; Fässer behalten das positive Storage-Gewicht.
- Kisten-Chunks können bei mindestens zwei Signaltypen und zwei Fake-Signalen auch unterhalb des Score-Schwellwerts Fingerprints bilden. Fingerprints vergleichen die Kisten-Geometrie rotations- und offsettolerant sowie die Block-Entity-Zahlen; mindestens 60 % der gespeicherten Kisten (mindestens zwei) und mindestens die Hälfte der ursprünglichen Block-Entity-Signale müssen erhalten sein. Ab drei passenden Chunks wird ein Layout standardmäßig bestätigt.
- **Neue Score-Fingerprint-Prüfung:** Bei SUS-qualifizierten Chunks wird der auf eine Nachkommastelle gerundete Score mit der exakten Zusammensetzung aus Fässern, Kisten, Shulkern, Hoppern, Öfen, Redstone-Block-Entities und Spawnern verglichen. Ein gleicher Score allein reicht nicht. Standardmäßig werden vier unterschiedliche Chunks mit derselben Score-/Signalsignatur benötigt; erst dann werden die betroffenen Treffer als FakeBase eingestuft und gespeichert. Unbestätigte Score-Muster werden weder aus BaseFinder-Listen noch aus StorageESP herausgefiltert. Künftige Treffer eines bestätigten Musters werden ebenfalls ignoriert.
- **Frühprüfung:** Nach dem Lesen der Block-Entities prüft BaseFinder bekannte Kisten-Fingerprints, bevor die Sections und der vollständige Block-/Tunnel-Scan verarbeitet werden. Bestätigte Treffer überspringen dadurch diesen teuren Vollscan.
- **Indizes und Cache:** Bereits bekannte Fake-Chunk-Positionen liegen in vorberechneten Sets; Score-Fingerprints werden über einen zusammengesetzten Schlüssel gefunden; gespeicherte Kistenpositionen werden pro Muster nur einmal geparst.
- **Weniger Schreib-/Scanlast:** FakePattern- und Markierungs-JSON werden gesammelt in 100-Tick-Intervallen (ca. fünf Sekunden) geschrieben statt pro übereinstimmendem Chunk. Der Standard-Rescan wurde von 8 auf 12 Sekunden erhöht.
- FakePattern-Datensätze verwenden Version 4 für Kistenlayouts und Version 5 für Score-Fingerprints; ältere Datensätze behalten ihre bisherigen Felder und werden nicht als Score-Fingerprint behandelt. Kisten-Positionen werden im Speicher geparst gecacht; dieses Cache-Feld wird nicht in JSON gespeichert.

### Persistenz

- Lokale Dateien liegen unter `config/nightfall/`.
- Markierungen werden je Server-/Dimensionskontext in `marks_<server>_<dimension>.json` gespeichert.
- Fake-Pattern-Daten werden getrennt in `fake_patterns_<server>_<dimension>.json` gespeichert.
- Markierungen werden bei Zustandsänderung als dirty markiert und bei passendem Tickintervall, Weltwechsel, Deaktivierung oder Clientende geschrieben.
- Die Dateien werden nicht an GitHub oder einen externen Dienst gesendet.

### Null-, Fehler- und Performancefälle

- Ohne Spieler/Welt wird der Tick-Scan übersprungen.
- Chunkzugriff verwendet nur vorhandene/geladene Chunk-Objekte.
- Lese-/Schreibfehler werden geloggt.
- Ein unbekannter Chunk benötigt weiterhin einen vollständigen Blockscan. Bereits bekannte FakeBase-Kistenlayouts können aber nach der Block-Entity-Prüfung den Vollscan überspringen.
- Fake-Musterabfragen verwenden vorberechnete Chunk-Position- und Score-Indizes; Kisten-Templates werden nur einmal pro Laufzeitinstanz geparst.
- JSON-Schreibvorgänge werden gebündelt; dadurch kann ein Muster bei einem unerwarteten Client-Absturz vor dem nächsten 100-Tick-Schreibintervall noch ungespeichert sein. Weltwechsel und Deaktivierung speichern geänderte Muster sofort.
- Mob-Cluster-Zählung durchläuft die aktuell geladenen Welt-Entities, nicht nur die gerade angezeigten Markierungen.
- Es gibt keine eigenen Hintergrundthreads.

## Behobene Datenhaltungsbefunde

### BF-01 – Markierungs- und Fake-Pattern-Dateien direkt überschrieben

**Behoben:** `0fbc7f6db047ef2a5af19d3b5fc6641f46b3d49e`.

Beide JSON-Dateien werden jetzt zunächst in eine temporäre Datei geschrieben und anschließend per atomarem Move ersetzt. Wenn das Dateisystem atomare Moves nicht unterstützt, wird auf einen normalen Replace-Move zurückgefallen. Dadurch wird das Risiko reduziert, dass ein Abbruch während des Schreibens die vorherige Datei direkt zerstört.

### BF-02 – ein ungültiger Markierungseintrag stoppte das Laden der gesamten Datei

**Behoben im selben Commit.**

Null-Einträge, fehlende/ungültige Statuswerte, CLEAN-Einträge und nicht endliche Scores werden einzeln übersprungen. Negative Zeitstempel werden auf 0 begrenzt; leere oder null Gründe werden ausgelassen. Gültige übrige Einträge können weiterhin geladen werden. Übersprungene Einträge werden gezählt und einmalig protokolliert.

**Verifikation:** Quellcode auf GitHub gespeichert und erneut gelesen. Build und In-Game-Test stehen noch aus.

## Nutzerbeobachtung

- **Quelle:** Nutzerbericht, ungeprüft.
- **Beobachtung:** Bei einer RTP-bezogenen Nutzung seien 122 Basen angezeigt/gezählt worden, obwohl FakeBase ebenfalls vorhanden gewesen sei.
- **Fehlende Belege:** Clientversion, genaue Settings, Testablauf, Bedeutung der Zahl und Screenshot/Log.
- **Einordnung:** Das ist kein reproduzierter Fehler. Keine Änderung an Erkennung, Fake-Pattern-Vergleich oder Server-Schutz.

## Offene Tests

- [ ] Laden/Speichern mit gültiger Markierungsdatei
- [ ] Einzelner null-/ungültiger Eintrag neben gültigen Einträgen
- [ ] Schreib-/Lesefehler und fehlende Verzeichnisse
- [ ] Weltwechsel zwischen zwei Server-/Dimensionskontexten
- [ ] Clientende mit dirty Markierungen
- [ ] Pause aktiv/deaktiviert über Weltwechsel
- [ ] ein einzelner Chunk-Scan über dem eingestellten Zeitbudget
- [ ] reproduzierbarer Test des Nutzerberichts mit Versions-/Settings-Beleg

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Vollständige statische Prüfung der Einstellungen, Queue, Status, Persistenz und Lifecycle; atomare Dateiersetzung und robuste Markierungsvalidierung ergänzt. |
| 2026-11-20 | Kisten separat gezählt und negativ gewichtet; Kisten-Fingerprint-Erkennung für passende Kisten-Chunks unterhalb des Score-Schwellwerts ermöglicht. |
| 2026-11-22 | Wiederholte SUS-Score-Signaturen ergänzt; bekannte Kisten-Fingerprints vor dem vollständigen Blockscan geprüft; Lookups indiziert, Template-Parsing gecacht und JSON-Schreibvorgänge gebündelt. |

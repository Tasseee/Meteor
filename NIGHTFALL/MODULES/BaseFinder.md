# BaseFinder

- **Status:** Statusstatistiken und Scanfehler-Behandlung ergänzt; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `BaseFinder`
- **Kategorie:** `BASEFINDER`
- **Aktueller Quellcode-SHA:** `d71040d061664bc955df031767d2744e4a54dc9a`
- **Datenklassen:** `ChunkResult`, interne `Mark`- und `FakePattern`-Datensätze
- **Speicher-Hilfsklasse:** `Storage`

## Zweck und tatsächliches Verhalten

BaseFinder bewertet bereits geladene Chunks im eingestellten Chunk-Radius anhand verschiedener clientseitig sichtbarer Block-, Block-Entity- und Entity-Signale. Die Ergebnisse werden intern als `CLEAN`, `SUS` oder `GONE` geführt. Die sichtbaren Top-Ergebnisse und Markierungen werden zusätzlich nach Fake-Pattern-Zuordnung gefiltert.

Der Client lädt durch diesen Modulcode keine zusätzlichen Chunks. Der Scan-Queue werden nur Chunks hinzugefügt, die der Client bereits geladen hat.

**Wichtig:** Die folgende Akte dokumentiert den bestehenden Code und seine Grenzen. Die Erkennung versteckter Basen, die Bewertung von Serverdaten und das Umgehen von FakeBase-/Server-Schutz werden nicht verbessert.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Range (Chunks) | 32 | 2–48; Scanbereich in Chunk-Distanz |
| Budget ms/Tick | 2,5 | 0,2–5,0 ms; Zeitbudget zwischen Chunk-Scans |
| Rescan (s) | 8 | 2–120 Sekunden |
| Min Score | 6 | 3–100 |
| Min. Signaltypen | 2 | 1–10 |
| Gewicht Storage | 1,0 | 0,1–5,0 |
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
- Mob-Cluster-Zählung wird separat alle 20 Ticks aus aktuell geladenen Nicht-Spieler-LivingEntities aufgebaut.
- Fake-Pattern-Übereinstimmungen werden bei der Ergebnislistenbildung aus `suspects()` und `marks()` ausgeschlossen. Die interne Scan-/Bewertungslogik bleibt davon getrennt.

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
- Pro Chunk-Scan entstehen temporäre Arrays, Maps und Datenobjekte; es wurde keine Performance-Optimierung am Erkennungsalgorithmus vorgenommen.
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

## Ergänzungen: Status und Datenpflege

- `ScannerStatus` zeigt zusätzlich SUS-Anzahl, SUS-WEG-Anzahl und die Gesamtzahl der erfassten Chunk-Ergebnisse.
- Bei fehlgeschlagenen Einzel-Scans wird der Fehler gezählt und in der Nightfall-Konsole protokolliert. Der Scan-Tick läuft weiter, statt an einem einzelnen fehlerhaften Chunk abzubrechen.
- `Debug` zeigt SUS-/SUS-WEG-Zahlen und die Zahl der fehlgeschlagenen Chunk-Scans.
- Ungültige oder unvollständige gespeicherte Fake-Profile werden beim Laden übersprungen und protokolliert. Die Bewertungs- und Vergleichsregeln wurden dabei nicht geändert.

**Teststatus:** Änderungen statisch geprüft; Build und In-Game-Test stehen noch aus.

## Globale Kelp-Statistik

- `ScannerStatus` zeigt eine zusätzliche Zeile mit der Anzahl ausgewachsener Kelp-Blöcke und der Zahl der Chunks, in denen diese vorkommen.
- Die Summe umfasst nur bereits geladene und vom BaseFinder gescannte Chunks innerhalb des eingestellten `Range (Chunks)`-Radius.
- Beim Neuaufbau der Scan-Queue werden Statistik-Einträge für nicht mehr geladene oder außerhalb des Scanradius liegende Chunks entfernt.
- Die Statistik ist eine reine Anzeige. Sie verändert weder Score noch SUS-/GONE-/CLEAN-Status oder Fake-Pattern-Vergleich.
- Ausgewachsener Kelp ist kein Beweis für eine bestimmte Ladezeit; die Anzeige ist lediglich eine Bestandszählung des aktuell erfassten Bereichs.

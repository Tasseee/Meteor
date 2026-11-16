# LootDropFinder

- **Status:** Code geprüft; In-Game-/Performance-Test offen
- **Java-Klasse / Anzeigename:** `LootDropFinder`
- **Kategorie:** `VISUALS`
- **Aktueller Quellcode-SHA:** `beebaecadf8a2ba76b76b2500c43c90ac531c9a0`

## Zweck und tatsächliches Verhalten

Trotz des Namens sucht das Modul nicht nach Item-Entities oder fallengelassenen Gegenständen. Es scannt geladene Chunk-Sektionen nach ausgewählten Blockzuständen: Emerald-, Diamant- und Eisenblöcken, Heavy Core, Netherite-/Goldblöcken und Gilded Blackstone.

Es sammelt Treffer pro Chunk. Nach dem initialen Baseline-Durchlauf können neu entdeckte Treffer einen Chunk-Hinweis auslösen, wenn mindestens zehn passende Blockpositionen in diesem Chunk gelistet sind. Der Chunk wird dann temporär markiert und optional per Chat/Sound gemeldet.

**Diese Dokumentation beschreibt das vorhandene Verhalten. Die Blockerkennung, Suchreichweite oder Server-Schutzumgehung werden nicht optimiert.**

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Reichweite | 64 Blöcke | 8–256; Schritt 8 |
| Chunk-Markierung (Sek.) | 60 | 5–300; Schritt 5 |
| Emerald-Block | an | Boolesch |
| Diamant-Block | an | Boolesch |
| Eisen-Block | an | Boolesch |
| Heavy Core | an | Boolesch |
| Netherite-/Goldblock | an | Boolesch |
| Gilded Blackstone | an | Boolesch |
| Sound bei Fund | an | Boolesch |
| Nachricht bei Fund | an | Boolesch |

## Technische Prüfung

- **Chunkquelle:** ausschließlich bereits geladene Chunks; der Code lädt keine zusätzlichen Chunks.
- **Arbeitsverteilung:** höchstens zwei Chunk-Scans pro Clienttick; Queue wird in Entfernungssortierung aufgefüllt.
- **Refresh:** ein Chunk wird frühestens nach 100 Ticks erneut in die Scan-Queue aufgenommen; leere Queue wird frühestens nach 20 Ticks neu befüllt.
- **Baseline:** erste Runde erfasst vorhandene Treffer ohne Fundmeldungen. Meldungen gelten für nach der Baseline neu beobachtete Block-IDs.
- **Marker:** laufen nach eingestellter Tickdauer ab.
- **Lifecycle:** sämtliche Maps, Queues und Zähler werden beim Aktivieren, Deaktivieren und Weltwechsel zurückgesetzt.
- **Performance:** zwei komplette Chunk-Sektionen-Scans pro Tick können bei vielen geladenen Chunks spürbare Clientlast verursachen; nicht profiliert.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

### LDF-01 – verwaiste Höhen-Cache-Einträge

**Behoben:** `e0af67c5cc8e87b3d5d52abb9f7528005b6e2dc6`.

Wenn ein Chunk-Marker ablief, wurde er aus `markedChunks` entfernt, der zugehörige Eintrag in `markerHeights` blieb aber erhalten. Die Höhen-Map konnte dadurch über längere Laufzeit wachsen. Beim Ablauf eines Markers wird der passende Höhen-Eintrag jetzt ebenfalls entfernt.

### LDF-02 – deklarierte Scan-Konstanten nutzen

**Behoben:** `00140dde035ec1135071b0ea34b770d209cd63fc`.

Die bereits vorhandenen Konstanten `CHUNKS_PER_TICK` und `CHUNK_REFRESH_TICKS` wurden zuvor nicht verwendet; stattdessen standen dieselben Zahlen als Literale im Code. Der Code nutzt jetzt die Konstanten. Das ändert das Scanverhalten nicht.

### Einschränkungen

- Der Modulname ist missverständlich: Erkennung betrifft Blöcke in geladenen Chunks, keine gedroppten Items.
- Die initiale Runde dient als Baseline und meldet keine bereits vorher vorhandenen Treffer.
- `totalDetections` wird erhöht, aber im HUD nicht verwendet; es ist derzeit überflüssiger Zustand.
- Keine Optimierung des Suchalgorithmus oder der Erkennungsreichweite vorgenommen.

## Offene Tests

- [ ] Initial Scan vs. nachfolgende neue Treffer
- [ ] Markerablauf und Höhen-Cache-Bereinigung
- [ ] Reichweite 8/256
- [ ] Queue mit vielen geladenen Chunks
- [ ] Weltwechsel/Deaktivieren
- [ ] FPS-/Clienttick-Profiling in einer lokalen Testwelt

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Code geprüft; Marker-Höhen-Cache bereinigt und Scan-Konstanten verwendet. |

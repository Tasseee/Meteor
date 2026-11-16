# SpawnerEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `SpawnerEsp`
- **Anzeigename:** `SpawnerESP`
- **Kategorie:** `VISUALS`
- **Modul-SHA:** `98c077c97bb4abaf94f619b76adc0bb407c5a407`
- **Gemeinsame Renderlogik:** `BlockEntityEsp` / `EspBase`

## Zweck und Verhalten

Markiert Spawner-Block-Entities in geladenen Chunks. Die Klasse selbst hat keine zusätzlichen Filter; die Scan-, Distanz- und Renderlogik kommt aus `BlockEntityEsp` und `EspBase`.

## Einstellungen

Geerbte ESP-Einstellungen: Reichweite (vorgesehener Default 128, Maximum 256), Max Objekte (512), Linienstärke, Transparenz, Antialiasing, Distanz-Fade, Box-Stil, Tracer, Name und Distanz.

## Technische Prüfung

- **Scanquelle:** Block-Entities in geladenen Chunks; keine automatische Chunk-Ladung.
- **Intervall:** geerbter Scanpfad alle 11 geeigneten Ticks.
- **Sortierung:** nach Entfernung von der Kameraposition.
- **Lifecycle:** Cache wird bei Deaktivierung/Weltwechsel geleert.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

### SE-01 – geerbter Range-Default

**Behoben im gemeinsamen Code:** `c03f2c5205b1a6bec2c01d23ad48cdc4fe488a8d`. SpawnerESP verwendet jetzt den im Konstruktor angegebenen Default 128 statt Maximalwert 256.

**Verifikation:** Quellcode gespeichert und erneut gelesen; Build-/In-Game-Test offen.

## Offene Tests

- [ ] Spawner in geladenen Chunks
- [ ] Reichweite 128 / Maximum 256
- [ ] Weltwechsel / Chunk-Entladung
- [ ] Max Objekte und Renderleistung

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | SpawnerEsp und geerbten Scanpfad geprüft; Range-Default korrigiert. |

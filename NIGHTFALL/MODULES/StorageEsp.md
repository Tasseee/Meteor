# StorageEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `StorageEsp`
- **Anzeigename:** `StorageESP`
- **Kategorie:** `VISUALS`
- **Modul-SHA:** `534201c15579c57a9a7a40f2a908600452563581`
- **Gemeinsame Renderlogik:** `BlockEntityEsp`, `EspBase`

## Zweck und Verhalten

Markiert ausgewählte Block-Entities in geladenen Chunks: Truhen, Fässer, Shulker-Boxen, Endertruhen, Hopper und Öfen. Es werden Position und Typ markiert; Inhalte werden von dieser Klasse nicht ausgelesen.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Chests | an |
| Barrels | an |
| Shulker | an |
| Ender Chest | aus |
| Hopper | aus |
| Furnace | aus |
| Reichweite (geerbt) | 96 |
| Max Objekte (geerbt) | 512 |
| Box / Linien / Transparenz | geerbte ESP-Einstellungen |

## Technische Prüfung

- **Scanintervall:** geerbte BlockEntityEsp-Suche läuft derzeit alle 11 geeigneten Ticks.
- **Datenquelle:** Block-Entities aus geladenen Chunks; kein Chunk wird durch diese Klasse geladen.
- **Sortierung:** Cache wird anhand der Kameraposition nach Distanz sortiert.
- **Renderprüfung:** vor Zeichnen wird die Position erneut in der Welt aufgelöst und Filterzustand geprüft.
- **Lifecycle:** Cache wird beim Deaktivieren und Weltwechsel geleert.
- **Allokationen:** Scan baut temporäre Sortierlisten auf; Renderliste wird in Feldern gehalten.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

### SE-01 – Standardreichweite aus gemeinsamem Konstruktor war falsch

**Behoben im Code:** `c03f2c5205b1a6bec2c01d23ad48cdc4fe488a8d`.

Der gemeinsame Konstruktor `EspBase` nahm `defRange` entgegen, setzte den Standardwert aber fälschlich auf `maxRange`. Die Einstellung verwendet jetzt den übergebenen Default. StorageESP startet dadurch mit 96 statt 256; maximale Reichweite bleibt 256.

**Verifikation:** Code gespeichert und erneut gelesen; Build- und In-Game-Test offen.

## Offene Tests

- [ ] Alle sechs Block-Entity-Typen
- [ ] Standardreichweite 96 und Maximum 256
- [ ] Weltwechsel / Chunk laden-entladen
- [ ] Sortierung nach Kameraentfernung
- [ ] Max Objekte / Linien / Sichtbarkeit

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | StorageEsp und Shared-Scanpfad geprüft; geerbten Range-Default korrigiert. |

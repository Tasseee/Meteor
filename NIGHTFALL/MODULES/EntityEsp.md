# EntityEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `EntityEsp`
- **Anzeigename:** `EntityESP`
- **Kategorie:** `VISUALS`
- **Modul-SHA:** `602ca828e38119201b76d80f4e3b2ca8922cb614`
- **Gemeinsame Renderlogik:** `EntityScanEsp` / `EspBase`

## Zweck und Verhalten

Markiert geladene lebende Nicht-Spieler-Entities. Feindliche und passive Kategorien können getrennt ein- und ausgeschaltet werden.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Feindlich | an |
| Passiv | aus |
| Health (geerbt) | an |
| Nur sichtbare (geerbt) | aus |
| Reichweite (korrigierter Default) | 128 |
| Max Objekte | 512 |
| Box | 3D |
| Name / Distanz | an |
| Tracer | aus |

## Technische Prüfung

- **Klassifizierung:** prüft Entity-Typ und Klassifizierung hostile/passive; Spieler werden ausgeschlossen.
- **Renderpfad:** geladene Entities werden jedes HUD-Frame durchlaufen; Positionen/Boxen werden mit TickDelta interpoliert.
- **Sortierung:** Renderbuffer wird nach Distanz sortiert, wenn die Anzahl über Max Objekte liegt.
- **Null-/Lifecycle:** Spieler, Welt und Projection müssen verfügbar sein; eigener Zustand/Thread nicht vorhanden.
- **Performance:** pro passender Entity wird ein RenderTarget-Record pro Frame erzeugt; Messung steht aus.

## Befunde und Verbesserungen

### EE-01 – Standardreichweite aus gemeinsamem Konstruktor war falsch

**Behoben im Code:** `c03f2c5205b1a6bec2c01d23ad48cdc4fe488a8d`.

`EspBase` ignorierte den übergebenen Parameter `defRange` und verwendete stets `maxRange`. EntityESP startet jetzt mit dem vorgesehenen Default 128 statt 512; Maximum bleibt 512.

**Verifikation:** Code gespeichert und erneut gelesen; Build-/In-Game-Test offen.

## Offene Tests

- [ ] Feindlich/passiv Filter
- [ ] Health und Nur sichtbare
- [ ] Reichweite 128 / Maximum 512
- [ ] Viele Entities und Renderleistung
- [ ] Welt-/Perspektivwechsel

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | EntityEsp und gemeinsame Entity-Renderlogik geprüft; Range-Default korrigiert. |

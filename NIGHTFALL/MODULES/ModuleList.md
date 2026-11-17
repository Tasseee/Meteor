# ModuleList

- **Status:** Statischer Gesamt-Audit abgeschlossen; HUD-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `ModuleList`
- **Kategorie:** `HUD`
- **Quellcode-SHA:** `9cdb8209c18d7374e9c5f015e6b663fa4b385be8`

## Zweck und Verhalten

Zeigt eine nach Textbreite absteigend sortierte Liste aller aktivierten Module außerhalb der HUD-Kategorie. Der Header zeigt die Anzahl der aktuell aufgeführten Module.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- **Lebenszyklus:** wird im Konstruktor aktiviert.
- **Datenquelle:** `ModuleManager.all()`; nur aktivierte Module mit Kategorie ungleich HUD.
- **Sortierung:** längste Namen zuerst; bei gleicher Breite alphabetisch ohne Beachtung der Groß-/Kleinschreibung.
- **Größe:** Breite passt sich längstem Namen an (min. 62 px); Höhe entspricht 15 px Header plus 12 px je Modul.
- **HUD-Position:** standardmäßig oben rechts. Die Default-X-Position ermittelt jetzt vor dem ersten Render die Breite aus den aktuell aktiven Nicht-HUD-Modulen. Eine gespeicherte Altposition mit Y=112 wird auf Y=4 migriert.
- **Allokationen:** Sortierliste wird als Feld wiederverwendet; Vergleich fragt Textbreiten mehrfach ab.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- **ML-01 – Default-Ausrichtung korrigiert:** Vor dem ersten `draw()` war `w` noch der Initialwert 60; dadurch konnte ein breiterer Modulname die rechts verankerte Liste über den Bildschirmrand hinausschieben. `defaultPos()` berechnet die aktuelle Breite jetzt aus den aktiven Nicht-HUD-Modulen. Commit: `9477a2a7eb519ae9cfd3c6466c3db0d0ad470dc6`.
- Bei sehr vielen aktivierten Modulen kann die Liste über den unteren Bildschirmrand hinausreichen. Eine Maximalhöhe oder Scroll-/Kürzungslogik wäre eine mögliche UI-Verbesserung.
- Der Sortiervergleich berechnet die Schriftbreite mehrfach. Eine Vorberechnung könnte die Arbeit reduzieren, ohne das Verhalten zu ändern; Messung steht aus.

## Offene Tests

- [ ] 0, wenige und viele aktive Module
- [ ] lange Modulnamen
- [ ] unterschiedliche GUI-Skalierungen
- [ ] Position oben rechts und Migration alter Y-Position
- [ ] HUD-Editor und Bildschirmhöhe

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung. |
| 2026-11-17 | Default-X-Position anhand der tatsächlichen aktiven Inhaltsbreite berechnet; Commit `9477a2a7eb519ae9cfd3c6466c3db0d0ad470dc6`. HUD-Test bleibt offen. |

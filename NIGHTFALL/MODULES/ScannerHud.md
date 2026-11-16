# ScannerHud

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `ScannerHud`
- **Anzeigename:** `ScannerStatus`
- **Kategorie:** `HUD`
- **Quellcode-SHA:** `3dda1ef19798ebb22b17d3ffe5e014304d472ba5`
- **Abhängigkeit:** `BaseFinder`

## Zweck und Verhalten

Zeigt den Zustand des BaseFinder an: Pause, letzte Scanzeit, Queuegröße, Fortschrittsbalken, gescannte/zuletzt nachgefüllte Chunks, bis zu fünf Top-SUS-Ergebnisse und die Anzahl der als SUS-WEG markierten Chunks.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- **Lebenszyklus:** wird beim Erstellen aktiviert.
- **BaseFinder aus:** setzt HUD-Breite und -Höhe auf 0.
- **Datenzugriff:** liest Status, Suspects und Marks aus BaseFinder.
- **Position:** Standard (4, 22).
- **Anzeige:** fixe Breite 190 px; Höhe wächst mit bis zu fünf Zeilen plus optional SUS-WEG-Zeile.
- **Nullfälle:** Modulmanager-Registrierung von BaseFinder wird vorausgesetzt.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler im statischen Rendering gefunden.
- Anzeigename `ScannerStatus` unterscheidet sich vom Klassennamen `ScannerHud`; dies ist eine bewusste/kompatibilitätsrelevante Namensdifferenz, die beim Config-Key zu beachten ist.
- `String.format` verwendet die Standard-Locale; Dezimaltrennzeichen können daher je nach System variieren. Wenn ein festes Format gewünscht ist, `Locale.ROOT` verwenden.

## Offene Tests

- [ ] BaseFinder aktiv/pausiert/deaktiviert
- [ ] Queue leer/voll
- [ ] SUS- und SUS-WEG-Zeilen
- [ ] lange Koordinaten/Score-Werte
- [ ] GUI-Skalierung und HUD-Position

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | ScannerHud und BaseFinder-Integration codebasiert geprüft. |

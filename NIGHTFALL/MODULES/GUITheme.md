# GUITheme

- **Status:** Code geprüft; GUI-Test offen
- **Java-Klasse / Anzeigename:** `GUITheme`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `887a300e66aa7727c4ed6e7feafccfc2a3668ab1`

## Zweck und Verhalten

Verwaltet das ClickGUI-Farbschema. Das Modul startet standardmäßig aktiviert, wendet Presets und Design an und setzt beim Deaktivieren das Theme zurück. Beim Ändern einer einzelnen Farbe ruft die GUI `prepareColorEdit` auf, wodurch das Preset auf `Custom` gesetzt wird.

## Einstellungen

| Einstellung | Standard | Optionen / Bedeutung |
|---|---|---|
| Preset | Nightfall | Nightfall, Violet, Crimson, Emerald, Ocean, Mono, Custom |
| Design | Gradient | Gradient, Flat, Glass |
| Akzentfarbe | RGB 3530960 | RGB-Farbe |
| Sekundärfarbe | RGB 6898118 | RGB-Farbe |
| Panel-Farbe | RGB 1315864 | RGB-Farbe |
| Textfarbe | RGB 15263984 | RGB-Farbe |
| Sekundärtext | RGB 10132142 | RGB-Farbe |
| Overlay-Farbe | RGB 526349 | RGB-Farbe |

## Technische Prüfung

- **Lebenszyklus:** Aktivierung wendet Theme an; Deaktivierung ruft `Theme.reset()` auf.
- **Tick:** `applyTheme()` wird bei jedem Clienttick erneut ausgeführt.
- **Preset-Verhalten:** feste Presets überschreiben die sechs Farbwerte; `Custom` lässt sie unverändert.
- **GUI-Integration:** beide GUI-Bildschirme markieren einzelne Farbänderungen als Custom.
- **Null-/Thread-Fälle:** keine eigenen Threads, Listener oder Ressourcen.
- **Performance:** sechs Settings und Theme-Felder werden pro Tick gesetzt; das ist klein, sollte aber bei sehr häufigen GUI-Neuberechnungen nicht unnötig erweitert werden.
- **In-Game:** GUI-Test offen.

## Befunde und Verbesserungen

- Kein eindeutiger Funktionsfehler bei der statischen Prüfung bestätigt.
- Prüfen, dass beim Laden gespeicherter Farben das Preset `Custom` nicht versehentlich zurück auf ein Standard-Preset springt.
- Optional könnte `applyTheme()` nur bei geänderten Werten oder beim Setting-Event statt jeden Tick laufen; ohne Profiling ist keine messbare Belastung belegt.

## Offene Tests

- [ ] Alle sechs festen Presets
- [ ] Custom: jede Farbe einzeln ändern
- [ ] Gradient / Flat / Glass
- [ ] Aktivieren/Deaktivieren und Theme-Reset
- [ ] Config speichern, Client neu starten, Config laden

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | GUITheme und GUI-Farbänderungsintegration codebasiert geprüft. |

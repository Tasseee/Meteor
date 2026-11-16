# DebugHud

- **Status:** Code geprüft; In-Game-/Build-Test offen
- **Java-Klasse:** `DebugHud`
- **Anzeigename im Client:** `Debug`
- **Kategorie:** `HUD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/DebugHud.java`
- **Quellcode-SHA:** `481022c0c16316924f93a49fc2f521a19684d323`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das HUD zeigt FPS, BaseFinder-Scanzeit und -Queue, Ergebnis-/Markierungszahlen, ESP-Objekte des Frames sowie den zentralen Modul-Fehlerzähler.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- **Abhängigkeiten:** ruft bei jedem Draw `ModuleManager.get(BaseFinder.class)` auf. BaseFinder ist in `ModuleManager.init()` registriert; bei fehlerhafter oder unvollständiger Initialisierung würde `get()` eine `IllegalStateException` werfen.
- **Nullfälle:** kein eigener Nullcheck für BaseFinder, daher Abhängigkeit von vollständiger Modulregistrierung.
- **HUD:** dynamische Breite anhand der längsten Zeile; Höhe anhand der Zeilenanzahl.
- **Threading:** keine eigenen Threads; liest die Werte synchron aus BaseFinder und statischen Statusfeldern.
- **Ressourcen/Lifecycle:** keine eigenen Listener oder Ressourcen.
- **Fehlerbehandlung:** kein lokaler catch; zentraler Render-Handler deaktiviert ein Modul bei Render-Exception.
- **Laufzeit/Version:** nicht im Spiel getestet; Build nicht ausgeführt.

## Befunde und Verbesserungen

### Befund DH-01 – Java-Klassenname und Anzeigename unterscheiden sich

Die Klasse heißt `DebugHud`, der Konstruktor registriert aber den sichtbaren Modulnamen `Debug`. Der Modulindex benennt die Java-Klasse. Das ist kein Laufzeitfehler, kann aber bei Suche, Konfiguration und Dokumentation verwirren. Eine Umbenennung könnte gespeicherte Einstellungen/Referenzen beeinträchtigen; daher nicht ohne Migrationsprüfung ändern.

### Verbesserungskandidat DH-02 – Abhängigkeit explizit dokumentieren

Die Anzeige hängt von BaseFinder ab. Die Abhängigkeit ist im Code vorhanden und BaseFinder wird derzeit registriert. Ein kurzer Hinweis in der Modulbeschreibung bzw. ein defensiver Fallback könnte die Diagnose bei künftigen Änderungen erleichtern.

## Nutzer- und In-Game-Tests

Offen: Debug aktivieren, BaseFinder ein-/ausschalten, Werte bei leerer Scan-Queue beobachten, Modul-Fehlerzähler beobachten, HUD-Editor und kleine GUI-Auflösungen.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung in diesem Audit-Eintrag. |

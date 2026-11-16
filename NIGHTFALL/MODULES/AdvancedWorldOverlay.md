# AdvancedWorldOverlay

- **Registrierter Modulname:** `AdvancedWorldOverlay`
- **Prüfstatus:** **Code geprüft; In-Game-/Laufzeittest offen**
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AdvancedWorldOverlay.java`
- **Quellcode-SHA bei Prüfung:** `002631ad49b7326cb2be328c5ac737ad4273d75d`
- **Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`
- **Registrierung:** genau einmal in `ModuleManager.init()`, Kategorie `WORLD`

## Zweck und Verhalten (aus Code abgeleitet)

Das Modul zeichnet ein fest positioniertes HUD links oben (Startposition 8/8). Es zeigt je nach Einstellung Blockkoordinaten, Chunk-/Regionkoordinaten, Dimension, Lichtlevel und Blickrichtung. Der Titel "Nightfall World Overlay" wird immer gezeichnet, solange das Modul aktiviert ist und Spieler sowie Welt vorhanden sind.

**Wichtig:** Das ist eine Codebeschreibung. Das tatsächliche In-Game-Verhalten wurde in dieser Prüfung nicht ausgeführt.

## Einstellungen

Alle Einstellungen sind `BoolSetting` und standardmäßig aktiviert.

| Einstellung | Standard | Verhalten |
|---|---:|---|
| Koordinaten | `true` | Zeigt X/Y/Z der Spieler-Blockposition |
| Chunk und Region | `true` | Zeigt Chunk-X/Z und Region-X/Z |
| Dimension | `true` | Zeigt die Dimensionskennung |
| Lichtlevel | `true` | Zeigt den vom Weltobjekt abgefragten Lichtwert an der Spielerposition |
| Blickrichtung | `true` | Zeigt die Himmelsrichtung in Großbuchstaben |

## Technische Prüfung

| Bereich | Ergebnis | Grundlage / Einschränkung |
|---|---|---|
| Registrierung/Kategorie | **Bestätigt** | Eintrag in `ModuleManager.init()`; Kategorie `WORLD` |
| Einstellungen/Standardwerte | **Bestätigt** | Fünf BoolSettings, alle mit Standardwert `true` |
| HUD-/Statusanzeige | **Code geprüft** | Direkte Textausgabe, Startposition (8, 8), Zeilenabstand 11/12 px |
| Nullfälle | **Code geprüft** | `renderHud` prüft Spieler und Welt auf `null` |
| Aktivieren/Deaktivieren | **Code geprüft** | Gemeinsame Basisklasse ruft keine Modulressourcen auf; Rendering erfolgt nur für aktivierte Module |
| Listener/Ressourcen/Cleanup | **Nicht zutreffend nach sichtbarem Modulcode** | Modul registriert selbst keine Listener und hält keine eigenen Ressourcen |
| Fehlerbehandlung | **Zentral** | Exceptions aus `renderHud` werden vom `ModuleManager` geloggt und das Modul deaktiviert |
| Thread-Sicherheit | **Kein eigener Hintergrundthread** | Rendering greift synchron auf Client-/Weltzustand zu |
| Performance | **Einfacher Renderpfad** | Position/Chunk werden einmal pro Render-Aufruf berechnet; mehrere Strings werden pro Frame erzeugt |
| Versionskompatibilität | **Nicht separat geprüft** | Verwendet Minecraft-intermediäre Methodennamen; Build und In-Game-Test stehen aus |

## Befunde

### Bestätigte Fehler

Bei der reinen Codeprüfung wurde **kein eindeutiger Funktionsfehler bestätigt**. Das ist kein Nachweis, dass das Modul fehlerfrei ist.

### Mögliche Verbesserungen (noch keine Fehler)

- Die fünf Textzeilen und ihre Position könnten bei sehr kleinen GUI-Auflösungen bzw. UI-Skalierung im Spiel geprüft werden.
- Prüfen, ob die Bezeichnung "Lichtlevel" für den konkret abgefragten Minecraft-Lichtwert eindeutig genug ist.
- Die Großschreibung der Blickrichtung könnte bei Bedarf mit `Locale.ROOT` unabhängig von der System-Locale gemacht werden; aktuell ist daraus kein sichtbarer Fehler belegt.

## Nutzererfahrungen und Testberichte

Noch keine In-Game-Testberichte vorhanden.

| Test-ID | Datum | Umgebung | Ergebnis | Beleg |
|---|---|---|---|---|
| – | – | – | Nicht getestet | – |

## Offene Prüfpunkte

- [ ] Im Spiel aktivieren und jede Einstellung einzeln umschalten.
- [ ] Welt betreten/verlassen und Verhalten bei fehlendem Spieler/Welt beobachten.
- [ ] Kleine Auflösung und unterschiedliche GUI-Skalierungen prüfen.
- [ ] Build ausführen; Ergebnis separat protokollieren.

## Änderungsverlauf

| Datum | Commit | Änderung | Nachweis |
|---|---|---|---|
| 2026-11-16 | Nightfall-Audit | Erste codebasierte Prüfung; keine Java-Codeänderung | Quellcode-SHA oben |

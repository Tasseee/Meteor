# Minimap

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `Minimap`
- **Kategorie:** `HUD`
- **Aktueller Quellcode-SHA:** `455652ff508054eaa89f4e25ac048b3d1a408758`
- **Abhängigkeit:** `BaseFinder`

## Zweck und Verhalten

Zeigt ein Chunk-Radar mit SUS-/SUS-WEG-Markierungen aus den bereits vorhandenen BaseFinder-Ergebnissen. Die Karte kann sich mit der Blickrichtung drehen und optionale Chunk-Koordinaten anzeigen. Sie lädt keine Chunks selbst.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Größe | 96 | 50–220; Schritt 2 |
| Reichweite (Chunks) | 8 | 2–24 |
| Transparenz | 0,75 | 0,2–1,0 |
| Rotieren | an | Boolesch |
| Koordinaten | an | Boolesch |

## Technische Prüfung

- **Abhängigkeit:** liest BaseFinder; bei Deaktivierung von BaseFinder wird dessen Scan-Zustand geleert.
- **HUD-Platzierung:** standardmäßig unten rechts; Größe wird dynamisch aus der Einstellung übernommen.
- **Clipping:** setzt einen Scissor-Bereich für die Karte und transformiert die Zellen über Matrix3x2fStack.
- **Nullfälle:** Spieler und Welt müssen vorhanden sein.
- **Performance:** bei Rotation wird ein erweitertes Raster von bis zu 73×73 Zellen bei Reichweite 24 gezeichnet; das ist ein möglicher Renderlastpunkt.
- **Build/In-Game:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### MM-01 – Matrix- und Clip-Bereinigung

**Behoben im Code:** `0764fc3e1c8395666a1bcaf04d68a06a7e283c78`.

Matrix-Pop und das Zurücksetzen des Scissor-Clips liefen zuvor nur nach erfolgreicher Rasterzeichnung. Bei einer Render-Exception konnten beide Renderzustände aktiv bleiben. Die Bereinigung liegt jetzt in `finally`.

**Verifikation:** Code auf GitHub gespeichert und erneut gelesen; Build- und In-Game-Test offen.

### MM-02 – Renderlast bei maximaler Reichweite

Die rotierte Karte zeichnet bei Reichweite 24 bis zu 5.329 Zellen pro Frame. Das sollte mit einem Profiling geprüft werden; eine Reduktion der Zellen ohne Designabgleich wurde nicht vorgenommen.

## Offene Tests

- [ ] Rotation an/aus und vier Blickrichtungen
- [ ] Reichweite 2/24 und Größe 50/220
- [ ] Scissor-/Matrixzustand bei Renderfehler
- [ ] BaseFinder aus/an
- [ ] FPS-Messung bei maximaler Reichweite

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Clip-/Matrix-Bereinigung abgesichert; Tests offen. |

# WorldLyrics

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `WorldLyrics`
- **Kategorie:** `VISUALS`
- **Aktueller Quellcode-SHA:** `e9714d1ce7f40ab1bd5dfc1a96ad2c7633fdf82f`
- **Abhängigkeit:** `SpotifyLyricsHud`, `Projection`

## Zweck und Verhalten

Zeigt Wörter synchron zu den vom SpotifyLyricsHud bereitgestellten Liedtext-Zeilen in der Spielwelt oder auf dem Bildschirm an. Modi: Sky, Floating und Screen-Random. Es lädt selbst keine Liedtexte, sondern verwendet die Daten des SpotifyLyricsHud.

Beim Aktivieren schaltet das Modul SpotifyLyricsHud automatisch ein, falls es ausgeschaltet ist.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Anzeigemodus | Floating | Sky, Floating, Screen-Random |
| Textgröße | 1,25 | 0,75–2,5 |
| Sichtdauer (Sek.) | 4,0 | 1–10 Sekunden |
| Wort für Wort | an | Boolesch |
| Schwebe-Animation | an | Boolesch |
| Neon-Glas-Karten | an | Boolesch |
| Mehrfacher Neon-Glow | an | Boolesch |

## Technische Prüfung

- **Lebenszyklus:** aktive Wörter werden bei Aktivierung, Deaktivierung und Weltwechsel geleert.
- **Datenprüfung:** Spieler/Welt/Projection müssen verfügbar sein; Lyrics-Zeitarray muss zur Zeilenanzahl passen.
- **Begrenzung:** aktive Wortobjekte sind auf 180 begrenzt; abgelaufene Wörter werden entfernt.
- **Zeitbasis:** Animationen und Lebensdauer nutzen `System.currentTimeMillis()`.
- **Occlusion:** Floating-Modus kann pro Wort einen Welt-Raycast ausführen.
- **Matrix:** Wort-Rendering verwendet Push/Transform/Pop ohne `finally`.
- **Codequalität:** lokale Variablen `span` und Parameter `progress` in `spawnLine` werden nicht verwendet.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

### WL-01 – Matrix-Cleanup absichern

**Behoben im Code:** `512acb87a9cbd655b8329819dcbe6a3bab2b56e8`.

Das Text-Rendering in `drawWord` liegt jetzt in einem `try/finally`, sodass `popMatrix()` auch bei einer Render-Exception ausgeführt wird. Build- und In-Game-Test stehen noch aus.

### WL-02 – mögliche Renderlast

Im Floating-Modus kann für jedes aktive Wort pro Frame ein Occlusion-Ray-Cast ausgeführt werden. Bei 180 aktiven Wörtern wären das bis zu 180 Sichtlinienabfragen pro Renderframe. Das ist ein deutlicher Profiling-Kandidat; ohne Messung wird keine konkrete FPS-Einbuße behauptet.

### WL-03 – Trackwechsel-Schlüssel

Der aktuelle Schlüssel besteht aus Zeilenanzahl, erster und letzter Zeile. Unterschiedliche Songs mit identischen Randzeilen und gleicher Zeilenanzahl könnten denselben Schlüssel ergeben. Ein stabiler Track-Identifier aus SpotifyLyricsHud wäre zuverlässiger.

### WL-04 – stille Auto-Aktivierung

Das Einschalten von WorldLyrics aktiviert SpotifyLyricsHud automatisch. Das ist im Modulcode erkennbar, sollte aber in der GUI kurz erklärt werden, weil dadurch ein zusätzlicher OAuth-/Netzwerkprozess starten kann.

## Offene Tests

- [ ] alle drei Modi
- [ ] Songwechsel und Zurückspringen in der Wiedergabe
- [ ] Kamera-/Projection-Verfügbarkeit beim Zeilenwechsel
- [ ] bis zu 180 aktive Wörter und Renderleistung
- [ ] Matrixzustand nach Renderausnahme
- [ ] SpotifyLyricsHud deaktiviert/aktiviert

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | WorldLyrics geprüft; Matrix-Cleanup in `drawWord` abgesichert; Tests offen. |

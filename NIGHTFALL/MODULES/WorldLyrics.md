# WorldLyrics

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `WorldLyrics`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `4f46ff9e6b3dcf529877e5208218806277941054`
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

Wenn das Text-Rendering innerhalb von `drawWord` eine Exception wirft, erreicht der Code möglicherweise `popMatrix()` nicht. Eine `try/finally`-Bereinigung wäre eine sinnvolle defensive Korrektur. Noch nicht umgesetzt.

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
| 2026-11-16 | WorldLyrics anhand der Modulklasse geprüft; keine Java-Änderung. |

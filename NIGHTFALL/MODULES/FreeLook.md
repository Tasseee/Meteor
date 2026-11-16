# FreeLook

- **Status:** Code geprüft; Kamera-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `FreeLook`
- **Kategorie:** `VISUALS`
- **Aktueller Quellcode-SHA:** `836f98635a8c2ebddeac7f716c52f61da2705c67`
- **Mixin:** `CameraMixin` (`e269e58dc14a6fcd034b5c012efacf2040357d1f`)

## Zweck und Verhalten

Erlaubt, die Kamera mit der Maus unabhängig von der tatsächlichen Spieler-Blickrichtung zu drehen. Position und Blickrichtung des Spielers werden während aktiver FreeLook-Ansicht auf die gespeicherten Werte zurückgesetzt. Beim Aktivieren wird Freecam deaktiviert.

## Einstellungen

Keine modulspezifischen Einstellungen; Maus-Skalierung ist fest auf 0,15 eingestellt.

## Technische Prüfung

- **Nullfälle:** Aktivierung wird verzögert, bis Spieler und Welt vorhanden sind.
- **Kamera:** CameraMixin übernimmt nur die Kamera-Rotation, nicht die Kamera-Position.
- **Spielerzustand:** gespeicherte Spieler-Yaw/Pitch werden pro Tick erneut gesetzt.
- **Lifecycle:** bei Deaktivierung wird gespeicherte Yaw/Pitch wiederhergestellt; beim Weltwechsel wird der aktive Kamerastatus zurückgesetzt.
- **Threading/Ressourcen:** keine eigenen Threads oder Listener.
- **Allokationen:** pro Tick wurden zuvor zwei neue double-Arrays für die Cursorposition angelegt.
  
## Befunde und Verbesserungen

### FL-01 – Cursor-Arrays pro Tick neu angelegt

**Behoben:** `d9603126763f5a58d8d4edcf337dabaa7fcf0234`.

Die beiden Ein-Element-Arrays sind jetzt Felder und werden über Ticks wiederverwendet. Das reduziert kurzlebige Allokationen, ohne die Mausberechnung zu verändern.

**Verifikation:** Quellcode gespeichert und erneut gelesen; Kamera-/In-Game-Test und Build offen.

## Offene Tests

- [ ] Aktivieren bei vorhandener Welt
- [ ] Aktivieren ohne Welt und späterer Beitritt
- [ ] Yaw/Pitch-Rücksetzung nach Deaktivierung
- [ ] Wechsel zwischen FreeLook und Freecam
- [ ] Weltwechsel während aktivem FreeLook

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Cursor-Arrays wiederverwendet; Test offen. |

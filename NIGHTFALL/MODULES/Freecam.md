# Freecam

- **Status:** Code geprüft; Kamera-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `Freecam`
- **Kategorie:** `MOVEMENT`
- **Quellcode-SHA:** `96eefb8d870581414aba30061eb984d853f1ca4a`
- **Mixin:** `CameraMixin` (`e269e58dc14a6fcd034b5c012efacf2040357d1f`)

## Zweck und Verhalten

Bewegt die lokale Kamera unabhängig von der Spielerposition. Der Spieler bleibt an der gespeicherten Position; die Spielerbewegungsgeschwindigkeit wird während aktiver Freecam auf null gesetzt. Die Kamera wird über WASD sowie Space/Shift bewegt. Beim Aktivieren wird FreeLook deaktiviert.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Geschwindigkeit | 0,45 | 0,1–2,0; Schritt 0,05 |
| Kamera-Sensitivität | 0,15 | 0,03–0,5; Schritt 0,01 |

## Technische Prüfung

- **Aktivierung:** wartet auf Spieler und Welt; Startposition liegt 2,5 Blöcke vor dem Spieler.
- **Zeitdelta:** Bewegungszeit wird auf 0–0,05 Sekunden begrenzt, damit lange Frames keinen großen Kamerasprung verursachen.
- **Mausdelta:** Cursor-Arrays werden als Felder wiederverwendet; Delta ist auf ±250 Pixel/Tick begrenzt.
- **Interpolation:** Position, Yaw und Pitch werden zwischen vorherigem und aktuellem Kamerazustand interpoliert.
- **Lifecycle:** bei Deaktivierung werden Spielerposition, Velocity und Blickrichtung wiederhergestellt; Weltwechsel setzt die Kameraposition zurück.
- **Mixin:** rendert den lokalen Spieler auch in der Third-Person-Ansicht; CameraMixin setzt Kameraposition und -rotation.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler im statischen Kontrollfluss gefunden.
- `onWorldLeave()` setzt `active=false`, lässt das Modul aber aktiviert; beim Eintritt in eine neue Welt kann Freecam automatisch neu initialisieren. Ob das gewünscht ist, sollte getestet werden.
- Die Funktion wird als lokale Kameradarstellung dokumentiert; keine Änderung an serverseitiger Bewegung oder Anti-Cheat-Verhalten.

## Offene Tests

- [ ] WASD, Space, Shift
- [ ] Pitch-Grenzen −90°/90°
- [ ] Deaktivieren stellt Spielerzustand wieder her
- [ ] Welt verlassen und neue Welt betreten
- [ ] FreeLook-Kombination und Modulumschaltung
- [ ] niedrige FPS / Zeitdelta-Begrenzung

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Freecam und CameraMixin codebasiert geprüft; keine Java-Änderung. |

# Fly

- **Status:** Aktivierungsverzögerung adressiert; Bewegungs-/In-Game-Test offen
- **Quellcode-SHA:** `1ccfc86b206bb1fb3d863ab3dec26c51e1e5dc0a`
- **Kategorie:** `MOVEMENT`

## Verhalten

Setzt beim normalen Spieler (nicht im Fahrzeug und nicht im Elytra-Gleitflug) die horizontale Geschwindigkeit anhand der Blick-/Bewegungsrichtung. Space und Shift bestimmen die vertikale Komponente.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Speed (b/s) | 11,5 | 1–40; Schritt 0,5 |
| Vertical (b/s) | 9 | 1–30; Schritt 0,5 |

## Technische Prüfung

- Spieler-Nullcheck vorhanden.
- Fluggeschwindigkeit wird sowohl direkt beim Aktivieren als auch pro Tick gesetzt, damit die Bewegung nicht erst einen Tick später einsetzt.
- Beim Deaktivieren wird die Restgeschwindigkeit zurückgesetzt.
- Bei keiner vertikalen Eingabe wird Y-Geschwindigkeit auf 0 gesetzt.
- Kein eigener Listener, Thread oder Ressourcen-Cleanup.
- Tick-Ausnahme wird zentral behandelt.

## Befunde

- Der Start-Delay wurde durch unmittelbares Anwenden der Fluggeschwindigkeit in `onEnable()` behoben.
- Ruckelfreie Bewegung muss noch im Spiel geprüft werden; der Code setzt die Geschwindigkeit weiterhin tickbasiert.
- Es gibt keine explizite Deaktivierungsroutine; da die Geschwindigkeit bei Deaktivierung nicht zurückgesetzt wird, kann die zuletzt gesetzte Bewegung bis zur nächsten Vanilla-Physikaktualisierung bestehen bleiben. In-Game-Test offen.
- Keine Anpassung zur Vermeidung von Server-Kicks oder Anti-Cheat-Erkennung vorgenommen.

## Tests offen

- [ ] horizontal/vertikal
- [ ] keine Eingabe
- [ ] Fahrzeug/Elytra
- [ ] deaktivieren während Bewegung
- [ ] Weltwechsel

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |
| 2026-11-20 | Fluggeschwindigkeit wird bereits beim Aktivieren angewendet; Restgeschwindigkeit wird beim Deaktivieren zurückgesetzt. |

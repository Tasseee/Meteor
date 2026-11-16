# Fly

- **Status:** Code geprüft; Movement-/In-Game-Test offen
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
- Kein Zustand wird zwischen Ticks gespeichert.
- Bei keiner vertikalen Eingabe wird Y-Geschwindigkeit auf 0 gesetzt.
- Kein eigener Listener, Thread oder Ressourcen-Cleanup.
- Tick-Ausnahme wird zentral behandelt.

## Befunde

- Kein eindeutiger Codefehler in der einfachen Ticklogik bestätigt.
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

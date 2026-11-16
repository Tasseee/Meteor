# Speed

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `96e907c3dfc47d1b29894e16fe299f71f9a171d0`
- **Kategorie:** `MOVEMENT`

## Verhalten

Multipliziert die normale horizontale Bewegung bei vorhandener Eingabe. Vertikale Velocity bleibt unverändert. Es prüft Wasser, Lava, Fahrzeug, Elytra und Flugfähigkeit.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Multiplier | 1,4 | 1,0–3,0; Schritt 0,05 |
| Only Ground | aus | Boolesch |

## Technische Prüfung

- Spieler-Nullcheck.
- Keine Bewegung bei keinem Richtungseingang.
- Vertikale Komponente bleibt aus bisheriger Velocity erhalten.
- Keine gespeicherten Zustände, Threads, Listener oder Ressourcen.
- Fehlerbehandlung über zentralen Tick-Handler.

## Befunde

- Kein eindeutiger Lifecycle-Fehler bestätigt.
- Die horizontale Komponente wird tickweise vollständig neu gesetzt; Zusammenspiel mit anderen Bewegungsmodulen ist offen.
- Keine Werte oder Logik zur Vermeidung von Server-Kicks oder Anti-Cheat-Erkennung angepasst.

## Tests offen

- [ ] Only Ground an/aus
- [ ] Multiplier 1/3
- [ ] Wasser/Lava/Flug/Fahrzeug
- [ ] mehrere Bewegungsmodule gleichzeitig
- [ ] Deaktivieren während Bewegung

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

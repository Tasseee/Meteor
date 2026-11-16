# WaterWalk

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `d299100cbda32d2f5ad03ab6850046a599b478fd`
- **Kategorie:** `MOVEMENT`

## Verhalten

Setzt im Wasser bei Bewegungsinput die horizontale Geschwindigkeit auf einen konfigurierten Wert. Vertikale Geschwindigkeit bleibt unverändert. Creative und Fahrzeuge werden ausgeschlossen.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Speed (b/s) | 4,3 | 1–12; Schritt 0,1 |

## Technische Prüfung

- Spieler-Nullcheck vorhanden.
- Führt nur bei Wasserzustand und Bewegungsrichtung eine Geschwindigkeitsänderung durch.
- Keine eigenen Ressourcen, Listener oder Threads.
- Keine lokale Fehlerbehandlung; zentraler Tick-Handler greift.

## Befunde

- Kein eindeutiger Fehler in der kurzen Ticklogik festgestellt.
- Verhalten in flachem/tiefem Wasser, Strömungen und am Wasserrand muss getestet werden.
- Keine Anpassungen zum Umgehen von Server-Kicks oder Anti-Cheat vorgenommen.

## Tests offen

- [ ] flaches/tiefes Wasser
- [ ] Geschwindigkeit 1/12
- [ ] ohne Bewegungsinput
- [ ] Strömung/Wasserfall
- [ ] Creative/Fahrzeug/Weltwechsel

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

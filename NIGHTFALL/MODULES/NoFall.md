# NoFall

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `619db3a0614dfb0c74c8a86fcbf698165fa269d6`
- **Kategorie:** `MOVEMENT`

## Verhalten

Setzt die lokale Falldistanz während des Sinkflugs zurück, sofern die eingestellten Bedingungen erfüllt sind. Der Code ändert keine Ziel- oder Angriffslogik.

## Einstellungen

| Einstellung | Standard | Bedeutung |
|---|---|---|
| Nur ab 3 Blöcken | an | Falldistanz erst ab mindestens 3 Blöcken zurücksetzen |

## Technische Prüfung

- Spieler muss vorhanden sein.
- Elytra, Wasser und Lava werden ausgeschlossen.
- Spieler muss nicht am Boden sein und eine negative vertikale Geschwindigkeit haben.
- Bei aktivierter Option muss die Falldistanz mindestens 3 Blöcke erreichen.
- Es wird das Falldistanz-Feld auf 0 gesetzt; kein eigener Zustand.
- Keine Threads/Listener/Ressourcen.

## Befunde

- Kein Lifecycle- oder Nullfehler aus der kurzen Klasse ersichtlich.
- Keine Änderungen an Falldistanz-/Serververhalten oder Anti-Cheat-Umgehung vorgenommen.

## Tests offen

- [ ] Fall unter 3 Blöcken
- [ ] Fall über 3 Blöcken
- [ ] Wasser/Lava/Elytra
- [ ] deaktivieren im Fall
- [ ] verschiedene Landeblöcke

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

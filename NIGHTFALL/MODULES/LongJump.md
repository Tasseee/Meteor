# LongJump

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `fc5e0cc8c12a8b8f7c9f2b00f04f94f5c3c8222d`
- **Kategorie:** `MOVEMENT`

## Verhalten

Prüft, ob der Spieler am Boden steht und die Sprungtaste hält. Bei vorhandener Bewegungsrichtung wird ein Vorwärtsschub sowie mindestens eine vertikale Sprunggeschwindigkeit gesetzt.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Boost | 0,55 | 0,2–1,5; Schritt 0,05 |

## Technische Prüfung

- Spieler-Nullcheck und Bodenkontaktprüfung vorhanden.
- Wasser wird ausgeschlossen.
- Kein eigener Lifecycle-Zustand oder Ressource.
- Bewegung wird pro Tick angewendet, solange die Bedingung gilt.
- Keine lokale Fehlerbehandlung; zentraler Tick-Handler greift.

## Befunde

- Das Anwenden pro Tick bis zum tatsächlichen Abheben ist aus dem Code ersichtlich; wie oft der Wert vor Abheben gesetzt wird, ist ein In-Game-Prüfpunkt.
- Keine Änderungen an Sprungkraft oder Server-/Anti-Cheat-Verhalten vorgenommen.

## Tests offen

- [ ] Sprungtaste kurz/lang halten
- [ ] Boost 0,2/1,5
- [ ] ohne Richtung
- [ ] Wasser und unterschiedliche Böden
- [ ] Deaktivieren vor/nach Abheben

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

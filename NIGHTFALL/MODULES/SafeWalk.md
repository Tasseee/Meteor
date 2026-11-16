# SafeWalk

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `SafeWalk`
- **Kategorie:** `MOVEMENT`
- **Quellcode-SHA:** `ecba10920d59467deeba507c08e571ba6d6d4685`

## Zweck und Verhalten

Stoppt horizontale Bewegung, wenn der Spieler am Boden steht und die um die aktuelle horizontale Geschwindigkeit verschobene Bounding Box nach unten um die eingestellte maximale Fallhöhe frei wäre. Die Umsetzung setzt X- und Z-Geschwindigkeit auf 0; Y-Geschwindigkeit bleibt erhalten.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Max. Fallhöhe | 1,0 | 1,0–4,0 Blöcke; Schritt 0,5 |

## Technische Prüfung

- **Voraussetzungen:** Spieler/Welt vorhanden, Spieler am Boden, nicht sneaken, nicht in der entsprechenden Flug-/Creative-Fähigkeit.
- **Grenzfall:** Bewegung wird nur geprüft, wenn X oder Z exakt ungleich 0 ist.
- **Physik:** stoppt beide horizontalen Komponenten vollständig, nicht nur die Komponente über der Kante.
- **Lifecycle:** kein gespeicherter Zustand, keine Listener/Threads.
- **Fehlerbehandlung:** keine eigene Fehlerbehandlung; zentraler Tick-Handler greift.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Codefehler festgestellt.
- Das vollständige Stoppen beider Achsen kann sich an diagonalen Kanten abrupt anfühlen; das ist Teil der aktuellen Implementierung und sollte auf einer lokalen Testwelt bewertet werden.
- Keine Anti-Cheat- oder Serverbewegungs-Umgehung optimiert.

## Offene Tests

- [ ] Gerade Kante und diagonale Kante
- [ ] Fallhöhe 1 und 4
- [ ] sneaken, springen und fallen
- [ ] Slabs/Treppen und unregelmäßige Blockformen
- [ ] Weltwechsel und deaktiviertes Modul

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | SafeWalk codebasiert geprüft; keine Java-Änderung. |

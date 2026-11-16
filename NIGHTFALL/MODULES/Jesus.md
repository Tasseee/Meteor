# Jesus

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `f3a382d35d16955b2d360fe4d5d52e5a7ddea9f7`
- **Kategorie:** `MOVEMENT`

## Verhalten

Wenn der Spieler im Wasser ist und weder fliegt noch in Creative ist, wird die vertikale Geschwindigkeit abhängig davon angepasst, ob der Kopf unter Wasser steht. Space/Shift-Eingaben werden respektiert.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- Nullprüfungen auf Spieler und Welt vorhanden.
- Prüft Creative-/Flugstatus.
- Verändert nur vertikale Velocity.
- Keine gespeicherten Entity-Referenzen, Threads, Listener oder Ressourcen.
- Tick-Exceptions werden zentral behandelt.

## Befunde

- Kein eindeutiger Fehler im Modulcode bestätigt.
- Die Schwimm-/Unterwasser-Situation und Blockposition über dem Kopf benötigen In-Game-Tests.
- Keine Anpassungen für Server-Kicks oder Anti-Cheat-Umgehung vorgenommen.

## Tests offen

- [ ] Wasseroberfläche
- [ ] Kopf unter Wasser
- [ ] Space/Shift
- [ ] Creative/Flugstatus
- [ ] Weltwechsel und Deaktivierung

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

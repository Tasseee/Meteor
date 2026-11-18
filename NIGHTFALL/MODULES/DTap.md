# DTap

- **Registrierter Modulname:** `DTap`
- **Auditstatus:** Teilweise geprüft – Name und Beschreibung
- **Quellpfad:** `src/main/java/dev/nightfall/modules/DTap.java`
- **Quell-Blob-SHA:** `0acae4d047cf1326f906943f261ab6ef8132e8c0`

## Identität und sichtbare Beschreibung

Das Modul ist separat registriert und heißt weiterhin `DTap`. Die Klassenbeschreibung lautet sinngemäß, dass ein Obsidian-Block platziert und zwei End-Crystals nacheinander gezündet werden. Die Bezeichnung `Single-Tap` wurde nicht gefunden und wird nicht verwendet.

## Prüfgrenze

Nur Modulname und sichtbare Beschreibung wurden betrachtet. Die Zustandsmaschine, Angriffsausführung, Platzier-/Zünd-Timing, Zielauswahl und sonstige Kampfautomatisierung wurden nicht untersucht oder verändert.

## Offen

- [ ] Sichtbare Beschriftungen im laufenden Client prüfen.
- [ ] Nicht-angreifende Status-/Fehleranzeige prüfen, ohne Ausführungslogik anzufassen.
- [ ] Build-/In-Game-Nachweis fehlt.


## Kleine Inventar-Korrektur (2026-11-18)

- Die Startprüfung zählt End-Crystals jetzt auch in der Nebenhand. Zuvor wurden nur die 36 normalen Inventar-/Hotbar-Slots gezählt, obwohl die Platzierfunktion die Nebenhand bereits unterstützt.
- Die eigentliche DTap-Zustandsmaschine und ihre Platzier-/Zündzeiten wurden nicht umgebaut.
- In-Game-Test bleibt offen.

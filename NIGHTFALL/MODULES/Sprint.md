# Sprint

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `c0ad45368222a2a7a231d321c6651592e82cb114`
- **Kategorie:** `MOVEMENT`

## Verhalten

Aktiviert Sprint, wenn Vorwärtsbewegung gedrückt wird und die Spielerbedingungen Sprint erlauben: kein gesperrter Zustand, keine Nutzung eines Items und genügend Hunger.

## Einstellungen

Keine modulspezifischen Einstellungen.

## Technische Prüfung

- Spieler-Nullcheck vorhanden.
- Setzt Sprint nur bei erfüllten Bedingungen auf true.
- Bei nicht erfüllten Bedingungen setzt das Modul Sprint nicht explizit auf false; die Vanilla-Spielerlogik verwaltet den übrigen Zustand.
- Keine eigenen Threads, Listener oder Ressourcen.

## Befunde

- Kein eindeutiger Fehler aus der simplen Ticklogik bestätigt.
- Der Zustand bei Item-Nutzung, Hungergrenze und Bewegung muss im Spiel geprüft werden.
- Keine Anti-Cheat- oder Server-Kick-Optimierung vorgenommen.

## Tests offen

- [ ] vorwärts laufen
- [ ] hunger knapp über/unter Grenze
- [ ] Item verwenden
- [ ] Rückwärts/Seitwärts/Stillstand
- [ ] Weltwechsel und deaktivieren

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

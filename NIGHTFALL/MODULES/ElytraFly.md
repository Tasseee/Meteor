# ElytraFly

- **Status:** Code geprüft; Movement-/In-Game-Test offen
- **Quellcode-SHA:** `a161132988d479dd425f107ca2a1d287158b641b`
- **Kategorie:** `MOVEMENT`

## Verhalten

Setzt während aktivem Elytra-Gleitflug die horizontale Geschwindigkeit anhand der Bewegungsrichtung und die vertikale Geschwindigkeit anhand von Space/Shift. Funktioniert nicht im Fahrzeug.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Horizontal (b/s) | 26 | 5–60 |
| Vertical (b/s) | 9 | 1–25 |

## Technische Prüfung

- Nullprüfung auf Spieler.
- Aktiver Gleitflug und nicht im Fahrzeug sind Voraussetzung.
- Horizontalgeschwindigkeit wird aus b/s in Tickgeschwindigkeit umgerechnet.
- Bei keiner horizontalen Bewegung bleibt die bisherige X/Z-Geschwindigkeit erhalten.
- Vertikalgeschwindigkeit bleibt erhalten, wenn weder hoch noch runter gedrückt wird.
- Keine eigenen Ressourcen, Threads oder Listener.
- Keine lokale Fehlerbehandlung; zentraler Tick-Handler greift.

## Befunde

- Kein eindeutiger Fehler aus der kurzen Modulklasse bestätigt.
- Es gibt keine eigene Cleanup-Routine, da kein Zustand außerhalb des Spieler-Velocitys gespeichert wird.
- Keine Änderungen an Bewegungsleistung, Serververhalten oder Anti-Cheat-Umgehung vorgenommen.

## Tests offen

- [ ] im Elytra / ohne Elytra
- [ ] Space/Shift und diagonale Bewegung
- [ ] keine Eingabe
- [ ] Fahrzeugstatus
- [ ] Aktivieren/Deaktivieren und Weltwechsel

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung; keine Verhaltensänderung. |

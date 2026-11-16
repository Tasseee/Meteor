# ChunkEsp

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse:** `ChunkEsp`
- **Anzeigename:** `ChunkESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `fa8f2c5c326a29988eb2dc39ed3765a24bc16844`
- **Abhängigkeiten:** `BaseFinder`, `ChunkFavorites`, `ChunkResult`, `Projection`, `Draw`

## Zweck und Verhalten

Stellt bereits vom BaseFinder gelieferte Chunk-Ergebnisse als kompakte Radar-Marker dar. Benachbarte Treffer mit gleichem Status werden zu einer Region gruppiert. Favoriten werden priorisiert; Ergebnisse mit Fake-Chunk-Klassifizierung werden aus der Darstellung ausgeschlossen.

Diese Prüfung beschreibt die Anzeige-Schicht, nicht die Erkennungslogik des BaseFinder. Es wurden keine Erkennungs- oder Serverumgehungsmechanismen verändert.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Darstellung | Ring | Ring, Pulse, Beacon |
| Reichweite (Chunks) | 16 | 2–48 |
| Marker-Größe | 8 | 4–16 |
| Deckkraft | 0,88 | 0,2–1,0 |
| Puls-Geschwindigkeit | 1,0 | 0,2–3,0 |
| SUS-Farbe | RGB-Farbwert | Farbe |
| SUS-WEG-Farbe | RGB-Farbwert | Farbe |
| Favoriten-Farbe | RGB-Farbwert | Farbe |
| Score anzeigen | an | Boolesch |
| SUS WEG anzeigen | an | Boolesch |
| Favoriten hervorheben | an | Boolesch |
| Distanz-Fade | an | Boolesch |
| Max. Marker | 48 | 4–128 |

## Technische Prüfung

- **Cache:** Regionen werden höchstens alle 100 ms neu aufgebaut, außerdem bei Spieler-Chunk-Wechsel oder geänderter Markierungsanzahl.
- **Gruppierung:** Flood-Fill über vier direkte Nachbarn; Gruppen sind auf 64 Chunks begrenzt.
- **Sortierung:** Favoriten zuerst, anschließend Distanz und Score.
- **Rendergrenze:** höchstens `Max. Marker` und zusätzlich global 2.048 ESP-Objekte.
- **Nullfälle:** Spieler und gültige Projektion werden geprüft; BaseFinder wird über ModuleManager bezogen.
- **Allokationen:** Region-Neuaufbau erstellt temporäre Maps, Sets und Listen; dies geschieht nicht in jedem Frame.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler in der Marker-Gruppierung bestätigt.
- Bei identischer Markierungsanzahl wird der Cache spätestens nach 100 ms erneuert; Änderungen der Trefferinhalte sollten daher zeitnah sichtbar werden.
- Das Rendering hängt von BaseFinder-Markierungen und Fake-Chunk-Einstufung ab. Die Zuverlässigkeit dieser Eingabedaten wird separat beim BaseFinder dokumentiert.
- Keine Optimierung der BaseFinder-Erkennung oder der Server-seitigen Erfassung vorgenommen.

## Offene Tests

- [ ] Ring/Pulse/Beacon
- [ ] Favoriten und SUS-WEG
- [ ] Gruppen über 64 Chunks
- [ ] Markierungsänderung bei gleicher Trefferanzahl
- [ ] Distanzgrenzen und Bildschirmrand-Projektion
- [ ] Zusammenspiel mit BaseFinder an/aus

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung der Anzeige- und Gruppierungsschicht. |

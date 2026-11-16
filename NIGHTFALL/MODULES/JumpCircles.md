# JumpCircles

- **Status:** Code geprüft; In-Game-Test offen
- **Java-Klasse / Anzeigename:** `JumpCircles`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `21ea0dbb59e158989fdf0fbc903607db6477b86a`

## Zweck und Verhalten

Erzeugt bei der Landung nach einem Sprung einen lokalen Kreis aus 18 Partikeln rund um die Spielerposition. Es ist ein rein visueller Effekt.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Effekt | Cloud | Cloud, Enchant, Soul, Flamme, Herzen |
| Radius | 0,8 | 0,3–1,8; Schritt 0,1 |

## Technische Prüfung

- **Erkennung:** setzt `airborne`, sobald der Spieler nicht mehr am Boden ist; beim nächsten Bodenkontakt wird der Effekt ausgelöst.
- **Lifecycle:** Zustand wird bei Aktivierung und Weltwechsel zurückgesetzt.
- **Nullfälle:** tickt nur mit Spieler und Welt.
- **Performance:** exakt 18 Partikel pro Landung.
- **Threads/Ressourcen:** keine eigenen Threads oder Listener.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Fehler im Kontrollfluss gefunden.
- Kein `onDisable()`-Reset vorhanden; beim erneuten Aktivieren setzt `onEnable()` den Zustand zurück, daher ist kein persistierender Airborne-Zustand zu erwarten.
- Der Effekt wird nur beim Landen, nicht beim Abheben, ausgelöst – das entspricht der Modulbeschreibung.

## Offene Tests

- [ ] Alle Partikelarten
- [ ] Radius 0,3 / 1,8
- [ ] Sprung ohne Bewegung / laufender Sprung
- [ ] Modul während des Sprungs deaktivieren und erneut aktivieren
- [ ] Weltwechsel

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung. |

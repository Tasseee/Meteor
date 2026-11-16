# SpawnerProtect

- **Status:** Code geprüft; Lifecycle-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `SpawnerProtect`
- **Kategorie:** `MISC`
- **Aktueller Quellcode-SHA:** `929e2fb399ecb209ccac971143d58cf73e1c58df`

## Zweck und Verhalten

Merkt sich Spawner-Positionen, die im lokalen Umkreis sichtbar waren. Wenn ein zuvor erfasster Spawner in einem weiterhin geladenen Bereich nicht mehr vorhanden ist, wird eine Notification und Chatwarnung ausgegeben. Optional kann der Client bei Verlust getrennt werden.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Suchradius | 8 | 4–16; Schritt 1 |
| Bei Verlust trennen | aus | Boolesch |

## Technische Prüfung

- **Scanintervall:** alle zehn Clientticks.
- **Suchraum:** dreidimensionale Kugel mit eingestelltem Radius.
- **Unloaded chunks:** Positionen werden nur geprüft, wenn Chunk/Position geladen sind.
- **Deduplizierung:** Set speichert unveränderliche Blockpositionskopien.
- **Cleanup:** beim Aktivieren, Deaktivieren und Weltwechsel werden Track-Set und Timer zurückgesetzt.
- **Disconnect:** bei aktivierter Option wird die Verbindung mit einem Grund getrennt; kein Versuch, Serververhalten zu umgehen.
- **Performance:** bis zu ca. 17.000 Positionstests pro Scan bei Radius 16; keine Messung vorhanden.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

### SP-01 – alter Track-Zustand über Weltwechsel

**Behoben:** `8f6a59a9e0074c345b20096df6bbbdaa9ad4f5b7`.

Die gespeicherten Spawner-Positionen wurden vorher nicht in `onWorldLeave()` geleert. Dadurch konnten Positionen aus einer alten Welt in der neuen Welt geprüft werden. Weltwechsel setzt jetzt Set und Timer zurück.

**Verifikation:** Änderung auf GitHub gespeichert und erneut gelesen; Build- und In-Game-Test offen.

## Offene Tests

- [ ] Spawner im Radius erfassen
- [ ] Spawner verschwindet in geladener Umgebung
- [ ] Chunk entladen / wieder laden
- [ ] Weltwechsel mit gefülltem Track-Set
- [ ] Bei Verlust trennen an/aus
- [ ] Radius 4/16

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Weltwechsel-Cleanup ergänzt; Tests offen. |

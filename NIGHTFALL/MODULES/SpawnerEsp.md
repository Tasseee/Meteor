# SpawnerEsp

- **Status:** Code angepasst; Build- und In-Game-Test offen
- **Java-Klasse:** `SpawnerEsp`
- **Anzeigename:** `SpawnerESP`
- **Kategorie:** `VISUALS`
- **Gemeinsame Renderlogik:** `BlockEntityEsp` / `EspBase`

## Zweck und Verhalten

Markiert normale Mob-Spawner-Block-Entities in bereits geladenen Chunks. Es werden keine Chunks nachgeladen. Zusätzlich können generierte Spawner herausgefiltert werden:

- **Struktur-Spawner ausblenden:** Prüft die Bounding-Boxen der geladenen Structure-Starts. Das deckt insbesondere Spawner in Minenschächten und Netherfestungen ab.
- **Dungeon-Spawner ausblenden:** Nutzt eine Näherung für die klassischen Dungeon-Räume, die als Welt-Feature statt als Structure-Start generiert werden. Dafür werden Cobblestone-/Mossy-Cobblestone-Anteile und eine nahe Truhe geprüft.

Die Dungeon-Erkennung ist absichtlich separat abschaltbar: Ein vom Spieler gebauter Raum, der wie ein Dungeon aussieht, könnte sonst ebenfalls ausgeblendet werden. Minecraft speichert bei einem normalen Mob-Spawner keine verlässliche Herkunftsmarkierung, mit der sich jeder generierte Spawner zweifelsfrei von einem platzierten unterscheiden ließe.

## Einstellungen

Zusätzlich zu den geerbten ESP-Einstellungen:

| Einstellung | Standard | Wirkung |
|---|---:|---|
| Struktur-Spawner ausblenden | an | Blendet Spawner innerhalb geladener Struktur-Bounding-Boxen aus |
| Dungeon-Spawner ausblenden | an | Blendet Spawner mit typischem Dungeon-Raum-Muster aus |

Geerbte Einstellungen: Reichweite (128, maximal 256), Max Objekte, Linienstärke, Transparenz, Antialiasing, Distanz-Fade, Box-Stil, Tracer, Name und Distanz.

## Technische Prüfung

- **Scanquelle:** Block-Entities in geladenen Chunks und Strukturstarts; kein Chunk-Forcing.
- **Strukturfilter:** Liest Bounding-Boxen benachbarter geladener Structure-Starts ein.
- **Dungeonfilter:** Best-effort-Blockmusterprüfung, getrennt konfigurierbar.
- **Lifecycle:** Cache wird bei Deaktivierung/Weltwechsel geleert.
- **Build/In-Game:** noch nicht ausgeführt.

## Offene Tests

- [ ] Minenschacht-Spawner wird ausgeblendet
- [ ] Netherfestungs-Spawner wird ausgeblendet
- [ ] Dungeon-Spawner wird ausgeblendet
- [ ] Spielerplatzierter Spawner außerhalb einer Struktur bleibt sichtbar
- [ ] Spielerplatzierter Spawner in einer Struktur-Bounding-Box / dungeonähnlichem Raum: mögliche Fehlklassifikation prüfen
- [ ] Chunk-Entladung, Weltwechsel und Renderleistung

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-20 | Filter für generierte Struktur- und typische Dungeon-Spawner hinzugefügt; Laufzeittest offen. |

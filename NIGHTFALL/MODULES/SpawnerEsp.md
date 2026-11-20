# SpawnerEsp

- **Status:** Verbesserungen umgesetzt; Build und In-Game-Test offen
- **Java-Klasse:** `SpawnerEsp`
- **Anzeigename:** `SpawnerESP`
- **Kategorie:** `VISUALS`

## Verhalten und Einstellungen

- Markiert Spawner-Block-Entities in bereits geladenen Chunks.
- Standard-Reichweite: **256 Blöcke** (Maximum).
- Lila ESP-Box; Entfernung erscheint direkt im Label, z. B. `✦ Spawner · 42m`.
- **Dungeon-Filter** hat drei Modi:
  - **Vorsichtig:** filtert nur deutlich dungeonartige Umgebungen; erhöhte Schwelle (mindestens 40 Cobblestone-/Mossy-Cobblestone-Blöcke plus mindestens eine Truhe).
  - **Vergleichen:** blendet nichts aus; wahrscheinliche Dungeon-Spawner werden orange markiert und mit `?Dungeon` gekennzeichnet.
  - **Aus:** keine Dungeon-Prüfung.
- Umgebungsprüfungen werden pro Spawner zwischengespeichert und höchstens nach fünf periodischen Scans neu berechnet.

## Einschränkungen

Der Filter bleibt eine Heuristik. Spieler-Farmen mit vielen Cobblestone-Blöcken und Truhe können weiterhin ähnlich aussehen; natürliche Dungeons ohne diese Merkmale können durchrutschen. Wenn Custom- und natürliche Spawner dieselben clientseitigen Daten haben, ist eine sichere Unterscheidung nicht möglich.

Es werden ausschließlich Blockzustände in bereits geladenen Client-Chunks gelesen. Es werden keine Chunks nachgeladen und keine serverseitigen Sichtbarkeitsregeln umgangen.

## Offene Tests

- [ ] Build für die konfigurierte Minecraft-/Fabric-Version
- [ ] HugoSMP: Custom-Spawner in verschiedenen Umgebungen
- [ ] Vergleichsmodus: orange Markierung plausibel?
- [ ] Natürliche Dungeons und Spieler-Farmen (False Positives/Negatives)
- [ ] Weltwechsel / Chunk-Entladung
- [ ] Renderleistung bei vielen Spawnern

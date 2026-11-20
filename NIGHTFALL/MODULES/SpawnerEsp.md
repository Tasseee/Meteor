# SpawnerEsp

- **Status:** Änderungen umgesetzt; Build und In-Game-Test offen
- **Java-Klasse:** `SpawnerEsp`
- **Anzeigename:** `SpawnerESP`
- **Kategorie:** `VISUALS`

## Verhalten und Einstellungen

- Markiert Spawner-Block-Entities in bereits geladenen Chunks.
- Standard-Reichweite: **256 Blöcke** (Maximum).
- Lila ESP-Box; Entfernung direkt im Label, z. B. `✦ Spawner · 42m`.
- **Dungeon-Modus** bietet drei frei wählbare Einstellungen:
  - **Alle zeigen:** zeigt alle Spawner ohne Dungeon-Markierung.
  - **Dungeon markieren:** zeigt alle Spawner; vermutete Dungeon-Spawner werden orange markiert und mit `?Dungeon` gekennzeichnet.
  - **Dungeon ausblenden:** blendet Spawner aus, deren Umgebung stark nach einem natürlichen Dungeon aussieht.
- Dungeon-Umgebungsprüfungen werden pro Spawner zwischengespeichert und nach fünf periodischen Scans erneut berechnet.

## Dungeon-Erkennung

Die Heuristik prüft einen Bereich von 9×5×9 Blöcken und wertet mindestens 40 Cobblestone-/Mossy-Cobblestone-Blöcke plus mindestens eine Truhe als dungeonverdächtig. Das ist nur eine Annäherung: Spieler-Farmen können ähnlich aussehen und natürliche Dungeons können durchrutschen.

Die Prüfung liest ausschließlich Blockzustände, die der Client bereits in geladenen Chunks kennt. Sie lädt keine Chunks und umgeht keine serverseitigen Sichtbarkeitsregeln. Wenn Custom- und natürliche Spawner dieselben clientseitigen Daten haben, ist keine sichere Unterscheidung möglich.

## Offene Tests

- [ ] Build für die konfigurierte Minecraft-/Fabric-Version
- [ ] HugoSMP: alle drei Modi testen
- [ ] Natürliche Dungeons und Spieler-Farmen vergleichen
- [ ] Weltwechsel / Chunk-Entladung
- [ ] Renderleistung bei vielen Spawnern

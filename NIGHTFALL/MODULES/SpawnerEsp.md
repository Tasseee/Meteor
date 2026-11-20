# SpawnerEsp

- **Status:** Neu aufgebaut; Build und In-Game-Test offen
- **Java-Klasse:** `SpawnerEsp`
- **Anzeigename:** `SpawnerESP`
- **Kategorie:** `VISUALS`
- **Gemeinsame Renderlogik:** `BlockEntityEsp` / `EspBase`

## Neues Verhalten

- Markiert Spawner-Block-Entities in bereits geladenen Chunks.
- Lila Akzentfarbe und kompaktere 3D-Box als Standard.
- Label: `✦ Spawner`; Distanzanzeige bleibt über die geerbten ESP-Einstellungen verfügbar.
- Neuer Schalter **Natürliche Dungeons filtern** (standardmäßig an).
- Der Filter prüft beim Scan die sichtbaren Blockzustände in einer Umgebung von 9×5×9 Blöcken. Ein Bereich wird als typischer Dungeon gewertet, wenn mindestens 28 Cobblestone-/Mossy-Cobblestone-Blöcke und mindestens eine Truhe gefunden werden.
- Die Umgebungsprüfung läuft nur beim periodischen Scan, nicht in jedem Render-Frame.

## Wichtige Einschränkung

Das ist eine Heuristik, keine sichere Erkennung. Ein Custom-Spawner in einer cobblestoneartigen Farm mit Truhe kann fälschlich ausgeblendet werden; ein natürlicher Dungeon ohne passende Truhe/Blockmenge kann durchrutschen. Wenn Server-Custom-Spawner und natürliche Spawner identische Block-Entity-Daten verwenden, kann der Client sie nicht zweifelsfrei unterscheiden.

Der Filter liest ausschließlich Blockzustände, die der Client bereits in geladenen Chunks kennt. Er lädt keine Chunks und umgeht keine serverseitigen Sichtbarkeits- oder Anti-Xray-Regeln.

## Geerbte Einstellungen

Reichweite (Default 128, Maximum 256), Max Objekte, Linienstärke, Transparenz, Antialiasing, Distanz-Fade, Box-Stil, Tracer, Name und Distanz.

## Offene Tests

- [ ] Build für die konfigurierte Minecraft-/Fabric-Version
- [ ] Custom-Spawner auf HugoSMP
- [ ] Natürliche Dungeons mit und ohne Truhe
- [ ] False positives in Spieler-Farmen
- [ ] Weltwechsel / Chunk-Entladung
- [ ] Renderleistung bei vielen Spawnern


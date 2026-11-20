# EntityEsp

- **Status:** Positivlisten-Filter im Code gespeichert; Build-/In-Game-Test offen
- **Java-Klasse:** `EntityEsp`
- **Anzeigename:** `EntityESP`
- **Kategorie:** `VISUALS`
- **Code-Commit:** 5bd26e43b8593e7f4d7b026a14c2573805028fdc
- **Gemeinsame Renderlogik:** `EntityScanEsp` / `EspBase`

## Zweck und Verhalten

EntityESP zeigt nur diese Entity-Gruppen:

- Armor Stands
- Item Frames und Glow Item Frames
- Enderperlen
- Armadillos
- Boote und Rafts (einschließlich Chest-Varianten)

Alle anderen Entity-Typen werden vom Filter ausgeschlossen. Die Anzeige verwendet weiterhin die gemeinsame Reichweiten-, Sichtbarkeits-, Objektlimit- und HUD-Renderlogik aus `EntityScanEsp` / `EspBase`. Kampfbezogene Wurfobjekte und Trank-Projektile bleiben ausgeblendet.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Health (geerbt) | an |
| Nur sichtbare (geerbt) | aus |
| Reichweite (korrigierter Default) | 128 |
| Max Objekte | 512 |
| Box | 3D |
| Name / Distanz | an |
| Tracer | aus |

Die früheren Umschalter für feindliche/passive Mobs und die freie Entity-Type-Auswahl wurden entfernt, damit die Positivliste verbindlich bleibt.

## Technische Prüfung

- **Klassifizierung:** Registry-ID des Entity-Typs wird gegen eine feste Positivliste geprüft. Boot- und Raft-Varianten werden über ihre IDs erkannt.
- **Renderpfad:** geladene Entities werden jedes HUD-Frame durchlaufen; Positionen/Boxen werden mit TickDelta interpoliert.
- **Sortierung:** Renderbuffer wird nach Distanz sortiert, wenn die Anzahl über Max Objects liegt.
- **Fehlerbehandlung:** fehlende Registry-IDs oder Registry-Laufzeitfehler führen dazu, dass die Entity nicht akzeptiert wird.
- **Performance:** kein zusätzlicher Welt-Scan; die bestehende Render-Schleife wird weiterverwendet. Messung steht aus.

## Offene Tests

- [ ] Armor Stand / Item Frame / Glow Item Frame
- [ ] Ender Pearl und Armadillo
- [ ] Boot / Chest Boat / Raft
- [ ] Andere Mobs und Entity-Typen bleiben ausgeblendet
- [ ] Reichweite, Sichtbarkeit und Max-Objekte-Limit
- [ ] Build und In-Game-Test

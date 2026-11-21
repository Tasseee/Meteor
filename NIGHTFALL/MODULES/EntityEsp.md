# EntityESP

- **Status:** 2D-HUD-ESP umgesetzt; Build-/In-Game-Test offen
- **Java-Klasse:** `EntityEsp`
- **Anzeigename:** `EntityESP`
- **Kategorie:** `VISUALS`
- **Renderlogik:** eigene 2D-Projektion mit geglaetteten Boxen

## Zweck und Verhalten

EntityESP ist jetzt das einzige allgemeine Entity-ESP-Modul. Die fruehere 3D-Variante wurde durch die 2D-Version ersetzt. Geladene, ausgewaehlte Entities erhalten abgerundete Bildschirm-Boxen mit optionaler Fuellung, Soft Glow und Distanz-Fade.

Standardmaessig ausgewaehlt sind:

- Item Frames und Glow Item Frames
- Armor Stands
- Wölfe, Katzen und Armadillos
- Villager
- Eisen- und Schneegolems
- Enderperlen, Wind Charges und Splash Potions

Die Entity-Typen koennen ueber die Einstellung **Entity-Typen** angepasst werden.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Entity-Typen | obige Auswahl |
| Reichweite | 96 |
| Eckenradius | 5 |
| Linienstärke | 1,5 |
| Fuellen (Alpha) | 35 |
| Smoothing | 0,55 |
| Distanz-Fade | an |
| Soft Glow | an |
| **Namen anzeigen** | **an** |

Mit **Namen anzeigen** lassen sich die Entity-Namen oberhalb der jeweiligen 2D-Box ein- oder ausblenden.

## Technische Hinweise

- Positionen und Bounding-Boxen werden mit dem aktuellen Render-Tick interpoliert und anschliessend in den HUD-Raum projiziert.
- Die Boxpositionen werden pro Entity-ID geglaettet und beim Deaktivieren oder Weltwechsel geleert.
- Es wird ausschliesslich der HUD-Renderpfad verwendet; kein eigener 3D-Linien-Vertexpfad.
- Das alte separate 3D-EntityESP und die doppelte Modulregistrierung sind entfernt.

## Offene Tests

- [ ] Alle voreingestellten Entity-Typen
- [ ] Eigene Entity-Typen ueber die Auswahl
- [ ] Namen ein-/ausblenden
- [ ] Reichweite, Smoothing, Fuellung, Glow und Distanz-Fade
- [ ] Build und In-Game-Test

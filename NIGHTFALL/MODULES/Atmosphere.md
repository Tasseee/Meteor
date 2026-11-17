# Atmosphere

- **Registrierter Modulname:** `Atmosphere`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/Atmosphere.java`
- **Render-Einstieg:** `Nightfall.onInitializeClient()` hängt den HUD-Callback vor der Hotbar ein.

## Zweck und Einstellungen

Farbige Bildschirm-Atmosphäre mit wandernden Nebelbändern, Lichtstrahlen, Vignette und schwebenden Partikeln.

| Einstellung | Standard | Bereich / Optionen |
|---|---:|---|
| Farbe | `0x14DC14` | RGB |
| Nebeldichte | 0,40 | 0,00–1,00 |
| Vignette | 0,60 | 0,00–1,00 |
| Partikel | AN | Ein/Aus |
| Partikel-Anzahl | 70 | 0–200 |
| Regenbogen | AUS | Ein/Aus |
| Atmen | AN | Ein/Aus |
| Lichtstrahlen | AN | Ein/Aus |
| Regenbogen-Tempo | 0,25 | 0,02–1,50 |

## Codebefunde

- Die Ausgabe wird nur bei aktivem Modul, vorhandenem Spieler und ohne geöffnetes GUI gerendert.
- Nebelbänder, Strahlen und Partikel werden pro Renderframe aus `System.nanoTime()` berechnet.
- Die Partikelpositionen werden deterministisch aus dem Index abgeleitet; es werden keine Partikelobjekte oder Threads angelegt.
- Regenbogenfarbe und Atem-Puls werden renderseitig berechnet.

## Offene Tests

- [ ] Bildschirmtönung und Nebeldichte bei 0 und 1 prüfen.
- [ ] Lichtstrahlen, Vignette, Partikel und Regenbogen getrennt prüfen.
- [ ] Performance bei 200 Partikeln messen.
- [ ] Build und In-Game-Test bleiben offen.

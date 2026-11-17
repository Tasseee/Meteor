# GhostSkin

- **Registrierter Modulname:** `GhostSkin`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/GhostSkin.java`
- **Renderer:** `src/main/java/dev/nightfall/render/GhostSkinFeatureRenderer.java`

## Zweck und Einstellungen

Halbtransparentes, pulsierendes Skin-Overlay mit mehreren 3D-Schichten und optionaler Aura. Der Renderer vergleicht die ID des Renderzustands mit der ID des lokalen Spielers und soll dadurch ausschließlich den eigenen Spieler betreffen.

| Einstellung | Standard | Bereich / Optionen |
|---|---:|---|
| Farbe | `0x00FF40` | RGB |
| Deckkraft | 0,55 | 0,05–1,00 |
| Aura | AN | Ein/Aus |
| Aura-Deckkraft | 0,28 | 0,02–0,80 |
| Aura-Größe | 1,10 | 1,02–1,40 |
| Pulsieren | 0,25 | 0,00–0,60 |
| Regenbogen | AUS | Ein/Aus |
| 3D-Schichten | 4 | 0–8 |
| Schicht-Abstand | 0,035 | 0,01–0,09 |
| Schicht-Licht | 0,22 | 0,02–0,60 |
| Wabern | 0,45 | 0,00–1,00 |
| Regenbogen-Tempo | 0,25 | 0,02–1,50 |

## Codebefunde

- Das Modul ist in `ModuleManager.init()` registriert.
- Der Feature-Renderer wird über `LivingEntityFeatureRendererRegistrationCallback` an Player-Renderer angehängt.
- Der Renderpfad prüft Modulstatus, lokale Spieler-ID und verfügbare Skin-Textur.
- Jede zusätzliche Skin-Schicht wird mit Alpha begrenzt und in einem eigenen Matrix-Push/Pop gezeichnet.
- Es gibt keine eigene Konfigurationsdatei; Einstellungen laufen über Nightfalls Modul-Setting-System.

## Offene Tests

- [ ] Third-Person-Skin-Overlay sichtbar und nur lokal prüfen.
- [ ] Regenbogen, 3D-Schichten und Aura-Minimum/-Maximum prüfen.
- [ ] Skinwechsel, Slim-/Classic-Skin und fehlende Textur prüfen.
- [ ] Build und In-Game-Test bleiben offen.

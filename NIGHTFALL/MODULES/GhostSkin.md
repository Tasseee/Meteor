# GhostSkin

- **Registrierter Modulname:** `GhostSkin`
- **Auditstatus:** Codeprüfung des Renderpfads; In-Game-Test offen
- **Modul-Blob-SHA:** `2850c6ffeee96758e0efaf29c7d77b2a0de71a19`
- **Renderer:** `src/main/java/dev/nightfall/render/GhostSkinFeatureRenderer.java`
- **Renderer-Blob-SHA:** `69305ed1be7e2e50355f3b5e20e8eb64453c82f4`

## Zweck und Einstellungen

Das Modul beschreibt ein halbtransparentes, leuchtendes Skin-Overlay auf dem eigenen Spieler in Third-Person.

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Farbe | `0x00FF40` | RGB |
| Deckkraft | 0,55 | 0,05–1,00 |
| Aura | AN | Boolean |
| Aura-Deckkraft | 0,28 | 0,02–0,80 |
| Aura-Größe | 1,10 | 1,02–1,40 |
| Pulsieren | 0,25 | 0,00–0,60 |

## Codebefunde

- Renderer-Callback wird für Player-Renderer registriert.
- Es wird nur gerendert, wenn das Modul aktiv ist, der Renderzustand zur eigenen Spieler-ID gehört und eine Skin-Textur vorhanden ist.
- Alpha wird vor der ARGB-Erzeugung auf 0–255 begrenzt.
- Matrix Push/Pop ist durch `try/finally` abgesichert.
- Ein fehlender ModuleManager-Eintrag wird defensiv abgefangen.
- Das Pulsieren wird pro Renderaufruf aus `System.nanoTime()` berechnet; Laufzeit-/Performance-Messung liegt nicht vor.

## Offene Prüfung

<badge color="warning">In-Game-Test offen</badge>

- [ ] Third-Person-Overlay mit Standard-Skin prüfen.
- [ ] Aura ein/aus und minimale/maximale Deckkraft prüfen.
- [ ] Skinwechsel und fehlende Textur prüfen.
- [ ] Prüfen, ob nur die eigene Spielerfigur betroffen ist.
- [ ] Build-/Laufzeitnachweis für diesen Commit erfassen.

Dies ist eine Codeanalyse, kein bestätigter visueller In-Game-Test.

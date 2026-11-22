# SkyVisuals

- **Status:** Implementiert; CI-/In-Game-Prüfung ausstehend
- **Kategorie:** `VISUALS`
- **Renderer:** `src/main/java/dev/nightfall/render/SkyVisualRenderer.java`
- **Texturen:** `src/main/resources/assets/nightfall/textures/sky/`

## Sky-Stile

Das Modul verwendet die fünf mitgelieferten Skybox-Motive als echte Texturen, nicht als OptiFine-Abhängigkeit:

| Stil | Textur |
|---|---|
| Nebula | `nebula.png` |
| Aurora | `aurora.png` |
| Synthwave | `synthwave.png` |
| Eclipse | `eclipse.png` |
| Planet | `planet.png` |

Alle Motive sind 3×2-Cubemap-Atlanten. Der Nightfall-Renderer mappt die sechs Würfelflächen auf die jeweiligen Atlas-Bereiche und rendert die Skybox kamera-zentriert im World-Render-Pass.

## Einstellungen

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Himmel | Nebula | Nebula, Aurora, Synthwave, Eclipse, Planet |
| Abstand | 112 | 48–180 |
| Intensität | 1,0 | 0,1–1,0 |
| Rotation | 1,0 | 0–4 |

Rotation wird nur auf Nebula und Planet angewendet, da diese beiden beigefügten Packs eine Rotationsgeschwindigkeit definieren.

## Technische Hinweise

- Die Texturen liegen direkt in Nightfalls Ressourcen; OptiFine wird zur Laufzeit nicht benötigt.
- Der Skybox-Consumer nutzt den Entity-Translucent-Textur-Layer und Volllicht.
- Matrix-Push/Pop ist mit `try/finally` abgesichert.
- Die alten prozeduralen Himmel-Würfel und die separaten SkyRibbons-Bänder sind entfernt.
- Die korrekte Darstellung der sechs Flächen, Sichtbarkeit in allen Perspektiven und Performance müssen im Spiel geprüft werden.

# ItemGlow

- **Registrierter Modulname:** `ItemGlow`
- **Anzeigename:** `Item Glow`
- **Auditstatus:** Registrierungs- und Settings-Inventur; Renderpfad offen
- **Quellpfad:** `src/main/java/dev/nightfall/modules/ItemGlow.java`
- **Quell-Blob-SHA:** `b7c2a6753b33d4e55f3f45bedc98ddcc87766a45`

## Einstellungen

| Einstellung | Standard | Bereich |
|---|---:|---:|
| Farbe | `0x39FF14` | RGB |
| Glow-Radius | 6 | 1–12 |
| Intensität | 0,85 | 0,10–1,00 |
| Pulsieren | AN | Boolean |
| Hotbar | AN | Boolean |
| Inventar | AN | Boolean |

Die aktuelle Klasse stellt diese Werte über Getter bereit. Die tatsächlichen Render-Hooks/Mixins und deren Lebenszyklus wurden noch nicht untersucht.

## Inventurhinweis

Die frühere Auditliste führte `ItemGlint`, aber `ModuleManager.init()` registriert aktuell `ItemGlow`. Ein aktuelles `ItemGlint`-Modul ist in der Java-Inventur nicht vorhanden. Die alte `ItemGlint.md`-Akte bleibt als historischer Eintrag erhalten.

## Offen

- [ ] Renderer-/Mixin-Verbraucher der Getter identifizieren.
- [ ] UI, Renderpfade, Nullfälle und Cleanup prüfen.
- [ ] Build-/In-Game-Test ausführen.

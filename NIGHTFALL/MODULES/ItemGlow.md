# ItemGlow

- **Registrierter Modulname:** `ItemGlow`
- **Anzeigename:** `Item Glow`
- **Kategorie:** `VISUALS`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/ItemGlow.java`
- **Renderer:** `ItemGlowRenderer.java` (HUD-/Inventar-Glow) und `ItemGlowMeshRenderer.java` (3D-Mesh-Kontur)
- **Mixin:** `ItemGlowMeshMixin.java`

## Zweck und Einstellungen

Neon-Effekte für gehaltene Items, Hotbar-/Inventarsymbole und eine optionale Kontur entlang der Silhouette des tatsächlich gerenderten First-Person-Item-Modells. Der Effekt ersetzt keinen Minecraft-Enchantment-Glint.

| Einstellung | Standard | Bereich / Optionen |
|---|---:|---|
| Farbe | `0x39FF14` | RGB |
| Glow-Radius | 34 | 8–90 |
| Intensität | 0,85 | 0,05–1,00 |
| Pulsieren | AN | Ein/Aus |
| Regenbogen | AUS | Ein/Aus |
| Ring & Funken | AN | Ein/Aus |
| Zweithand | AN | Ein/Aus |
| Hotbar | AN | Ein/Aus |
| Inventar | AN | Ein/Aus |
| Position X | 0,74 | 0,50–0,95 |
| Position Y | 0,80 | 0,50–0,95 |
| Regenbogen-Tempo | 0,25 | 0,02–1,50 |
| 3D-Kontur | AN | Ein/Aus |
| Kontur-Stärke | 1,0 | 0,2–2,0 |

## Codebefunde

- Das Modul ist in `ModuleManager.init()` genau einmal registriert.
- `ItemGlowRenderer.init()` registriert den HUD-Callback und den Screen-Callback für Inventarbildschirme.
- Die First-Person-Aura zeichnet mehrlagige Ringe und umlaufende Funken um gehaltene Items; Hotbar, Offhand und Inventar haben getrennte Ein-/Aus-Schalter.
- `ItemGlowMeshMixin` hängt sich an Minecrafts Item-Render-Methode. Der Mesh-Renderer wertet die Baked-Quad-Geometrie aus und zeichnet eine Kontur an den Silhouettenkanten des First-Person-Modells.
- Die 3D-Kontur verwendet Minecrafts Linien-Render-Layer; die Farbe folgt dem Regenbogenmodus.
- Der Inventarzugriff läuft über `HandledScreenAccessor`; leere Slots werden übersprungen.

## Offene Tests

- [ ] 3D-Kontur an Schwert, Werkzeug, Block und Bogen im First-Person-Modus prüfen.
- [ ] Hotbar, Offhand und mehrere Inventarbildschirmtypen prüfen.
- [ ] Rainbow, Pulsieren und Kontur-Stärke-Minimum/-Maximum prüfen.
- [ ] Performance mit Items mit vielen Modell-Quads messen.
- [ ] Build und In-Game-Test bleiben offen.

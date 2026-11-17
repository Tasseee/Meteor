# ItemGlint – historischer Eintrag

> **Nicht mehr registriert auf aktuellem `main`.** `ModuleManager.init()` registriert `ItemGlow`; eine aktuelle Java-Klasse `ItemGlint` ist nicht vorhanden. Für den aktiven Modulstand siehe [ItemGlow.md](ItemGlow.md). Die folgenden Notizen dokumentieren einen früheren Modulstand und dürfen nicht als aktuelle Implementierung gelesen werden.


- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse:** `ItemGlint`
- **Anzeigename im Client:** `Item Glint`
- **Kategorie:** `VISUALS`
- **Modul-Quellcode-SHA:** `c54045d5d637779b262e918d7e1c499ef3a89da1`
- **Mixins:** `ItemStackGlintMixin`, `ItemRendererGlintColorMixin`
- **Mixin-SHAs:** `7d0b4967b9cdf9dddbaeb4226ff8ab8a3e2568cc`, `d62c8a7438d13e4dcc11a9457e89203b03e34a29`

## Zweck und Verhalten

Erzwingt bei aktiviertem Modul den Glanz-Effekt auf nicht-null ItemStacks und färbt Minecrafts reguläre Glint-Render-Layer ein. Die Layer/Texture werden laut Mixin-Kommentar beibehalten; die Vertex-Farbe wird über `GlintColorVertexConsumer` verändert.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---|---|
| Modus | Static | Static, Rainbow, Pulse |
| Farbe | RGB 0xFF2878 | Primärfarbe |
| Zweite Farbe | RGB 0x3C78FF | Zweite Farbe für Pulse |
| Geschwindigkeit | 1,0 | 0,1–4,0; Schritt 0,1 |
| Intensität | 1,0 | 0,2–2,0; Schritt 0,1 |

## Technische Prüfung

- **Nullfall:** `forceGlint` prüft auf nicht-null, nicht auf leeren Stack.
- **Farbkanäle:** Intensität wird pro Kanal angewendet und anschließend auf 0–255 begrenzt.
- **Render-Layer:** nur drei bekannte Glint-Layer werden umwickelt; andere Layer laufen unverändert.
- **Fehlerbehandlung:** Mixin-Zugriffe fangen Exceptions und geben den Originalpfad zurück; Fehler werden still ignoriert.
- **Allokationen:** ein `GlintColorVertexConsumer` wird bei passender Glint-Ausgabe erzeugt; Häufigkeit/Performance nicht gemessen.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

- Keine bestätigte Fehlfunktion aus der statischen Prüfung.
- Prüfen, ob leere ItemStacks überhaupt an `forceGlint` übergeben werden. Falls ja, kann ein zusätzlicher `!stack.isEmpty()`-Check sinnvolle unnötige Renderarbeit verhindern.
- Stille Fehler in den Mixin-Catch-Blöcken erschweren Diagnosen; ein gedrosselter Debug-Logeintrag wäre nützlich.

## Offene Tests

- [ ] Items ohne regulären Glint und bereits verzauberte Items
- [ ] Static/Rainbow/Pulse
- [ ] Intensität 0,2 und 2,0
- [ ] Rüstungs-/Hand-/GUI-Renderpfade
- [ ] Leere Stacks und Performance bei vielen Items

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Modul und beide Glint-Mixins codebasiert geprüft. |

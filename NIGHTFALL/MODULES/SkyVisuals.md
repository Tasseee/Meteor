# SkyVisuals

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `SkyVisuals`
- **Kategorie:** `VISUALS`
- **Modul-Quellcode-SHA:** `25004cd763bead213205afa9eccb9f880879f568`
- **Renderer:** `src/main/java/dev/nightfall/render/SkyVisualRenderer.java`
- **Renderer-SHA:** `3e5fdf2bb8c85b9d0323482b95b4b8e1a1bbd128`

## Zweck und Verhalten

Zeichnet nach den Entities eine lokale Weltraum-/Himmelsszene. Der Renderer zeigt in allen Modi 120 Sterne und ergänzt je nach Stil Nordlicht-Bänder, einen Planeten oder Nebelgeometrie. Kameraorientierung und Distanz werden für die Positionierung herangezogen.

## Einstellungen

| Einstellung | Standard | Grenzen / Optionen |
|---|---:|---|
| Himmel | Weltraum | Weltraum, Nordlicht, Planet |
| Hauptfarbe | RGB 5627374 | RGB-Farbe |
| Abstand | 112 | 64–180; Schritt 8 |
| Intensität | 1,0 | 0,2–2,0; Schritt 0,05 |
| Planetengröße | 1,0 | 0,5–2,0; Schritt 0,05 |

## Technische Prüfung

- **Nullfälle:** Spieler, Welt, Render-Matrizen und Consumer werden geprüft.
- **Matrix-Sicherheit:** Push/Pop sind in `try/finally` gekapselt.
- **Rendering:** 120 Sternwürfel je Frame; Nordlicht 168 Segmente, Nebel 90 Würfel; Planet 18×72 = 1.296 Oberflächen-Quads plus 72 Ringelemente.
- **Performance:** Planetmodus ist rechnerisch deutlich aufwendiger und erzeugt pro Frame viele temporäre Vektoren/Boxen. Keine Messung vorhanden.
- **Intensität:** Planet-Alpha wird als `0.98 * intensity` übergeben; bei Intensität über ca. 1,02 liegt dieser Wert über 1,0. Das sollte mit dem tatsächlichen Vertex-/Blendpfad geprüft werden.
- **Threads/Ressourcen:** keine eigenen Threads; ein WorldRenderEvents-Callback wird beim Clientstart registriert.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

### SV-01 – Planetmodus als Performance-Prüfkandidat

Die Planetoberfläche besteht aus 1.296 Quads pro Renderframe, zusätzlich zu Sternen und Ringelementen. Sinnvolle Optimierung wäre ein wiederverwendetes Mesh bzw. niedrigere Detailstufen bei geringer Renderdistanz oder niedriger FPS. Ohne Profiling wird das nicht als bestätigter Lag-Fehler eingestuft.

### SV-02 – Alpha bei hoher Intensität

Bei maximaler Intensität wird ein Alpha-Wert über 1,0 an den Vertex-Consumer übergeben. Renderer-/Blendverhalten ist nicht verifiziert; eine Begrenzung auf 1,0 wäre eine mögliche defensive Korrektur, falls Tests ein Artefakt zeigen.

## Offene Tests

- [ ] Alle drei Himmelsstile
- [ ] Intensität 0,2 / 1,0 / 2,0
- [ ] Planetengröße und Abstand an Grenzen
- [ ] Performancevergleich der Modi
- [ ] Alpha-/Blendverhalten bei hoher Intensität
- [ ] Perspektivwechsel und Weltwechsel

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Modul und SkyVisualRenderer codebasiert geprüft. |

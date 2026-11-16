# Hats

- **Status:** Code geprüft; Render-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `Hats`
- **Kategorie:** `VISUALS`
- **Modul-Quellcode-SHA:** `18abea99656bae18266366e8fc663f1940cec403`
- **Renderer:** `src/main/java/dev/nightfall/render/HatsRenderer.java`
- **Renderer-SHA:** `58eb17c0edf2d235ac7f58c4f0a7bc6e0c9b9e09`

## Zweck und Verhalten

Stellt einen lokal gerenderten 3D-Spiralhut über dem eigenen Spieler dar. Die Fabric-World-Render-Callback-Registrierung erfolgt beim Clientstart. Der Renderer prüft Spieler, Welt, Render-Matrizen und Consumer sowie den aktivierten Modulstatus.

## Einstellungen

| Einstellung | Standard | Grenzen / Bedeutung |
|---|---:|---|
| Farbe | RGB 5627374 | Farbwert |
| Größe | 1,0 | 0,5–2,0; Schritt 0,1 |
| Höhe | 0,08 | −0,2–0,6; Schritt 0,02 |
| Drehgeschwindigkeit | 1,0 | 0,1–3,0; Schritt 0,1 |

## Technische Prüfung

- **Renderumfang:** 3 Spiralen mit jeweils 25 Punkten, also 75 kleine Würfel pro Frame.
- **Matrix-Sicherheit:** Matrix wird vor der Geometrie gepusht und in `finally` wieder gepoppt.
- **Nullfälle:** Spieler, Welt, Matrizen und Consumer werden geprüft; Kamerazugriff erfolgt danach.
- **Threads/Ressourcen:** kein eigener Thread; ein WorldRenderEvents-Callback wird bei Clientinitialisierung registriert.
- **Performance:** 75 Würfel werden pro aktivem Renderframe geometrisch neu aufgebaut. Das ist ein Kandidat für eine spätere Messung/Optimierung, aber ohne FPS-Messung kein bestätigtes Problem.
- **In-Game/Build:** noch nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Logikfehler im Modul oder Renderer bestätigt.
- Performance bei niedrigen FPS und bei hoher Renderlast prüfen.
- Renderer-Registrierung sollte im Client-Lifecycle genau einmal erfolgen; die aktuelle Registrierung liegt in `onInitializeClient()`.

## Offene Tests

- [ ] Dritte Person und Perspektivwechsel
- [ ] Farbe, Größe, Höhe und Drehgeschwindigkeit an Grenzwerten
- [ ] HUD-/GUI-Sichtbarkeit
- [ ] Weltwechsel und Respawn
- [ ] FPS-Auswirkung bei aktivem/inaktivem Modul

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Modul und HatsRenderer codebasiert geprüft. |

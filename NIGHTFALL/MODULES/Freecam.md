# Freecam

- **Status:** Codeänderung gespeichert; Kamera-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `Freecam`
- **Kategorie:** `MOVEMENT`
- **Mixin:** `CameraMixin`

## Zweck und Verhalten

Die vorhandene Nightfall-Freecam bleibt die einzige Kamera-Implementierung. Die ZIP-eigene separate `FreecamController`-Architektur wurde nicht zusätzlich registriert, da sie eigene Entity-/GameRenderer-Mixins voraussetzt.

Die Kamera bewegt sich lokal unabhängig vom Spieler. Ergänzt wurden sanftes Beschleunigen/Abbremsen, ein optionaler Stopp bei Schaden sowie eine Koordinaten-/Geschwindigkeitsanzeige.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Geschwindigkeit | 0,6 | 0,1–3,0; Schritt 0,05 |
| Gleiten | 0,25 | 0,03–1,0; Schritt 0,01 |
| Stopp bei Schaden | AN | Ein/Aus |
| Freecam-Anzeige | AN | Ein/Aus |
| Kamera-Sensitivität | 0,15 | 0,03–0,5; Schritt 0,01 |
| Vertikale Maus invertieren | AN | Ein/Aus |

## Technische Prüfung

- Die bisherige Kamera-/Maussteuerung und `CameraMixin` bleiben erhalten.
- Die neue Geschwindigkeitsglättung wird über eine separate Kamerageschwindigkeit gerechnet.
- Bei Schaden wird das Modul deaktiviert; der vorhandene Disable-Pfad stellt die gespeicherte Spielerposition und Blickrichtung wieder her.
- Die Anzeige wird im regulären Nightfall-HUD-Renderdurchlauf gezeichnet.
- Der Alt-Body-Modus aus dem ZIP wurde nicht übernommen, da die dazugehörigen Hooks nicht mitgeliefert wurden.

## Offene Tests

- [ ] WASD, Space und Shift mit niedriger/hoher Geschwindigkeit prüfen.
- [ ] Gleiten-Minimum/-Maximum und sofortiges Loslassen der Tasten prüfen.
- [ ] Schaden-Stopp und Rückkehr zur gespeicherten Spielerposition prüfen.
- [ ] HUD-Position und Koordinatenanzeige prüfen.
- [ ] Maus-Invertierung bleibt unverändert funktionsfähig.
- [ ] Build und In-Game-Test bleiben offen.

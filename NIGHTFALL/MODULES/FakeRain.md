# FakeRain

- **Status:** Erstellt und registriert; Build-/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `FakeRain`
- **Kategorie:** `VISUALS`
- **Code-Commit:** `1d9101de2ca2a148f212baec67ec6f8f9e57895c`
- **Registrierung:** `src/main/java/dev/nightfall/module/ModuleManager.java`

## Zweck

Erzeugt einen lokalen Regen-Look als HUD-Overlay. Tropfen fallen als feine, animierte Streifen über das Bild.

Das Modul verändert **nicht** das echte Wetter, die Tageszeit, Weltzustände oder Netzwerkpakete. Andere Spieler sehen den Fake-Regen nicht.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Tropfen | 140 | 20–500; Schritt 10 |
| Fallgeschwindigkeit | 0,85 | 0,10–2,5; Schritt 0,05 |
| Deckkraft | 0,60 | 0,10–1,0; Schritt 0,05 |
| Farbe | Hellblau | Farbauswahl |

## Verhalten und Grenzen

- Zeichnet nur während einer aktiven Welt und blendet sich in Menüs aus.
- Die Animation wird lokal aus der Zeit und festen Tropfen-Seeds berechnet; es werden keine Partikel an Server oder Welt gesendet.
- Der Effekt ist ein Bildschirm-Overlay und keine echte 3D-Wetterdarstellung.

## Offene Tests

- [ ] Dichte und Geschwindigkeit bei verschiedenen FPS
- [ ] Farb- und Deckkraft-Einstellungen
- [ ] Ausblenden in Inventar und Menüs
- [ ] Build und In-Game-Test

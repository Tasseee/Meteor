# TargetMarker

- **Status:** Code geprüft; HUD-Test offen
- **Java-Klasse / Anzeigename:** `TargetMarker`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `8c49ef8603bbda72e1957659f0fc6d5d1299e70b`

## Zweck und Verhalten

Zeigt eine zusätzliche HUD-Markierung für die Entity direkt unter dem normalen Fadenkreuz. Es wird kein Ziel gesucht oder automatisch ausgewählt. Spieler selbst, tote Entities, geöffnete Screens und verborgenes HUD werden ignoriert.

## Einstellungen

| Einstellung | Standard | Optionen |
|---|---|---|
| Stil | Karte | Karte, Kompakt, Fadenkreuz |
| Akzentfarbe | RGB 9133302 | RGB |
| Distanz anzeigen | an | Boolesch |
| Leben anzeigen | an | Boolesch |

## Technische Prüfung

- **Zielquelle:** `mc.targetedEntity`, nicht eine eigene Zielauswahl.
- **Nullfälle:** Spieler, Welt, Target, Screen und HUD-Sichtbarkeit werden geprüft.
- **Label:** Entity-Name; Leben nur bei LivingEntity; Distanz mit `Locale.ROOT`.
- **Kamera:** Kamera-Position wird verwendet, mit Spielerposition als Fallback.
- **Ressourcen/Lifecycle:** keine eigenen Listener, Threads oder Ressourcen.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein eindeutiger Funktionsfehler bestätigt.
- Die Einstellung `Leben anzeigen` wirkt nur bei LivingEntities; bei anderen Entity-Typen bleibt sie wirkungslos. Das ist erwartbar, könnte aber im GUI klarer dargestellt werden.
- Das Modul ist eine Anzeige des Vanilla-Fadenkreuz-Ziels und keine Kampffunktion.

## Offene Tests

- [ ] Karte/Kompakt/Fadenkreuz
- [ ] Distanz-/Leben-Anzeige
- [ ] Nicht-LivingEntity
- [ ] Screen offen / HUD verborgen
- [ ] Kamera-Fallback

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | TargetMarker codebasiert geprüft; keine Java-Änderung. |

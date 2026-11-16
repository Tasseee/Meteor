# OreESP

- **Status:** Code geprüft; Leistungstest/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `OreESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `90755495544b0652adc9d1981dc0b2a20e4dca2c`

## Zweck und Verhalten

Markiert Blöcke, deren Registry-ID auf `_ore` endet, sowie Ancient Debris. Netherquarz und Nethergold werden ausdrücklich ausgeschlossen. Die Suche ist auf bereits geladene Chunks beschränkt.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Reichweite | 48 | 8–96; Schritt 4 |
| Max Markierungen | 500 | 20–1000; Schritt 20 |

## Technische Prüfung

- **Scanintervall:** bei `timer-- > 0`, danach `timer=12`; Folge-Scans liegen 13 geeignete Ticks auseinander.
- **Suchraum:** achsenparalleler X/Y/Z-Würfel innerhalb der Welt-Höhenlimits.
- **Chunk-Sicherheit:** ungeladene Chunks werden übersprungen; es werden keine Chunks nachgeladen.
- **Treffergrenze:** `Max Markierungen` begrenzt gespeicherte Treffer, nicht die Zahl untersuchter Blockpositionen.
- **Lifecycle:** Trefferliste wird bei Deaktivierung und Weltwechsel geleert.
- **Performance:** bei Reichweite 96 und wenigen Treffern kann ein großer Blockraum pro Scan geprüft werden.
- **Build/In-Game:** nicht ausgeführt.

## Nutzerbeobachtung

- **Server:** HugoSMP.
- **Bericht:** Erze würden laut Nutzer erst angezeigt, wenn man direkt davor steht.
- **Status:** Unbestätigt; keine Screenshots, Logs oder Testbedingungen vorhanden.
- **Namenszuordnung:** Nutzer sagte „OreFinder“; registriert ist aktuell `OreESP`. Die Gleichsetzung ist plausibel, aber nicht bestätigt.

## Befunde und Grenzen

- Der Code durchsucht Blockzustände in geladenen Chunks, statt nur einen Block direkt vor dem Spieler zu prüfen. Ob das beobachtete Verhalten aus geladenen Chunks, Einstellungen, Anzeigeprojektion oder Serverkontext entsteht, ist ohne In-Game-Beleg offen.
- Der Suchalgorithmus und die Erkennungsleistung werden nicht optimiert, um das Auffinden versteckter Ressourcen auf Multiplayer-Servern zu verbessern.
- Performance-Risiko ist dokumentiert; keine Scan-Änderung vorgenommen.

## Offene Tests

- [ ] lokale Testwelt mit bekannten Erzpositionen
- [ ] Range 8/96 und Markierungscap 20/1000
- [ ] Chunk-Grenzen und ungeladene Chunks
- [ ] HUD-Projektion bei verschiedenen Blickrichtungen
- [ ] Bericht auf HugoSMP mit Einstellungen/Version/Beleg

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | OreESP geprüft; Nutzerbeobachtung als unbestätigt erhalten. |

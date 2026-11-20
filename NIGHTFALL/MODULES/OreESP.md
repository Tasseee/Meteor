# OreESP

- **Status:** Visuelle Überarbeitung eingepflegt; Build/In-Game-Test offen
- **Java-Klasse / Anzeigename:** `OreESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `e7a4751e12e7406e89cd6f78b8489e4c06ed0b57`

## Zweck und Verhalten

Markiert Blöcke, deren Registry-ID auf `_ore` endet, sowie Ancient Debris. Netherquarz und Nethergold werden ausdrücklich ausgeschlossen. Die Suche bleibt auf bereits geladene Chunks beschränkt.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Reichweite | 48 | 8–96; Schritt 4 |
| Max Erzblöcke pro Scan | 500 | 20–1000; Schritt 20 |

## Darstellung

Erztypen haben jetzt eigene Rahmenfarben:
- Diamant: Türkis
- Smaragd: Grün
- Gold: Gelb
- Eisen: Hellgrau
- Kupfer: Orange
- Lapislazuli: Blau
- Redstone: Rot
- Kohle: Grau
- Ancient Debris: Violett

Jede zusammenhängende Ader erhält eine einzige Box, deren Grenzen die äußersten Blöcke dieser Ader umschließen. Die Gruppierung folgt 6er-Nachbarschaft (Flächenkontakt) und derselben Erzart. Der letzte gefundene Ader-Cluster wird vollständig abgeschlossen, auch wenn er das eingestellte Blocklimit überschreitet. Die Farbzuordnung ändert nicht, welche Blöcke gesucht werden.

## Technische Prüfung

- **Scanintervall:** bei `timer-- > 0`, danach `timer=12`; Folge-Scans liegen 13 geeignete Ticks auseinander.
- **Suchraum:** achsenparalleler X/Y/Z-Würfel innerhalb der Welt-Höhenlimits.
- **Chunk-Sicherheit:** ungeladene Chunks werden übersprungen; es werden keine Chunks nachgeladen.
- **Treffergrenze:** `Max Erzblöcke pro Scan` begrenzt die Anzahl der in Vein-Clustern erfassten Erzblöcke; ein bereits begonnener Cluster wird vollständig abgeschlossen.
- **Lifecycle:** Trefferliste wird bei Deaktivierung und Weltwechsel geleert.
- **Performance:** bei Reichweite 96 und wenigen Treffern kann ein großer Blockraum pro Scan geprüft werden.
- **Build/In-Game:** noch nicht ausgeführt.

## Nutzerbeobachtung

- **Server:** HugoSMP.
- **Bericht:** Erze würden laut Nutzer erst angezeigt, wenn man direkt davor steht.
- **Status:** Unbestätigt; keine Screenshots, Logs oder Testbedingungen vorhanden.
- **Namenszuordnung:** Nutzer sagte „OreFinder“; registriert ist aktuell `OreESP`. Die Gleichsetzung ist plausibel, aber nicht bestätigt.

## Befunde und Grenzen

- Der Code durchsucht Blockzustände in geladenen Chunks, statt nur einen Block direkt vor dem Spieler zu prüfen. Ob das beobachtete Verhalten aus geladenen Chunks, Einstellungen, Anzeigeprojektion oder Serverkontext entsteht, ist ohne In-Game-Beleg offen.
- Die Vein-Gruppierung ergänzt den bestehenden Scan. Eine Box bildet jeweils die achsenparallelen Grenzen eines zusammenhängenden Clusters derselben Erzart ab. Bei nicht geladenen angrenzenden Chunks kann die Ader nicht über diese Grenze hinaus vervollständigt werden.
- Keine Umgehung von Server-Schutzmechanismen wurde eingebaut.

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
| 2026-11-19 | Erztypen farblich unterschieden und Rahmenstärke auf 2 erhöht. |\n| 2026-11-20 | Zusammenhängende Erzblöcke derselben Sorte werden zu einer gemeinsamen Vein-Box gruppiert. |

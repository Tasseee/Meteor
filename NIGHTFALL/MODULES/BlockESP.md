# BlockESP

- **Status:** Code geprüft; Leistungstest offen
- **Java-Klasse / Anzeigename:** `BlockESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `4c4c6a066fcbe4f4505464fe34f3634714798b26`

## Zweck und Verhalten

Durchsucht geladene Chunks im Umkreis um den Spieler nach auswählbaren Blocktypen und zeichnet Treffer als Boxen im HUD-Projektionspfad. Es sucht nicht nach allen Blöcken, sondern nur nach den aktivierten Kategorien.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Reichweite | 64 | 8–64; Schritt 4 |
| Max Markierungen | 600 | 20–600; Schritt 20 |
| Diamanterz | an | Boolesch |
| Antiker Schrott | an | Boolesch |
| Kisten | an | Boolesch |
| Spawner | an | Boolesch |
| Obsidian | aus | Boolesch |
| Golderz | aus | Boolesch |
| Smaragderz | aus | Boolesch |

## Technische Prüfung

- **Scanintervall:** `timer-- <= 0`, danach `timer = 12`; damit liegen aktuell 13 geeignete Ticks zwischen Scans.
- **Chunk-Sicherheit:** Es werden nur bereits geladene Chunks abgefragt.
- **Suchraum:** X/Y/Z werden als achsenparalleler Würfel um die Spielerposition gebildet; ein zusätzlicher Kugel-Distanzfilter fehlt.
- **Treffergrenze:** `Max Markierungen` begrenzt Treffer, nicht die Anzahl untersuchter Blockpositionen.
- **Worst-Case-Arbeit:** Bei Reichweite 64 kann der Würfel bis zu 129×129×129 Blockpositionen umfassen, sofern die entsprechenden Chunks geladen sind und kaum Treffer die frühe Abbruchgrenze erreichen.
- **Lifecycle:** Trefferliste wird beim Deaktivieren und Weltwechsel geleert.
- **In-Game/Build:** nicht ausgeführt.

## Befunde und Verbesserungen

### BE-01 – potenziell hohe Scanlast

Die Treffergrenze reduziert die Zahl gespeicherter Markierungen, aber nicht den Aufwand, wenn nur wenige Treffer vorhanden sind. Die maximale Reichweite kann daher viele Blockabfragen in einem einzelnen Tick auslösen. Das ist ein belegter Performance-Risikopunkt; ohne Profiling wird keine konkrete FPS-Einbuße behauptet.

**Keine Scan-Optimierung vorgenommen:** Die Verbesserung der Block-/Ressourcen-Erkennung könnte die Nutzung auf Multiplayer-Servern unfair verstärken. Hier bleibt es bei Dokumentation und einem Performance-Risikohinweis.

### BE-02 – Reichweite ist würfelförmig

Treffer in den Ecken des Würfels können weiter als die eingestellte Distanz vom Spieler entfernt sein. Ob das gewollt ist, muss anhand der UI-Erwartung geprüft werden.

## Offene Tests

- [ ] Reichweite 8/64
- [ ] Max Markierungen 20/600
- [ ] Geladene und ungeladene Chunk-Grenzen
- [ ] CPU-/FPS-Profiling in einer lokalen Testwelt
- [ ] Filteroptionen einzeln

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Erste Codeprüfung; keine Java-Änderung. |

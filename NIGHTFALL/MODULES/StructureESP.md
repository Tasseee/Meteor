# StructureESP

- **Status:** Code geprüft; Leistungstest offen
- **Java-Klasse / Anzeigename:** `StructureESP`
- **Kategorie:** `VISUALS`
- **Quellcode-SHA:** `2d9dd0757d0353139c06333b9436738b1e2995cc`

## Zweck und Verhalten

Markiert ausgewählte Blocktypen in bereits geladenen Chunks: Spawner, Portalrahmen, Trial-Spawner, Kisten, verdächtige Blöcke, Fässer/Endertruhen/Trapped Chests, Dekotöpfe, Bienenstöcke, End-Gateways, Shulkerboxen und Vaults. Es werden keine Chunks nachgeladen.

## Einstellungen

| Einstellung | Standard | Grenzen |
|---|---:|---|
| Reichweite | 48 | 8–96; Schritt 8 |
| Spawner | an | Boolesch |
| Portalrahmen | an | Boolesch |
| Trial-Spawner | an | Boolesch |
| Kisten | an | Boolesch |
| Verdächtige Blöcke | an | Boolesch |
| Weitere Truhen und Fässer | an | Boolesch |
| Dekotöpfe | an | Boolesch |
| Bienenstöcke | an | Boolesch |
| End-Gateways | an | Boolesch |
| Shulkerboxen | an | Boolesch |
| Tresore | an | Boolesch |

## Technische Prüfung

- **Scanintervall:** `timer-- <= 0`, danach `timer=30`; Folge-Scans liegen damit 31 geeignete Ticks auseinander.
- **Trefferlimit:** maximal 160 Boxen, nicht maximal 160 untersuchte Blockpositionen.
- **Suchraum:** achsenparalleler Würfel auf X/Z/Y, begrenzt auf Welt-Höhenbereich.
- **Chunk-Sicherheit:** nur bereits geladene Chunks werden untersucht.
- **Lifecycle:** Positionen werden bei Deaktivierung und Weltwechsel geleert; Timer wird bei Weltwechsel zurückgesetzt.
- **Performance:** bei geringer Trefferzahl kann der Scan trotz Trefferlimit viele Blockzustände prüfen; Reichweite 96 entspricht bis zu 193³ Koordinaten vor geladenen Chunk- und Cap-Abbruchbedingungen.
- **Build/In-Game:** nicht ausgeführt.

## Befunde und Verbesserungen

- Kein klarer Fehler im Trefferfilter bestätigt.
- Das Verhältnis zwischen Trefferlimit und tatsächlich untersuchten Positionen ist eine Performance-Risikoquelle.
- Keine Verbesserung der Struktur-/Spawner-Erkennung oder Server-Schutzumgehung vorgenommen.

## Offene Tests

- [ ] alle Filter einzeln
- [ ] Reichweite 8/96
- [ ] 160-Treffer-Cap
- [ ] ungeladene Chunk-Grenzen
- [ ] Clienttick-/FPS-Profiling in lokaler Testwelt

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | StructureESP codebasiert geprüft; keine Java-Änderung. |

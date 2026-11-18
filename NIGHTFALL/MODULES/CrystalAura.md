# CrystalAura

- **Registrierter Modulname:** `CrystalAura`
- **Prüfstatus:** Statisch anhand des aktuellen Quellcodes geprüft; In-Game-Test offen
- **Quellpfad:** `src/main/java/dev/nightfall/modules/CrystalAura.java`

## Vereinfachtes Verhalten (2026-11-18)

- Das Modul hat nur noch einen klaren Ablauf: nahe End-Crystals zerstören und neue Crystals auf gültigen Obsidian-/Bedrock-Blöcken platzieren.
- Die frühere automatische Obsidian-Platzierung nach einem Treffer, Zielhöhen-Checks, Mehrfachplatzierungen pro Tick und zugehörige Zustandsfelder wurden entfernt.
- Die Modulausführung enthält keine direkte Abfrage oder Pause wegen AutoPvP. CrystalAura läuft eigenständig.
- Beim Verlassen der Welt und beim Deaktivieren werden beide Tick-Timer zurückgesetzt.
- Der Hotbar-Slot wird nach einem temporären Auto-Switch auch bei einem fehlgeschlagenen Platzier-Versuch wiederhergestellt.

## Einstellungen

| Einstellung | Standard |
|---|---:|
| Ziel-Reichweite | 10 Blöcke |
| Platzier-Reichweite | 4,5 Blöcke |
| Zerstör-Reichweite | 4,5 Blöcke |
| Platzier-Delay | 0 Ticks |
| Zerstör-Delay | 0 Ticks |
| Platzieren | AN |
| Zerstören | AN |
| Auto-Switch | AN |

## Prüfgrenze

Die Prüfung ist statisch. Serverreaktion, tatsächliches Platzieren/Zerstören, Timing und Kompatibilität mit anderen Combat-Modulen wurden nicht im Spiel getestet. CrystalAura baut kein Obsidian mehr automatisch; es setzt vorhandenes Obsidian bzw. Bedrock voraus.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-18 | CrystalAura auf eigenständige Place-/Break-Funktion vereinfacht; alte Auto-Obsidian-Combo-Logik entfernt |

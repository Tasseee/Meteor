# AutoPvp

- **Registrierter Modulname:** `AutoPvp`
- **Auditstatus:** Teilweise geprüft – nur Namen, Beschreibungen und sichtbare Einstellungslabels
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoPvp.java`
- **Quell-Blob-SHA:** `fff2714e8303093276f561fb66251f46958ae2a7`

## Getrennte Bezeichnungen

- **AutoPvP:** eigenes registriertes Modul.
- **Windcharge -> Mace:** eigenständige sichtbare Option in AutoPvP. Sie wird nicht als `Stun-Slam` bezeichnet.
- **Stun-Slam:** Die vom Nutzer beschriebene separate Funktion ist in der aktuellen Registrierung nicht als eigene Modulklasse bestätigt. Keine Kampfsequenz dafür ergänzen.
- **Pearl zum Gegner:** sichtbares Label der AutoPvP-Option; die frühere Codeprüfung ordnete sie als Wurf in Richtung des gegnerischen Landepunkts ein, nicht als Pearl-Fangfunktion.
- **Crystal-Combo:** sichtbares Label in den Einstellungen. Die Angriffs-/Ausführungslogik wurde nicht geprüft.

## Getrennte Module

`DTap` und `ObiTrapBot` sind separat in `ModuleManager.init()` registriert. Sie werden nicht in AutoPvP umbenannt oder zusammengeführt.

## Statusanzeigen

Der aktuelle `CombatStatusHud` zeigt keinen Crystal-/DTap-/ObiTrap-Status und keinen Boden-/Obsidian-Kontext. Das ist eine dokumentierte UI-Lücke, keine Änderung an Kampfverhalten. Eine Anzeigeänderung wurde in diesem Lauf nicht vorgenommen.

## Prüfgrenze und offene Punkte

- Sichtbare Namen/Labels wurden geprüft.
- Kampfabläufe, Zielauswahl, Aim, Timing, Crystal-/Trap-Ausführung und Combos wurden ausdrücklich nicht untersucht oder verändert.
- Ob die angezeigten Optionen im Spiel korrekt und verständlich wirken, ist nicht ingame verifiziert.

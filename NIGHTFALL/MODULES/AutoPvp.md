# AutoPvp

- **Registrierter Modulname:** `AutoPvp`
- **Auditstatus:** Teilweise geprüft – ausgewählte Timing- und Bewegungslogik wurde angepasst; In-Game-Test offen
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoPvp.java`
- **Quell-Blob-SHA:** `bf000407904390b8fa072385fe08167afa965d37`

## Getrennte Bezeichnungen

- **AutoPvP:** eigenes registriertes Modul.
- **Windcharge -> Mace:** eigenständige sichtbare Option in AutoPvP. Sie wird nicht als `Stun-Slam` bezeichnet.
- **Stun-Slam:** Separate, vom Nutzer definierte Funktion: Axt-Schildbruch, danach ein Mace-Treffer in der Luft. Eine eigene Implementierung/Registrierung ist im aktuellen Modulbestand nicht bestätigt. Diese Definition wird nur für Namen und Dokumentation festgehalten; keine Ausführungssequenz ergänzen.
- **Pearl zum Gegner:** sichtbares Label der AutoPvP-Option; die frühere Codeprüfung ordnete sie als Wurf in Richtung des gegnerischen Landepunkts ein, nicht als Pearl-Fangfunktion.
- **Crystal-Combo:** sichtbares Label in den Einstellungen. Die Angriffs-/Ausführungslogik wurde nicht geprüft.

## Getrennte Module

`DTap` und `ObiTrapBot` sind separat in `ModuleManager.init()` registriert. Sie werden nicht in AutoPvP umbenannt oder zusammengeführt.

## Statusanzeigen

Der aktuelle `CombatStatusHud` zeigt keinen Crystal-/DTap-/ObiTrap-Status und keinen Boden-/Obsidian-Kontext. Das ist eine dokumentierte UI-Lücke, keine Änderung an Kampfverhalten. Eine Anzeigeänderung wurde in diesem Lauf nicht vorgenommen.

## Prüfgrenze und offene Punkte

- Sichtbare Namen/Labels wurden geprüft.
## Crit-Timing und Bewegung (2026-11-17)

- `Schwert-Crits ohne CrystalAura` bleibt als vorhandener Config-Schlüssel erhalten. Die Logik bereitet Schwert-Crits nun auch dann vor, wenn die CrystalAura-Integration eingeschaltet ist; eine bereits laufende Crystal-Combo übernimmt weiterhin zuerst.
- Im Crit-Fenster wird Sprint freigegeben. AutoPvP wartet am Boden auf mindestens 98 % Angriffscooldown, zählt anschließend den konfigurierbaren `Crit-Sprungdelay` herunter (Standard 2 Ticks, Bereich 0–6), startet den Sprung und hält die Angriffsauslösung zurück, bis der Spieler tatsächlich fällt (`fallDistance > 0.08`, vertikale Geschwindigkeit < 0) und der Angriffscooldown mindestens 90 % beträgt.
- Kritische Sprünge werden bei blockierter Decke bzw. Obsidian im Laufweg nicht vorbereitet. Außerhalb des Crit-Fensters bleibt die bisherige Hindernis-Sprunglogik bestehen.
- `An Gegner vorbeilaufen` ist standardmäßig aktiviert. Im Nahbereich (mehr als 0,35 Blöcke, bis `Angriffsreichweite + 0.8`) wechselt die seitliche Bewegung standardmäßig alle 2 Ticks; `Drive-by Seitenwechsel` ist zwischen 1 und 4 Ticks einstellbar. Währenddessen bleibt Vorwärtsinput auch unterhalb des eingestellten Stop-Abstands aktiv, solange der Block-/Bodencheck den Weg freigibt.
- Das ergibt schnelle Links-rechts-Zickzackbewegung mit weiterem Druck nach vorne, um am Ziel vorbeizukommen. Die Minecraft-Spielerkollision bleibt aktiv; echtes NoClip oder garantiertes Durchlaufen durch den Spielerkörper ist nicht möglich.
- Die Werte wurden statisch im Code geprüft. Hit-Timing, serverseitige Bewegung und Spielgefühl müssen noch auf einem Testserver/In-Game überprüft werden.

## Prüfgrenze und offene Punkte

- Zielauswahl und bestehende Crystal-/Trap-Combos wurden nicht neu gestaltet.
- Build und In-Game-Test für die neue Crit-/Drive-by-Logik sind offen.
- Die vorhandene Einstellung `Schwert-Crits ohne CrystalAura` behält ihren gespeicherten Namen zur Config-Kompatibilität; das Verhalten umfasst nun auch den integrierten CrystalAura-Betrieb.

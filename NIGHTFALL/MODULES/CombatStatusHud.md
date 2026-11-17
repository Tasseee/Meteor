# CombatStatusHud

- **Registrierter Modulname:** `CombatStatusHud`
- **Auditstatus:** Teilweise geprüft – reine Anzeige
- **Quellpfad:** `src/main/java/dev/nightfall/modules/CombatStatusHud.java`
- **Quell-Blob-SHA:** `b1ea23febdae758a5867e2766ce5185beb304a39`

## Aktuell angezeigte Informationen

Die HUD-Zeile bezieht das Ziel aus `TrapCrit` oder `KillAura`. Sie zeigt je nach Zustand Zielname bzw. Leerlauftext, Distanz, `TRAP`/`frei` oder `AURA`, den Crit-Hinweis und eine Cooldown-Leiste. Zusätzlich kann eine Mace-Schadensschätzung erscheinen.

## Sichtbare Lücke

Im aktuellen HUD-Code werden weder CrystalAura/Crystal-Kontext noch DTap oder ObiTrapBot als Statusquelle angezeigt. Daher gibt es dort aktuell auch keine Unterscheidung zwischen Boden-/Standflächen-Kontext und Obsidian-Kontext. Das ist nur eine Bestandsaufnahme; es wurde keine neue Statuslogik hinzugefügt.

## Prüfgrenze

Nur die Anzeige wurde betrachtet. Ziel-/Angriffslogik der referenzierten Module wurde nicht geprüft oder verändert.

## Offen

- [ ] Im laufenden Client Textbreite, Überlappungen und Layout prüfen.
- [ ] Anforderungen an nicht-angreifende Statusanzeigen separat festlegen.
- [ ] Kein In-Game-Test in diesem Lauf.

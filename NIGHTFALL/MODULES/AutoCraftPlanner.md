# AutoCraftPlanner

- **Status:** Code geprüft; Build-/In-Game-Test offen
- **Java-Klasse:** `AutoCraftPlanner`
- **Anzeigename:** `AutoCraft Planner`
- **Kategorie:** `WORLD`
- **Quellpfad:** `src/main/java/dev/nightfall/modules/AutoCraftPlanner.java`
- **Aktueller Quellcode-SHA:** `498350f8cfc5a655a60b1aec5a164f422c759a85`
- **Registrierung:** `ModuleManager.init()`

## Zweck und Verhalten

Das Modul prüft eine manuell eingegebene Zutatenliste gegen das Spielerinventar und zeigt den Fortschritt im HUD. Es craftet nicht automatisch.

Das Format lautet `item-id:menge`; mehrere Einträge werden mit Kommas getrennt. Mengen von 1 bis 64 werden akzeptiert. Ungültige Mengen, nicht parsebare IDs und AIR werden ignoriert. Das Inventar wird über alle Slots durchsucht.

## Einstellungen

| Einstellung | Standard | Verhalten |
|---|---|---|
| Zutaten (ID:Menge, Komma) | `minecraft:oak_planks:4,minecraft:stick:2` | Zutatenliste; maximal 256 Zeichen |
| HUD anzeigen | `true` | Schaltet die Anzeige ein/aus |

## Technische Prüfung

- Registrierung/Kategorie: bestätigt, `WORLD`.
- HUD: fester Startpunkt (8, 96), zeigt erfüllte/gesamte Anforderungen und Besitz-/Sollmenge.
- Nullfall: ohne Spieler leere Ergebnisliste; Rendering prüft Spieler zusätzlich.
- Lebenszyklus: keine eigenen Listener, Ressourcen oder Threads.
- Fehlerbehandlung: ungültige Listeneinträge werden still übersprungen.
- Performance: Anforderungen werden vor der Inventarsuche zusammengefasst; Inventar wird pro eindeutiger Item-ID einmal durchlaufen.
- Laufzeit/Version: Build und In-Game-Test stehen noch aus.

## Befunde und Verbesserungen

### AC-01 – Doppelte Zutaten zusammenfassen

**Behoben im Code:** `10ed170f88f7fb17f991feb319924c28c1c92ebb`.

Gleiche normalisierte Item-IDs werden jetzt vor dem Inventarvergleich aggregiert. Zum Beispiel wird zweimal `minecraft:iron_ingot:4` als Gesamtbedarf 8 behandelt statt als zwei voneinander unabhängige Anforderungen von je 4. Die Einfügereihenfolge bleibt durch `LinkedHashMap` stabil.

**Verifikation:** Änderung und Quellcode wurden auf GitHub gespeichert und anschließend gelesen. Build- und In-Game-Test sind noch offen.

### AC-02 – Parser-Rückmeldung

Fehlerhafte Einträge werden weiterhin still ignoriert. Ein späterer Hinweis auf ungültige Listeneinträge wäre eine sinnvolle UI-Verbesserung.

## Nutzer- und In-Game-Tests

Noch keine In-Game-Ergebnisse. Offen: Duplikate mit/ohne Namespace, unbekannte Item-IDs, Leerzeichen, ungültige Mengen und Eingabe am 256-Zeichen-Limit.

## Änderungsverlauf

| Datum | Änderung |
|---|---|
| 2026-11-16 | Codeprüfung und Zusammenfassung doppelter Zutaten; Build-/In-Game-Test offen. |

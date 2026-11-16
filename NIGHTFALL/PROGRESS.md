# Nightfall – Fortschritt

**Inventur-Baseline:** `21dc9f822a6295cdb6238037f344da799bbcca26` (Branch `main`, 2026-11-16)  
**Letzte Nightfall-Prüfungen:** `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud` und `AutoBreed` (2026-11-16)  
**Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`  
**SHA der Registrierungsquelle:** `03f98bfae608c6d16bfd88be797fc41bd4dfb24c`  
**Bisheriges Audit-Ledger:** `MODULE_AUDIT_PROGRESS.md`  
**SHA des Audit-Ledgers:** `bfff728b74b82e6d92ebf18db72e017bc4bb43f2`

## Audit-Protokoll

Aggregierte Statistiken werden auf Wunsch des Nutzers nicht weiter gepflegt. Die Modulakten und der Modulindex sind die maßgeblichen Einzelstände.

**Wichtig:** Die 22 vollständigen und 6 teilweisen Prüfstände sind übernommene frühere Auditstände. Im aktuellen Nightfall-Arbeitslauf wurden die oben aufgeführten acht Module anhand des Quellcodes geprüft. Für sie stehen Build- und In-Game-Tests noch aus. `AutoCraftPlanner` wurde um die Zusammenfassung doppelter Zutaten erweitert; `DroppedItemNotifier` verwendet seine temporäre UUID-Sammlung wieder. Die 101 registrierten Einträge wurden mit `ModuleManager.init()` abgeglichen. Die fünf Einträge `TextHuds.*` sind eigenständige registrierte Unterklassen und teilen sich `TextHuds.java`.

## Einrichtung in diesem Commit

- [x] Aktuellen `main`-Stand geprüft.
- [x] Registrierte Module aus `ModuleManager.init()` ermittelt.
- [x] Bisherige Prüfstände aus `MODULE_AUDIT_PROGRESS.md` übernommen.
- [x] Für jedes registrierte Modul eine eigene Startakte angelegt.
- [x] Test-, Beleg- und Fehlerberichtsvorlagen angelegt.
- [ ] In-Game-Tests neu durchgeführt – nicht Teil der Dokumentationsinitialisierung.
- [x] `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud` und `AutoBreed` anhand des aktuellen Quellcodes geprüft und dokumentiert.
- [x] Doppelte Zutaten im `AutoCraftPlanner` zusammengefasst.
- [x] UUID-Arbeitsmenge im `DroppedItemNotifier` wiederverwendet.
- [ ] Build- und In-Game-Tests für diese Module – offen.
- [ ] Weitere Modulakten neu inhaltlich prüfen – fortlaufende Aufgabe.

## Nächste Schritte

1. Build- und In-Game-Tests für die bereits geprüften Module nachholen.
2. Weitere nicht kampfbezogene Module einzeln untersuchen und belegte Code-Funde dokumentieren.
3. Echte Nutzerberichte und Testergebnisse als separate Testberichte speichern.
4. Bestätigte, zulässige Verbesserungen priorisieren, isoliert umsetzen und nachweisen.

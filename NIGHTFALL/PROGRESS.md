# Nightfall – Fortschritt

**Inventur-Baseline:** `21dc9f822a6295cdb6238037f344da799bbcca26` (Branch `main`, 2026-11-16)  
**Letzte Nightfall-Prüfungen:** `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud`, `AutoBreed`, `BlockNotifier`, `FavoriteChunk`, `FirstPersonStyle`, `Hats`, `ItemGlint`, `MovementParticles`, `ParticleTrail`, `SkyVisuals`, `BlockESP`, `ChunkEsp`, `ItemEsp`, `StorageEsp`, `EntityEsp`, `PlayerEsp`, `SpawnerEsp`, `AutoFish` und `AutoSign`, `GUITheme`, `FriendManager`, `HitParticles`, `JumpCircles`, `Minimap`, `ModuleList` und `ScannerHud`, `FakeStats`, `GlowESP`, `SafeWalk`, `Step`, `WeaponAnimations`, `WorldLyrics`, `SpotifyLyricsHud`, `AutoFarm` und `LootDropFinder`, `FreeLook`, `Freecam`, `TargetMarker`, `StructureESP`, `OreESP`, `StaffWatch` und `SpawnerProtect`, `BoatFly`, `ElytraFly`, `Fly`, `Jesus`, `LongJump`, `NoFall`, `Speed`, `Sprint` und `WaterWalk` (2026-11-16)  
**Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`  
**SHA der Registrierungsquelle:** `03f98bfae608c6d16bfd88be797fc41bd4dfb24c`  
**Bisheriges Audit-Ledger:** `MODULE_AUDIT_PROGRESS.md`  
**SHA des Audit-Ledgers:** `bfff728b74b82e6d92ebf18db72e017bc4bb43f2`

## Audit-Protokoll

Aggregierte Statistiken werden auf Wunsch des Nutzers nicht weiter gepflegt. Die Modulakten und der Modulindex sind die maßgeblichen Einzelstände.

**Wichtig:** Die 22 vollständigen und 6 teilweisen Prüfstände sind übernommene frühere Auditstände. Im aktuellen Nightfall-Arbeitslauf wurden die oben aufgeführten Module anhand des Quellcodes geprüft. Für sie stehen Build- und In-Game-Tests noch aus. `AutoCraftPlanner` fasst doppelte Zutaten zusammen; `DroppedItemNotifier` verwendet seine temporäre UUID-Sammlung wieder; `BlockNotifier` hat ein korrigiertes Scanintervall und verwendet seine Positionssammlung wieder; `EspBase` verwendet jetzt die übergebenen Standardreichweiten; `AutoFish` unterstützt die Nebenhand, wenn dies freigegeben ist; `Minimap` stellt Matrix und Scissor-Clip nach Renderfehlern wieder her; `WorldLyrics` stellt nach Renderfehlern seine Transformationsmatrix wieder her; `SpotifyLyricsHud` schließt seinen OAuth-Executor und verarbeitet mehrere LRC-Zeitmarken; `AutoFarm` stellt den Hotbar-Slot auch bei Interaktionsfehlern wieder her; `LootDropFinder` bereinigt abgelaufene Marker-Höhendaten; `FreeLook` verwendet Cursor-Arrays wieder; `SpawnerProtect` löscht alte Spawnerpositionen beim Weltwechsel. Die 101 registrierten Einträge wurden mit `ModuleManager.init()` abgeglichen. Die fünf Einträge `TextHuds.*` sind eigenständige registrierte Unterklassen und teilen sich `TextHuds.java`.

## Einrichtung in diesem Commit

- [x] Aktuellen `main`-Stand geprüft.
- [x] Registrierte Module aus `ModuleManager.init()` ermittelt.
- [x] Bisherige Prüfstände aus `MODULE_AUDIT_PROGRESS.md` übernommen.
- [x] Für jedes registrierte Modul eine eigene Startakte angelegt.
- [x] Test-, Beleg- und Fehlerberichtsvorlagen angelegt.
- [ ] In-Game-Tests neu durchgeführt – nicht Teil der Dokumentationsinitialisierung.
- [x] Die in der Kopfzeile aufgelisteten Modulakten anhand des aktuellen Quellcodes geprüft und dokumentiert.
- [x] Doppelte Zutaten im `AutoCraftPlanner` zusammengefasst.
- [x] UUID-Arbeitsmenge im `DroppedItemNotifier` wiederverwendet.
- [x] Scanintervall und temporäre Positionsmenge in `BlockNotifier` korrigiert.
- [x] ESP-Standardreichweiten in `EspBase` korrigiert.
- [x] Handauswahl für `AutoFish` korrigiert.
- [x] Matrix-/Clip-Cleanup in `Minimap` abgesichert.
- [x] Matrix-Cleanup beim Text-Rendering in `WorldLyrics` abgesichert.
- [x] OAuth-Callback-/LRC-Parser in `SpotifyLyricsHud` verbessert.
- [x] Hotbar-Cleanup in `AutoFarm` abgesichert.
- [x] Marker-Höhendaten in `LootDropFinder` beim Ablauf bereinigt.
- [x] Cursor-Arrays in `FreeLook` wiederverwendet.
- [x] Track-Zustand in `SpawnerProtect` beim Weltwechsel zurückgesetzt.
- [ ] Build- und In-Game-Tests für die oben genannten Module – offen; Bewegungsmodul-Logik bewusst nicht angepasst.
- [ ] Weitere Modulakten neu inhaltlich prüfen – fortlaufende Aufgabe.

## Nächste Schritte

1. Build- und In-Game-Tests für die bereits geprüften Module nachholen.
2. Weitere nicht kampfbezogene Module einzeln untersuchen und belegte Code-Funde dokumentieren.
3. Echte Nutzerberichte und Testergebnisse als separate Testberichte speichern.
4. Bestätigte, zulässige Verbesserungen priorisieren, isoliert umsetzen und nachweisen.

# Nightfall – Fortschritt

**Inventur-Baseline:** `21dc9f822a6295cdb6238037f344da799bbcca26` (Branch `main`, 2026-11-16)  
**Nutzungsanzeige:** `MODULE_USAGE.md` – lokale Aktivierungszahl und aktive Zeit in jeder ClickGUI-Modulzeile.  
**Letzte Nightfall-Prüfungen:** `BaseFinder`, `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud`, `AutoBreed`, `BlockNotifier`, `FavoriteChunk`, `FirstPersonStyle`, `Hats`, `ItemGlint`, `MovementParticles`, `ParticleTrail`, `SkyVisuals`, `BlockESP`, `ChunkEsp`, `ItemEsp`, `StorageEsp`, `EntityEsp`, `PlayerEsp`, `SpawnerEsp`, `AutoFish` und `AutoSign`, `GUITheme`, `FriendManager`, `HitParticles`, `JumpCircles`, `Minimap`, `ModuleList` und `ScannerHud`, `FakeStats`, `GlowESP`, `SafeWalk`, `Step`, `WeaponAnimations`, `WorldLyrics`, `SpotifyLyricsHud`, `AutoFarm` und `LootDropFinder`, `FreeLook`, `Freecam`, `TargetMarker`, `StructureESP`, `OreESP`, `StaffWatch`, `AntiVanishHints` und `SpawnerProtect`, `BoatFly`, `ElytraFly`, `Fly`, `Jesus`, `LongJump`, `NoFall`, `Speed`, `Sprint` und `WaterWalk` (2026-11-16)  
**Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`  
**SHA der Registrierungsquelle:** `03f98bfae608c6d16bfd88be797fc41bd4dfb24c`  
**Bisheriges Audit-Ledger:** `MODULE_AUDIT_PROGRESS.md`  
**SHA des Audit-Ledgers:** `bfff728b74b82e6d92ebf18db72e017bc4bb43f2`

## Audit-Protokoll

- **2026-11-16 – StaffWatch-Verbesserung:** `/nf staff add/del` speichert erfolgreiche Änderungen sofort; `AntiVanishHints` überspringt seine HUD-Ausgabe, solange `StaffWatch` aktiv ist, und prüft Tab-/Welt-Einträge defensiv auf fehlende Werte. `StaffWatch` ist jetzt ein verschieb- und skalierbares `HudModule`, zeigt TAB-/WELT-Status verständlicher an, aktualisiert Präsenzdaten pro Tick und nutzt für TAB `getListedPlayerListEntries()` statt der allgemeineren `getPlayerList()`-Methode. Der alleinige Tab-Status heißt `TAB/NETZ` und wird nicht als Unterservernachweis gewertet; gefundene Namen erscheinen im HUD mit ihrer originalen Profil-Schreibweise. Serverunterschiede für HUGSMP und DonutSMP bleiben bis zum In-Game-Abgleich offen. Details und offene Tests in `NIGHTFALL/MODULES/StaffWatch.md` und `NIGHTFALL/MODULES/AntiVanishHints.md`.
- **2026-11-16 – HUD-/AntiVanish-Upgrade:** StaffWatch bietet konfigurierbare Zeilenanzahl (3–12), Kompaktmodus, Sortierung sichtbarer Treffer und Legenden-Schalter. AntiVanishHints wurde als verschiebbares/skaliertes HudModule mit konfigurierbarem Zeilenlimit und Tick-basiertem Cache umgesetzt. Beide Module nutzen ausschließlich manuell eingetragene Namen und clientseitig gelistete Tab-Profile bzw. geladene Welt-Entities; fehlende Sichtbarkeit wird nicht als Vanish-Beweis interpretiert. Build- und In-Game-Tests offen.
- **2026-11-16 – Freecam-Maussteuerung:** Option `Vertikale Maus invertieren` ergänzt, Standard `AN`; horizontale Maussteuerung bleibt unverändert. Details und offene Tests in `NIGHTFALL/MODULES/Freecam.md`.

Aggregierte Statistiken werden auf Wunsch des Nutzers nicht weiter gepflegt. Die Modulakten und der Modulindex sind die maßgeblichen Einzelstände.

**Wichtig:** Die 22 vollständigen und 6 teilweisen Prüfstände sind übernommene frühere Auditstände. Im aktuellen Nightfall-Arbeitslauf wurden die oben aufgeführten Module anhand des Quellcodes geprüft. `BaseFinder` wurde zusätzlich mit Fokus auf Datenhaltung und Lifecycle geprüft. Für sie stehen Build- und In-Game-Tests noch aus. `AutoCraftPlanner` fasst doppelte Zutaten zusammen; `DroppedItemNotifier` verwendet seine temporäre UUID-Sammlung wieder; `BlockNotifier` hat ein korrigiertes Scanintervall und verwendet seine Positionssammlung wieder; `EspBase` verwendet jetzt die übergebenen Standardreichweiten; `AutoFish` unterstützt die Nebenhand, wenn dies freigegeben ist; `Minimap` stellt Matrix und Scissor-Clip nach Renderfehlern wieder her; `WorldLyrics` stellt nach Renderfehlern seine Transformationsmatrix wieder her; `SpotifyLyricsHud` schließt seinen OAuth-Executor und verarbeitet mehrere LRC-Zeitmarken; `AutoFarm` stellt den Hotbar-Slot auch bei Interaktionsfehlern wieder her; `LootDropFinder` bereinigt abgelaufene Marker-Höhendaten; `FreeLook` verwendet Cursor-Arrays wieder; `SpawnerProtect` löscht alte Spawnerpositionen beim Weltwechsel. Die 101 registrierten Einträge wurden mit `ModuleManager.init()` abgeglichen. Die fünf Einträge `TextHuds.*` sind eigenständige registrierte Unterklassen und teilen sich `TextHuds.java`.

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
- [x] `BaseFinder`: Markierungs- und Fake-Pattern-Dateien atomar speichern; ungültige Markierungseinträge beim Laden einzeln überspringen.
- [x] `ModuleUsageStats`: lokale Aktivierungszahl und aktive Dauer für alle Module; in jeder ClickGUI-Zeile sichtbar.
- [ ] Build- und In-Game-Tests für die oben genannten Module – offen. Die Freecam-Änderung betrifft nur die lokale vertikale Kamerasteuerung; horizontale Eingabe und serverseitige Bewegungslogik bleiben unverändert.
- [ ] Weitere Modulakten neu inhaltlich prüfen – fortlaufende Aufgabe.

## Nächste Schritte

1. Build und In-Game-Tests für Nutzungsanzeige und BaseFinder-Persistenzkorrekturen nachholen.
2. Weitere nicht kampfbezogene Module einzeln untersuchen und belegte Code-Funde dokumentieren.
3. Echte Nutzerberichte und Testergebnisse als separate Testberichte speichern.
4. Bestätigte, zulässige Verbesserungen priorisieren, isoliert umsetzen und nachweisen.

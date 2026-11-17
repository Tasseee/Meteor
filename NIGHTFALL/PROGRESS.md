# Nightfall – Fortschritt

**Aktueller Inventur-Abgleich:** `359cf23f0b4836bf47936c046c2c1c69759805e4` (Branch `main`, 2026-11-17)  
**Nutzungsanzeige:** `MODULE_USAGE.md` – lokale Aktivierungszahl und aktive Zeit in jeder ClickGUI-Modulzeile.  
**Letzte Nightfall-Prüfungen:** `BaseFinder`, `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud`, `AutoBreed`, `BlockNotifier`, `FavoriteChunk`, `FirstPersonStyle`, `Hats`, `ItemGlint`, `MovementParticles`, `ParticleTrail`, `SkyVisuals`, `BlockESP`, `ChunkEsp`, `ItemEsp`, `StorageEsp`, `EntityEsp`, `PlayerEsp`, `SpawnerEsp`, `AutoFish` und `AutoSign`, `GUITheme`, `FriendManager`, `HitParticles`, `JumpCircles`, `Minimap`, `ModuleList` und `ScannerHud`, `FakeStats`, `GlowESP`, `SafeWalk`, `Step`, `WeaponAnimations`, `WorldLyrics`, `SpotifyLyricsHud`, `AutoFarm` und `LootDropFinder`, `FreeLook`, `Freecam`, `TargetMarker`, `StructureESP`, `OreESP`, `StaffWatch`, `AntiVanishHints` und `SpawnerProtect`, `BoatFly`, `ElytraFly`, `Fly`, `Jesus`, `LongJump`, `NoFall`, `Speed`, `Sprint` und `WaterWalk` (2026-11-16)  
**Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`  
**SHA der Registrierungsquelle:** `7329859ce075c9708f749f30b00e904fdf523d6b`  
**Bisheriges Audit-Ledger:** `MODULE_AUDIT_PROGRESS.md`  
**SHA des Audit-Ledgers:** `281981cf98714c92b9e97b7fdcc0628eeafffbf6`

## Audit-Protokoll

- **2026-11-16 – StaffWatch-Verbesserung:** `/nf staff add/del` speichert erfolgreiche Änderungen sofort; `AntiVanishHints` überspringt seine HUD-Ausgabe, solange `StaffWatch` aktiv ist, und prüft Tab-/Welt-Einträge defensiv auf fehlende Werte. `StaffWatch` ist jetzt ein verschieb- und skalierbares `HudModule`, zeigt TAB-/WELT-Status verständlicher an, aktualisiert Präsenzdaten pro Tick und nutzt für TAB `getListedPlayerListEntries()` statt der allgemeineren `getPlayerList()`-Methode. Der alleinige Tab-Status heißt `TAB/NETZ` und wird nicht als Unterservernachweis gewertet; gefundene Namen erscheinen im HUD mit ihrer originalen Profil-Schreibweise. Serverunterschiede für HUGSMP und DonutSMP bleiben bis zum In-Game-Abgleich offen. Details und offene Tests in `NIGHTFALL/MODULES/StaffWatch.md` und `NIGHTFALL/MODULES/AntiVanishHints.md`.
- **2026-11-16 – HUD-/AntiVanish-Upgrade:** StaffWatch bietet konfigurierbare Zeilenanzahl (3–12), Kompaktmodus, Sortierung sichtbarer Treffer und Legenden-Schalter. AntiVanishHints wurde als verschiebbares/skaliertes HudModule mit konfigurierbarem Zeilenlimit und Tick-basiertem Cache umgesetzt. Beide Module nutzen ausschließlich manuell eingetragene Namen und clientseitig gelistete Tab-Profile bzw. geladene Welt-Entities; fehlende Sichtbarkeit wird nicht als Vanish-Beweis interpretiert. Build- und In-Game-Tests offen.
- **2026-11-16 – Freecam-Maussteuerung:** Option `Vertikale Maus invertieren` ergänzt, Standard `AN`; horizontale Maussteuerung bleibt unverändert. Details und offene Tests in `NIGHTFALL/MODULES/Freecam.md`.

Der vollständige Auditstatus wird in `MODULE_AUDIT_PROGRESS.md` und `MODULE_INDEX.md` gemeinsam gepflegt: 27 vollständig geprüft, 6 teilweise geprüft und 69 offen (102 Registrierungen). Codeprüfung allein bedeutet nicht automatisch vollständigen Auditabschluss.

**Wichtig:** Die 22 vollständigen und 6 teilweisen Prüfstände sind übernommene frühere Auditstände. Im aktuellen Nightfall-Arbeitslauf wurden die oben aufgeführten Module anhand des Quellcodes geprüft. `BaseFinder` wurde zusätzlich mit Fokus auf Datenhaltung und Lifecycle geprüft. Für sie stehen Build- und In-Game-Tests noch aus. `AutoCraftPlanner` fasst doppelte Zutaten zusammen; `DroppedItemNotifier` verwendet seine temporäre UUID-Sammlung wieder; `BlockNotifier` hat ein korrigiertes Scanintervall und verwendet seine Positionssammlung wieder; `EspBase` verwendet jetzt die übergebenen Standardreichweiten; `AutoFish` unterstützt die Nebenhand, wenn dies freigegeben ist; `Minimap` stellt Matrix und Scissor-Clip nach Renderfehlern wieder her; `WorldLyrics` stellt nach Renderfehlern seine Transformationsmatrix wieder her; `SpotifyLyricsHud` schließt seinen OAuth-Executor und verarbeitet mehrere LRC-Zeitmarken; `AutoFarm` stellt den Hotbar-Slot auch bei Interaktionsfehlern wieder her; `LootDropFinder` bereinigt abgelaufene Marker-Höhendaten; `FreeLook` verwendet Cursor-Arrays wieder; `SpawnerProtect` löscht alte Spawnerpositionen beim Weltwechsel. Die 102 registrierten Einträge wurden mit `ModuleManager.init()` abgeglichen. Der Abgleich hat zwei fehlende aktuelle Registrierungen (`ItemGlow`, `GhostSkin`) und einen veralteten Ledger-Namen (`ItemGlint`) aufgedeckt. Die fünf Einträge `TextHuds.*` sind eigenständige registrierte Unterklassen und teilen sich `TextHuds.java`.

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


## Lauf 2026-11-17 – Inventur und sichere Anzeigeprüfung

- **Aktueller Stand:** `main` vor den Dokumentationsänderungen: `819ea2d17cf76c547e2bbbf55e3d404f4d63f797`.
- **Inventur:** `ModuleManager.init()` enthält 102 eindeutige Modulregistrierungen. Der alte Audit-Ledger führte 101; `ItemGlow` und `GhostSkin` fehlten, während `ItemGlint` nicht mehr registriert ist. Index und Ledger wurden entsprechend berichtigt.
- **Watermark:** `Watermark.java` (Blob nach Korrektur: `b8e9cf2233c39490b79bc99f576ee6d00572cee2`) liest `mc.field_1724.method_7334().name()`, also den Profilnamen des eigenen Spielers, nicht den Anzeigenamen. Der Modultext behauptete zuvor fälschlich, auch eine Version zu zeigen. Die Beschreibung wurde auf den tatsächlichen Inhalt korrigiert (Commit `819ea2d`).
- **FakeLag:** Nur `FakeLagGhostRenderer.java` wurde als Renderpfad betrachtet. Guards für Spieler/Welt/Pending-Position und Matrix-Restore in `finally` sind vorhanden. **In-Game-Darstellung bleibt ausdrücklich offen.** Netzwerk-/Kampf-Ausführungslogik wurde nicht auditiert oder verändert.
- **GhostSkin:** Renderpfad für den eigenen Spieler, vorhandene Skin-Textur, Alpha-Begrenzung und Matrix-Restore in `finally` codebasiert geprüft. **In-Game-Test offen.**
- **AutoPvP / DTap / ObiTrapBot / CombatStatusHud:** Nur Registrierung und sichtbare Bezeichnungen bzw. HUD-Ausgabe geprüft. `Windcharge -> Mace` bleibt getrennt benannt; `Stun-Slam` wurde nicht als registriertes separates Feature bestätigt. `DTap` bleibt `DTap`, `ObiTrapBot` separat. `Pearl zum Gegner` ist laut vorheriger Codeprüfung eine Wurfoption, keine Fangfunktion. Das HUD zeigt keinen Crystal-/DTap-/ObiTrap- oder Boden-/Obsidian-Status.
- **ItemGlow:** Registrierung und Settings-Klasse inventarisiert; Renderer-/Mixin-Pfad noch offen. Die alte `ItemGlint.md`-Akte ist als historisch/nicht registriert gekennzeichnet.
- **Build:** GitHub Actions für den aktuellen Dokumentationsstand `5f7e942922c40f80c9534c93e151f3f16d226d98` ist erfolgreich abgeschlossen: [Run #44039685234](https://github.com/Tasseee/Meteor/actions/runs/44039685234). Der Schritt `gradle build --no-daemon` war erfolgreich (Java 21 / Gradle 9.2.0). Im Repository liegt kein Gradle-Wrapper; kein lokaler Build wurde ausgeführt. Eine `src/test`-Testsuite ist nicht vorhanden. Kein In-Game-Test wurde ausgeführt.
- **Änderungsumfang:** Ein kleiner Watermark-Beschreibungstext wurde korrigiert; die übrigen Änderungen dieses Laufs sind Dokumentation/Inventur. Keine Kampf- oder Angriffslogik wurde verändert.


## Lauf 2026-11-17 – Visual-/HUD-Audit und Stun-Slam-Dokumentation

- **Ausgangsstand:** `main` vor den Lauf-Dokumentationsänderungen: `feed85b8cc54c6d9336a98b82ad979068747d4e0`; Registrierungsquelle `ModuleManager.java` blieb bei Blob-SHA `7329859ce075c9708f749f30b00e904fdf523d6b`.
- **Inventur:** 102 Registrierungen erneut direkt aus `ModuleManager.init()` extrahiert und vollständig mit dem Ledger abgeglichen; keine fehlende oder zusätzliche Registrierung.
- **Vollständig statisch geprüft:** `GhostSkin`, `ItemGlow`, `DebugHud`, `LowHealthWarning` und `CustomCrosshair`. `NotificationsHud` wurde anhand der aktuellen Implementierung und `NotificationCenter` erneut gegengeprüft; es war bereits vollständig im Ledger.
- **ItemGlow:** HUD-Hotbar-/Offhand-Pfad, HandledScreen-Pfad, Accessor-Mixin, Callback-Registrierung, Wertebereiche, Config-Schlüssel und begrenzte Render-Schleifen geprüft. Die Einstellung `Hotbar` steuert auch den Offhand-Slot. Kein klarer Fehler; In-Game-Test offen.
- **GhostSkin:** Modulsettings, einmalige Renderer-Registrierung, Eigen-Spieler-/Textur-Guards, Alpha-Begrenzung und Matrix-Cleanup geprüft. In-Game-Test offen.
- **DebugHud / LowHealthWarning / CustomCrosshair:** Abhängigkeiten, Einstellungsgrenzen, HUD-Bedingungen, Weltwechsel-/Nullfälle, Lifecycle und Renderkosten geprüft. Keine Java-Änderung nötig.
- **NotificationsHud:** Queue ist synchronisiert und auf 12 Einträge begrenzt; aktive Liste ist ein Snapshot, Toast-Fortschritt begrenzt. Sichtbarer Modulname ist `Notifications`, Java-Klasse `NotificationsHud`.
- **AutoPvP-Dokumentation:** Die Nutzerdefinition von Stun-Slam wurde berichtigt: Axt-Schildbruch, danach Mace-Treffer in der Luft. Windcharge -> Mace bleibt davon getrennt. Keine Kampfsequenz ergänzt oder geändert.
- **Windcharge-/Statusprüfung:** `CooldownHud` zeigt den Wind-Charge-Cooldown, aber keinen Erkennungsstatus. `CombatStatusHud` zeigt keinen Crystal-/DTap-/ObiTrap-/Windcharge- oder Boden-/Obsidian-Kontext. DTap und ObiTrapBot bleiben getrennte Module; DTap wird nicht als Single-Tap bezeichnet.
- **Pearl:** `Pearl zum Gegner` bleibt als Wurffunktion dokumentiert, nicht als Fangfunktion.
- **Build/Tests:** GitHub Actions run `44039685234` auf Commit `5f7e942922c40f80c9534c93e151f3f16d226d98` lief erfolgreich (`gradle build --no-daemon`, Java 21 / Gradle 9.2.0). Die späteren Commits in diesem Lauf sind Dokumentationsänderungen; der aktuelle Dokumentations-Head wurde nicht erneut gebaut. Kein Gradle-Wrapper und keine `src/test`-Suite vorhanden. Kein In-Game-Test ausgeführt.
- **Änderungsumfang:** Nur Dokumentation/Inventur; keine Java-Änderungen und keine Änderungen an automatisierten Kampfabläufen.

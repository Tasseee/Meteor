# Nightfall – Fortschritt

**Aktueller Inventur-Abgleich:** `9477a2a7eb519ae9cfd3c6466c3db0d0ad470dc6` (Branch `main`, 2026-11-17)  
**Nutzungsanzeige:** `MODULE_USAGE.md` – lokale Aktivierungszahl und aktive Zeit in jeder ClickGUI-Modulzeile.  
**Letzte Nightfall-Prüfungen:** `BaseFinder`, `AdvancedWorldOverlay`, `AutoCraftPlanner`, `AutoSmelter`, `DroppedItemNotifier`, `LowHealthWarning`, `CustomCrosshair`, `DebugHud`, `AutoBreed`, `BlockNotifier`, `FavoriteChunk`, `FirstPersonStyle`, `Hats`, `ItemGlow`, `MovementParticles`, `ParticleTrail`, `SkyVisuals`, `BlockESP`, `ChunkEsp`, `ItemEsp`, `StorageEsp`, `EntityEsp`, `PlayerEsp`, `SpawnerEsp`, `AutoFish` und `AutoSign`, `GUITheme`, `FriendManager`, `HitParticles`, `JumpCircles`, `Minimap`, `ModuleList` und `ScannerHud`, `FakeStats`, `GlowESP`, `SafeWalk`, `Step`, `WeaponAnimations`, `WorldLyrics`, `SpotifyLyricsHud`, `AutoFarm` und `LootDropFinder`, `FreeLook`, `Freecam`, `TargetMarker`, `StructureESP`, `OreESP`, `StaffWatch`, `AntiVanishHints` und `SpawnerProtect`, `BoatFly`, `ElytraFly`, `Fly`, `Jesus`, `LongJump`, `NoFall`, `Speed`, `Sprint` und `WaterWalk` (2026-11-16)  
**Registrierungsquelle:** `src/main/java/dev/nightfall/module/ModuleManager.java`  
**SHA der Registrierungsquelle:** `7329859ce075c9708f749f30b00e904fdf523d6b`  
**Bisheriges Audit-Ledger:** `MODULE_AUDIT_PROGRESS.md`  
**SHA des Audit-Ledgers:** `920aca808b134877c85bf434e99cd6a60ced2661`

## Audit-Protokoll

- **2026-11-16 – StaffWatch-Verbesserung:** `/nf staff add/del` speichert erfolgreiche Änderungen sofort; `AntiVanishHints` überspringt seine HUD-Ausgabe, solange `StaffWatch` aktiv ist, und prüft Tab-/Welt-Einträge defensiv auf fehlende Werte. `StaffWatch` ist jetzt ein verschieb- und skalierbares `HudModule`, zeigt TAB-/WELT-Status verständlicher an, aktualisiert Präsenzdaten pro Tick und nutzt für TAB `getListedPlayerListEntries()` statt der allgemeineren `getPlayerList()`-Methode. Der alleinige Tab-Status heißt `TAB/NETZ` und wird nicht als Unterservernachweis gewertet; gefundene Namen erscheinen im HUD mit ihrer originalen Profil-Schreibweise. Serverunterschiede für HUGSMP und DonutSMP bleiben bis zum In-Game-Abgleich offen. Details und offene Tests in `NIGHTFALL/MODULES/StaffWatch.md` und `NIGHTFALL/MODULES/AntiVanishHints.md`.
- **2026-11-16 – HUD-/AntiVanish-Upgrade:** StaffWatch bietet konfigurierbare Zeilenanzahl (3–12), Kompaktmodus, Sortierung sichtbarer Treffer und Legenden-Schalter. AntiVanishHints wurde als verschiebbares/skaliertes HudModule mit konfigurierbarem Zeilenlimit und Tick-basiertem Cache umgesetzt. Beide Module nutzen ausschließlich manuell eingetragene Namen und clientseitig gelistete Tab-Profile bzw. geladene Welt-Entities; fehlende Sichtbarkeit wird nicht als Vanish-Beweis interpretiert. Build- und In-Game-Tests offen.
- **2026-11-16 – Freecam-Maussteuerung:** Option `Vertikale Maus invertieren` ergänzt, Standard `AN`; horizontale Maussteuerung bleibt unverändert. Details und offene Tests in `NIGHTFALL/MODULES/Freecam.md`.

Der vollständige Auditstatus wird in `MODULE_AUDIT_PROGRESS.md` und `MODULE_INDEX.md` gemeinsam gepflegt: 32 vollständig geprüft, 6 teilweise geprüft und 64 offen (102 Registrierungen). Codeprüfung allein bedeutet nicht automatisch vollständigen Auditabschluss.

**Wichtig:** Die 27 zuvor vollständig geprüften Module wurden in diesem Lauf um 5 weitere vollständig statisch geprüfte Module ergänzt; 6 Module bleiben teilweise geprüft und 64 offen. Im aktuellen Nightfall-Arbeitslauf wurden die oben aufgeführten Module anhand des Quellcodes geprüft. `BaseFinder` wurde zusätzlich mit Fokus auf Datenhaltung und Lifecycle geprüft. Für sie stehen Build- und In-Game-Tests noch aus. `AutoCraftPlanner` fasst doppelte Zutaten zusammen; `DroppedItemNotifier` verwendet seine temporäre UUID-Sammlung wieder; `BlockNotifier` hat ein korrigiertes Scanintervall und verwendet seine Positionssammlung wieder; `EspBase` verwendet jetzt die übergebenen Standardreichweiten; `AutoFish` unterstützt die Nebenhand, wenn dies freigegeben ist; `Minimap` stellt Matrix und Scissor-Clip nach Renderfehlern wieder her; `WorldLyrics` stellt nach Renderfehlern seine Transformationsmatrix wieder her; `SpotifyLyricsHud` schließt seinen OAuth-Executor und verarbeitet mehrere LRC-Zeitmarken; `AutoFarm` stellt den Hotbar-Slot auch bei Interaktionsfehlern wieder her; `LootDropFinder` bereinigt abgelaufene Marker-Höhendaten; `FreeLook` verwendet Cursor-Arrays wieder; `SpawnerProtect` löscht alte Spawnerpositionen beim Weltwechsel. Die 102 registrierten Einträge wurden mit `ModuleManager.init()` abgeglichen. Der Abgleich hat zwei fehlende aktuelle Registrierungen (`ItemGlow`, `GhostSkin`) und einen veralteten Ledger-Namen (`ItemGlint`) aufgedeckt. Die fünf Einträge `TextHuds.*` sind eigenständige registrierte Unterklassen und teilen sich `TextHuds.java`.

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
- **GhostSkin:** Modul und Renderer statisch vollständig geprüft: eigener Spieler, Skin-Textur, Alpha-Begrenzung, einmalige Registrierung und Matrix-Restore in `finally`. **In-Game-Test offen.**
- **AutoPvP / DTap / ObiTrapBot / CombatStatusHud:** Nur Registrierung und sichtbare Bezeichnungen bzw. HUD-Ausgabe geprüft. `Windcharge -> Mace` bleibt getrennt benannt. Der Nutzer definiert Stun-Slam als Axt-Schildbruch mit anschließendem Mace-Treffer in der Luft; eine separate Implementierung/Registrierung ist nicht bestätigt. `DTap` bleibt `DTap`, `ObiTrapBot` separat. `Pearl zum Gegner` ist eine Wurfoption, keine Fangfunktion. Das HUD zeigt keinen Crystal-/DTap-/ObiTrap-/Windcharge- oder Boden-/Obsidian-Status.
- **ItemGlow:** Modul, HUD-/Inventar-Renderer und `HandledScreenAccessor`-Mixin statisch vollständig geprüft. Die `Hotbar`-Option steuert auch den Offhand-Slot; Konfigurationsschlüssel nicht ohne Migration umbenennen. **In-Game-Test offen.** Die alte `ItemGlint.md`-Akte ist historisch/nicht registriert.
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


## Lauf 2026-11-17 – HUD-/Theme-Gesamtaudit

- **Ausgangsstand:** `main` vor dem Lauf-Audit-Fix: `f593906f6f94aeb78eeaa3bf454c73b7b3ac83c0`; Registrierungsquelle `ModuleManager.java` mit Blob-SHA `7329859ce075c9708f749f30b00e904fdf523d6b`; 102 registrierte Instanzen.
- **Vollständig statisch geprüft:** `ModuleList`, `Minimap`, `ScannerHud` (Config-/Anzeigename `ScannerStatus`), `FakeStats` und `GUITheme`. Gemeinsame Grundlagen `HudModule`, `Module`, `ModuleManager` und `Config` wurden für Lifecycle, HUD-Platzierung und Persistenz mit herangezogen.
- **ModuleList:** Fehler bei der Default-Rechtsausrichtung gefunden und behoben: Die Default-X-Position berücksichtigt jetzt die Breite der aktiven Nicht-HUD-Modulnamen bereits vor dem ersten Draw. Java-Commit `9477a2a7eb519ae9cfd3c6466c3db0d0ad470dc6`; Blob-SHA `9cdb8209c18d7374e9c5f015e6b663fa4b385be8`. Dokumentation aktualisiert.
- **Minimap:** Einstellungsgrenzen, Player-/World-Guards, BaseFinder-Lesezugriff und Matrix-/Scissor-Cleanup geprüft. Rotierte Reichweite 24 zeichnet bis zu 73×73 Zellen; Profiling und In-Game-Test bleiben offen.
- **ScannerHud:** Status- und Fortschrittsanzeige sowie Config-Key `ScannerStatus` geprüft. GONE-Zähler läuft pro Render über die gespeicherten Marks; bei vielen Marks beobachten. In-Game-Test offen.
- **FakeStats:** lokale, deutlich als Fake markierte HUD-Werte und Einstellungsgrenzen geprüft; keine Serverdatenänderung, keine eigenen Listener/Threads.
- **GUITheme:** Presets, Custom-Farben, GUI-Integration, Aktivierungs-/Deaktivierungsverhalten und Tick-Anwendung geprüft. Kein klarer Fehler; GUI-Test offen.
- **Build/Tests:** Kein lokaler Build möglich; kein Wrapper und keine `src/test`-Suite vorhanden. GitHub-Status für den neuen Code-Commit war zum Prüfzeitpunkt nicht als erfolgreicher Build nachgewiesen.
- **Sicherheitsumfang:** Keine Kampf- oder Angriffsautomatisierung untersucht oder geändert.
- **Fortschritt:** 32 vollständig geprüft, 6 teilweise geprüft, 64 offen (102 Registrierungen).
- **Erneute Prüfliste 1–5:** Watermark liest weiterhin `method_7334().name()` (Profilname des eigenen Spielers). `FakeLagGhostRenderer` besitzt Welt-/Spieler-/Render-Guards und stellt den Matrix-Stack in `finally` wieder her; der In-Game-Skin-Test bleibt offen. Die Status-HUDs zeigen weiterhin keinen Crystal-/Boden-/Obsidian-Kontext und keine Windcharge-Erkennung; `CooldownHUD` zeigt nur Item-Cooldowns. `DTap` bleibt separat und nicht `Single-Tap`; `ObiTrapBot` und `TrapperBot` sind getrennte Registrierungen; kein eigenständiges Stun-Slam-Modul nachgewiesen. `Windcharge -> Mace` bleibt getrennt dokumentiert. `Pearl zum Gegner` ist als Wurfoption dokumentiert, nicht als Auffangfunktion. Kampf-Ausführungslogik wurde nicht gelesen oder geändert.
## Lauf 2026-11-17 – Claude-HUD-Entwurf und HugoSMP-Unterserver-Prüfung

- `StaffHudPanel.java` ergänzt und von `StaffWatch` sowie `AntiVanishHints` gemeinsam verwendet. Das Design enthält Theme-Panel, Akzentkante, Zähler-Badge, Status-Pills, Zeilenhervorhebung und Editor-Vorschau.
- Beide Module bleiben in Nightfalls vorhandener Konfiguration und HUD-Editor integriert; die bestehende `StaffPresence`-Datenbasis und `/nf staff`-Befehle wurden wiederverwendet, keine doppelten Listen- oder Command-Systeme angelegt.
- StaffWatch erhielt zusätzlich den ein-/ausblendbaren Untertitel. AntiVanishHints erhielt Legenden- und Untertitel-Schalter; im Spiel blendet es sich bei aktivem StaffWatch weiterhin aus, im Editor kann es separat positioniert werden.
- **Unterserver-Recherche:** Standardmäßige Minecraft-Tab-/Player-Info-Einträge enthalten kein Backend-/Unterserverfeld pro anderem Spieler. Öffentlich auffindbare HugoSMP-Informationen dokumentieren keine solche clientseitige Zuordnung. Eine verlässliche Zuordnung erfordert eine ausdrückliche öffentliche Information vom Server/Proxy. `TAB/NETZ` bleibt daher als Backend unbekannt gekennzeichnet; keine Abfrage fremder Backend-Listen oder versteckter Spielerinformationen.
- **Offen:** GitHub-Buildstatus für diese neuen Codeänderungen prüfen; In-Game-Tests, besonders HUD-Skalierung und HUGSMP-Tab-Verhalten, bleiben offen. Keine Build- oder In-Game-Erfolgsaussage ohne entsprechenden Test.
## Lauf 2026-11-17 – HugoSMP-Online, Server und Nähe-Alarm

- `StaffPresence` prüft die aktuelle Minecraft-Serveradresse auf `hugosmp.net` bzw. Subdomains. Auf dieser Verbindung bedeutet `ONLINE`: Name in der vom Server gelieferten Tab-Liste; `SERVER`: passende Spieler-Entity in der aktuellen Client-Welt; `OFFLINE`: kein Treffer bei verfügbarer Tab-Datenquelle; sonst `HUGOSMP N/A` bzw. `TAB N/A`.
- `StaffWatch` erhielt die Setting-Option `Nähe-Alarm` (standardmäßig AN). Bei einer manuell eingetragenen Spieler-Entity innerhalb der tatsächlichen Client-Renderdistanz wird beim Eintritt einmal die lokale Meldung `<Name> is in your render distance` ausgelöst. Der Bereich wird aus der View-Distance-Einstellung in Chunks × 16 Blöcke berechnet; der Spieler muss zusätzlich als Entity in der aktuellen Welt geladen sein.
- Die Meldung beschreibt nur Nähe, nicht Blickrichtung oder Absicht. Es gibt keine versteckte Spieler-Erkennung, keine zusätzlichen Netzwerkabfragen und keine Ermittlung interner Backend-Namen.
- Direkte Verbindung über eine rohe IP oder einen anderen Alias kann nicht sicher HugoSMP zugeordnet werden; dann steht `HUGOSMP N/A`. Öffentlich dokumentierte Standard-Tabdaten geben keine eindeutige Unterserver-Zuordnung pro Spieler her.
- Build- und In-Game-Tests für diesen neuen Status-/Nähepfad sind offen.
- **Status-Text-Präzisierung:** Kein TAB-/WELT-Treffer heißt `NICHT GELISTET`, nicht `OFFLINE`; aus fehlender Sichtbarkeit wird kein sicherer Offline-Status abgeleitet.

## Lauf 2026-11-17 – AutoPvP Crit-Timing und Drive-by-Bewegung

- `AutoPvp.java` ergänzt: Crit-Vorbereitung wartet auf mindestens 98 % Angriffscooldown, dann den konfigurierbaren `Crit-Sprungdelay` (Standard 2 Ticks, Bereich 0–6), und löst den Angriff nur bei tatsächlichem Fallen (`fallDistance > 0.08`, vertikale Geschwindigkeit < 0) sowie mindestens 90 % Cooldown aus.
- Schwert-Crit-Vorbereitung funktioniert jetzt auch bei eingeschalteter CrystalAura-Integration; eine bereits aktive Crystal-Combo behält Vorrang. Sprint wird im Crit-Fenster freigegeben.
- `An Gegner vorbeilaufen` (Standard AN) ergänzt: wechselnde seitliche Bewegung im Nahbereich; Seitenwechsel konfigurierbar von 4–14 Ticks, Standard 7. Vorwärtsbewegung ist weiterhin an einen freien sicheren Pfad gebunden.
- Keine NoClip-/Kollisionsdeaktivierung: die Bewegung versucht, am Ziel seitlich vorbeizulaufen und kann nicht tatsächlich durch den Spielerkörper laufen.
- Crit-Zustand wird bei Zielverlust, Modul-Deaktivierung und Weltwechsel zurückgesetzt. Config-Schlüssel der bestehenden Crit-Einstellung blieb zur Kompatibilität unverändert.
- Build und In-Game-Tests bleiben offen; insbesondere Crit-Timing auf dem HugoSMP-Server und das Verhalten der seitlichen Bewegung müssen praktisch getestet werden.

## Lauf 2026-11-17 – AutoPvP kontinuierliches Durchlaufen/Seitenwechseln

- Drive-by-Nahkampfbewegung angepasst: `Drive-by Seitenwechsel` standardmäßig 2 Ticks, konfigurierbar von 1 bis 4 Ticks. Links-/Rechtsinput wechselt damit deutlich schneller.
- Vorwärtsinput bleibt im Drive-by-Bereich bis auf 0,35 Blöcke Abstand aktiv, auch wenn der konfigurierte Stop-Abstand bereits unterschritten ist. Der bestehende Block-/Bodencheck (`safeForward`) bleibt als Begrenzung aktiv.
- Spieler-Kollision wird nicht deaktiviert: Das Verhalten drückt nach vorne und versucht, durch seitlichen Wechsel am Ziel vorbeizukommen; echtes NoClip/Durchlaufen durch die Spieler-Hitbox ist damit nicht garantiert.
- Doppelte `onWorldLeave()`-Methode in `AutoPvp.java` zu einem Handler zusammengeführt; Crit-/Drive-by-Reset und bisheriger Weltwechsel-Cleanup bleiben enthalten.
- In-Game-Test und erfolgreicher Build für diesen Stand sind noch offen.


## Lauf 2026-11-20 – Performance-Diagnose für angefragte Module

- `ModuleManager` misst optional die Laufzeit von `onTick()` für `AutoShield`, `ShieldBreaker`, `AutoPvP`, `CrystalAura` und `DTap`.
- `DebugHud` zeigt den geglätteten Durchschnitt sowie das bisherige Maximum in Mikrosekunden.
- Profiling ist nur bei aktiviertem HUD `Debug` aktiv; beim Ausschalten werden Messwerte zurückgesetzt. Es werden feste Arrays verwendet.
- Keine Änderungen an Zielsuche, Rotation, Angriffstiming, Klick-/Tap-Folgen, Crystal-Platzierung, Netzwerkaktionen oder anderen Kampfabläufen.
- Build und In-Game-Test bleiben offen.

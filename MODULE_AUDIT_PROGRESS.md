# Nightfall module audit progress

Inventory snapshot: `8d8ed3639b25c8fb3fb9936c5e950cb132632dfb` (main, 2026-11-21).
Registered module instances: **110**. The existing detailed ledger covers the previous 102-entry inventory (32 fully reviewed, 6 partial, 64 pending); the 8-entry increase still needs to be reconciled into those status lists.

This file is the shared progress ledger for the scheduled repository audits. Update it only after a module has been checked against registration, settings/defaults, UI/HUD, lifecycle, listeners/resource cleanup, null/boundary handling, logging, threading, performance, compatibility, tests, and documentation. A label-only review is not a full review.

## Static no-op wiring audit (2026-11-21)

- Registered modules in `ModuleManager.init()`: **110**.
- Confirmed module with no runtime caller in the current source tree: **1 — `GlowESP`**. Its `shouldGlowLocal` / `colorFor` helpers are only defined in `GlowESP.java`; no mixin or renderer invokes them, so the module's outline settings currently have no effect.
- Confirmed inert sub-feature (not a whole module): `Item Glow → 3D-Kontur`. `ItemGlowMeshRenderer.render()` intentionally returns without drawing because the previous line-mesh path caused render-backend crashes. The 2D/slot glow features remain active.
- Modules without their own tick/HUD override were cross-checked against their integration points (renderers, mixins, shared helpers, or inherited ESP renderers). No additional definitely-unwired module was found in this pass.
- This is a static call-site audit, not a complete in-game test of every registered module. Runtime behavior and server-specific conditions still need testing.

## Fully reviewed
- [x] `Waypoints`
- [x] `FakeBases`
- [x] `Watermark`
- [x] `Coordinates`
- [x] `CooldownHud`
- [x] `NotificationsHud`
- [x] `GhostSkin`
- [x] `ItemGlow`
- [x] `DebugHud`
- [x] `LowHealthWarning`
- [x] `CustomCrosshair`
- [x] `WaypointHud`
- [x] `Atmosphere`
- [x] `AntiVanishHints`
- [x] `AntiCheatMonitor`
- [x] `ArmorHud`
- [x] `AutoReconnect`
- [x] `AutoRespawn`
- [x] `AutoRtp`
- [x] `AutoSign`
- [x] `AutoTpa`
- [x] `AutoText`
- [x] `TextHuds.Fps`
- [x] `TextHuds.Ping`
- [x] `TextHuds.Clock`
- [x] `TextHuds.Direction`
- [x] `TextHuds.BiomeHud`
- [x] `ModuleList`
- [x] `Minimap`
- [x] `ScannerHud` (display name / config key: `ScannerStatus`)
- [x] `FakeStats`
- [x] `GUITheme`

## Partial review only (not complete)
- [~] `AutoPvp` — visible settings/labels checked; combat automation deliberately not reviewed or modified
- [~] `DTap` — visible name/description checked; remains separate from AutoPvP; combat automation deliberately not reviewed or modified
- [~] `ObiTrapBot` — separately registered module identity/description checked; combat automation deliberately not reviewed or modified
- [~] `MaceAssist` — visible setting names only; combat automation deliberately not reviewed or modified
- [~] `FakeLag` — `FakeLagGhostRenderer` render path inspected only; in-game ghost-skin rendering remains explicitly open
- [~] `CombatStatusHud` — display code inspected; no combat execution logic reviewed

## Pending full review
- [ ] `TrapCrit`
- [ ] `KillAura`
- [ ] `TriggerBot`
- [ ] `FriendManager`
- [ ] `TrapperBot`
- [ ] `AutoShield`
- [ ] `TargetStrafe`
- [ ] `ShieldBreaker`
- [ ] `CrystalAura`
- [ ] `CrystalTest`
- [ ] `AutoAnchor`
- [ ] `AutoTotem`
- [ ] `AutoGap`
- [ ] `Fly`
- [ ] `ElytraFly`
- [ ] `BoatFly`
- [ ] `Speed`
- [ ] `Sprint`
- [ ] `Step`
- [ ] `SafeWalk`
- [ ] `LongJump`
- [ ] `Jesus`
- [ ] `WaterWalk`
- [ ] `Freecam`
- [ ] `NoFall`
- [ ] `FreeLook`
- [ ] `PlayerEsp`
- [ ] `EntityEsp`
- [ ] `ItemEsp`
- [ ] `DroppedItemNotifier`
- [ ] `SpotifyLyricsHud`
- [ ] `WorldLyrics`
- [ ] `LootDropFinder`
- [ ] `StorageEsp`
- [ ] `BlockNotifier`
- [ ] `ParticleTrail`
- [ ] `HitParticles`
- [ ] `JumpCircles`
- [ ] `MovementParticles`
- [ ] `TargetMarker`
- [ ] `SkyVisuals`
- [ ] `Hats`
- [ ] `FirstPersonStyle`
- [ ] `WeaponAnimations`
- [ ] `SlowSwing`
- [ ] `InvSee`
- [ ] `GlowESP`
- [ ] `SpawnerEsp`
- [ ] `ChunkEsp`
- [ ] `BlockESP`
- [ ] `OreESP`
- [ ] `StructureESP`
- [ ] `SeedCracker`
- [ ] `FavoriteChunk`
- [ ] `AdvancedWorldOverlay`
- [ ] `AutoFarm`
- [ ] `AutoBreed`
- [ ] `AutoSmelter`
- [ ] `AutoCraftPlanner`
- [ ] `BaseFinder`
- [ ] `SpawnerProtect`
- [ ] `StaffWatch`
- [ ] `AutoFish`
- [ ] `MacroManager`

## Shared/support code inspected
- `WaypointManager.java` — invalid JSON entries are now skipped safely (see commit `a66b3e2`).
- `MapScreen.java`, `Commands.java`, `Storage.java` — Waypoint consumers and data source checked.
- `Module.java`, `ModuleManager.java` — registration and common lifecycle checked; current `main` registers 102 unique module instances.
- `GhostSkin.java`, `GhostSkinFeatureRenderer.java` — module registration/settings, own-player and skin-texture guards, alpha clamp, global renderer registration, and matrix push/pop restoration checked; In-Game-Test remains open.
- `ItemGlow.java`, `ItemGlowRenderer.java`, `HandledScreenAccessor.java`, `nightfall.mixins.json` — hotbar/offhand and handled-inventory render paths, mixin registration, bounds, config-key compatibility and callback lifecycle checked; In-Game-Test remains open. The `Hotbar` toggle also controls offhand.
- `DebugHud.java`, `ModuleManager.java` — opt-in tick-time diagnostics for AutoShield, ShieldBreaker, AutoPvP, CrystalAura and DTap; fixed-size counters, disabled unless Debug HUD is enabled. Combat execution logic remains out of scope.
- `LowHealthWarning.java` — threshold bounds, player-null/world-leave reset, hysteresis, notification queue and HUD preview checked.
- `CustomCrosshair.java` — style modes, size/gap bounds, RGB masking and render guards checked; `Abstand` is used only in `Kreuz` mode.
- `NotificationCenter.java` — synchronized queue and snapshot behavior checked.
- `HudModule.java`, `Projection.java`, `Draw.java` — HUD transform and projection support checked; `Draw.line()` now restores its matrix in `finally`.
- `Config.java`, `ListSetting.java`, `Nightfall.java` — setting persistence, lowercase list normalization, and client message/lifecycle event wiring checked.
- `StaffWatch.java` — inspected only as the `AntiVanishHints` data dependency; full module review remains pending.
- `AntiCheatMonitor.java` — word-boundary matching prevents `inactive`/`deactivated` from being mistaken for active (commit `ae228be`).
- `AntiVanishHints.java` — skips player-list scans and temporary sets when the watch list is empty (commit `b899a36`).
- `AutoReconnect.java`, `AutoRtp.java`, `AutoRespawn.java` — bounded timers/attempts, disconnect/world-leave handling, and BaseFinder dependency checked; no resource listeners owned by these modules.
- `AutoSign.java`, `AbstractSignEditScreenMixin.java` — four bounded text settings, one-time sign-screen prefill, overwrite behavior, and manual save flow checked.
- `AutoTpa.java` — incoming game-message hook, regex/filter/list handling and pending-request lifecycle checked; self-name comparison now uses the account profile name (commit `78a7394`).
- `AutoText.java`, `MessageListSetting.java` — message limits, persistence, rotation and GUI/command editing checked; status now reports the sent index and tick reads avoid a per-tick list copy (commits `0baae3c`, `5864c36`).
- `ModuleList.java` — registration/name, enabled-module filter, sort order, dynamic dimensions, saved-position migration and reused sort buffer checked; default right alignment now calculates the active content width before the first draw (commit `9477a2a`). Long lists can still extend below the screen; HUD/editor test remains open.
- `Minimap.java` — bounded size/range/opacity settings, BaseFinder read-only lookup, player/world guards, chunk-cell loop, non-scalable placement and matrix/scissor restoration checked. Rotated range 24 can draw up to 73×73 cells; profiling and in-game render test remain open.
- `ScannerHud.java` — `ScannerStatus` config identity, BaseFinder enabled/paused state, progress bounds, top-five rendering and GONE count checked. It iterates the marks list per render; cost depends on saved mark count. Locale-dependent `String.format` is cosmetic. In-game test remains open.
- `FakeStats.java` — four bounded local settings, explicit `FAKE STATS · LOKAL` labeling, dynamic dimensions and shared HUD lifecycle checked; no server writes, listeners or threads.
- `GUITheme.java` — eight settings, fixed/custom preset behavior, color-editor integration, enable/disable reset and client-thread application checked. `applyTheme()` runs every tick; no measurable regression established, so no speculative change made. GUI test remains open.

## Constraints and open verification
- Do not modify or optimize automated attack/targeting/aim/click/tap/Crystal/Trap/combo behavior.
- `AutoPvp` visibly labels the option `Windcharge -> Mace`; no `Stun-Slam` label or separately registered Stun-Slam module was found in the inventory. The user defines Stun-Slam separately as axe shield-break followed by a Mace hit while airborne. This meaning is now documented in `NIGHTFALL/MODULES/AutoPvp.md`; implementation/registration is not confirmed and no sequence may be added.
- `DTap` remains separately registered and is not labeled `Single-Tap`. `ObiTrapBot` remains separately registered. `CombatStatusHud` displays TrapCrit/KillAura target details and a Mace estimate, but no Crystal/DTap/ObiTrap/Windcharge detection status or floor/obsidian context. `CooldownHud` displays Wind Charge cooldown only, not a detection state.
- `AutoPvp` labels its Pearl option `Pearl zum Gegner`; the prior code review confirmed it throws toward the opponent's landing point, not catches a Pearl. This remains a label/identity note only.
- Watermark uses the player's game-profile name (`method_7334().name()`), not the display component.
- In-game verification is required for visual behavior such as FakeLag ghost skin rendering.
- No Gradle wrapper or `src/test` test suite is present. GitHub Actions run `44039685234` on commit `5f7e942922c40f80c9534c93e151f3f16d226d98` completed successfully: the `gradle build --no-daemon` step passed on Java 21 / Gradle 9.2.0. Subsequent commits `e93fe8b`, `997db22` and `feed85b` are documentation-only; the exact current documentation head has not been rebuilt.
- `ItemGlint` is not a current registered module/class; the active registration is `ItemGlow`. The old `NIGHTFALL/MODULES/ItemGlint.md` is retained as a historical note only.


## Performance-Diagnose (2026-11-20)
- Die fünf angefragten Module werden über ihren `onTick()`-Handler optional gemessen; Anzeige in `Debug` als geglätteter Durchschnitt und Maximum in Mikrosekunden.
- Die Messung wird nur bei aktiviertem Debug-HUD durchgeführt. Das ist eine Diagnose, keine Optimierung des Kampfverhaltens.
- Die Module `AutoShield`, `ShieldBreaker`, `AutoPvp`, `CrystalAura` und `DTap` bleiben bezüglich ihrer automatisierten Kampfabläufe ausdrücklich unverändert und nicht vollständig auditiert.
- Build- und In-Game-Test des Profilers bleiben offen.


## Sichere Lifecycle-Verbesserungen (2026-11-20)
- [~] `AutoShield` — identisches Cleanup in `resetState()` zentralisiert; Kampf-Erkennungslogik nicht verändert
- [~] `ShieldBreaker` — Hotbar-Cleanup respektiert manuelle Slot-Wechsel; Ziel-/Angriffslogik nicht verändert
- [~] `AutoPvp` — temporäre Crit-Zustände werden auch beim Weltwechsel zurückgesetzt; Kampfabläufe nicht verändert
- [~] `CrystalAura` — identisches Cleanup in `resetState()` zentralisiert; Platzierungs-/Zerstörungslogik nicht verändert
- [~] `DTap` — bestehender zentraler Reset für Enable/Disable/Weltwechsel geprüft; keine Änderung nötig

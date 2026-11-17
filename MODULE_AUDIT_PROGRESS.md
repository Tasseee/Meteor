# Nightfall module audit progress

Inventory snapshot: `819ea2d17cf76c547e2bbbf55e3d404f4d63f797` (main, 2026-11-17).
Registered module instances: **102**. Fully reviewed: **22**. UI-/label-only partial reviews: **6**. Pending: **74**.

This file is the shared progress ledger for the scheduled repository audits. Update it only after a module has been checked against registration, settings/defaults, UI/HUD, lifecycle, listeners/resource cleanup, null/boundary handling, logging, threading, performance, compatibility, tests, and documentation. A label-only review is not a full review.

## Fully reviewed
- [x] `Waypoints`
- [x] `FakeBases`
- [x] `Watermark`
- [x] `Coordinates`
- [x] `CooldownHud`
- [x] `NotificationsHud`
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
- [ ] `ItemGlow`
- [ ] `GUITheme`
- [ ] `CustomCrosshair`
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
- [ ] `ModuleList`
- [ ] `Minimap`
- [ ] `ScannerHud`
- [ ] `LowHealthWarning`
- [ ] `FakeStats`
- [ ] `DebugHud`
- [ ] `GhostSkin`

## Shared/support code inspected
- `WaypointManager.java` — invalid JSON entries are now skipped safely (see commit `a66b3e2`).
- `MapScreen.java`, `Commands.java`, `Storage.java` — Waypoint consumers and data source checked.
- `Module.java`, `ModuleManager.java` — registration and common lifecycle checked; current `main` registers 102 unique module instances.
- `GhostSkinFeatureRenderer.java` — own-player/skin-texture guards, alpha clamp, and matrix push/pop restoration inspected; In-Game-Test remains open.
- `ItemGlow.java` — registration and setting accessors inventoried only; rendering hooks remain open.
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

## Constraints and open verification
- Do not modify or optimize automated attack/targeting/aim/click/tap/Crystal/Trap/combo behavior.
- `AutoPvp` visibly labels the option `Windcharge -> Mace`; no `Stun-Slam` label or separately registered Stun-Slam module was found in the inventory. The user's separate Stun-Slam definition remains unverified; do not add its combat sequence.
- `DTap` remains separately registered and is not labeled `Single-Tap`. `ObiTrapBot` is separately registered. `CombatStatusHud` displays TrapCrit/KillAura target details and a Mace estimate, but no Crystal/DTap/ObiTrap status or floor/obsidian context.
- `AutoPvp` labels its Pearl option `Pearl zum Gegner`; the prior code review confirmed it throws toward the opponent's landing point, not catches a Pearl. This remains a label/identity note only.
- Watermark uses the player's game-profile name (`method_7334().name()`), not the display component.
- In-game verification is required for visual behavior such as FakeLag ghost skin rendering.
- No `src/test` directory or test suite was found. GitHub Actions run `44039685234` for documentation commit `5f7e942922c40f80c9534c93e151f3f16d226d98` completed successfully; its `gradle build --no-daemon` step passed on Java 21 / Gradle 9.2.0.
- `ItemGlint` is not a current registered module/class; the active registration is `ItemGlow`. The old `NIGHTFALL/MODULES/ItemGlint.md` is retained as a historical note only.

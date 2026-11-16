# Nightfall module audit progress

Inventory snapshot: `b899a36f2dee3afe61d9582d9ae7f1f0eb7fdf62` (main, 2026-11-16).
Registered module instances: **101**. Fully reviewed: **16**. UI-/label-only partial reviews: **6**. Pending: **79**.

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
- [x] `TextHuds.Fps`
- [x] `TextHuds.Ping`
- [x] `TextHuds.Clock`
- [x] `TextHuds.Direction`
- [x] `TextHuds.BiomeHud`

## Partial review only (not complete)
- [~] `AutoPvp` — only visible settings/labels checked; combat automation deliberately not reviewed or modified
- [~] `DTap` — only visible settings/description checked; combat automation deliberately not reviewed or modified
- [~] `ObiTrapBot` — module identity/description only; combat automation deliberately not reviewed or modified
- [~] `MaceAssist` — visible setting names only; combat automation deliberately not reviewed or modified
- [~] `FakeLag` — ghost renderer code inspected only; in-game skin rendering remains unverified
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
- [ ] `ItemGlint`
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
- [ ] `AutoRtp`
- [ ] `AutoReconnect`
- [ ] `AutoSign`
- [ ] `AutoTpa`
- [ ] `AutoText`
- [ ] `SpawnerProtect`
- [ ] `StaffWatch`
- [ ] `AutoRespawn`
- [ ] `AutoFish`
- [ ] `MacroManager`
- [ ] `ModuleList`
- [ ] `Minimap`
- [ ] `ScannerHud`
- [ ] `LowHealthWarning`
- [ ] `FakeStats`
- [ ] `DebugHud`

## Shared/support code inspected
- `WaypointManager.java` — invalid JSON entries are now skipped safely (see commit `a66b3e2`).
- `MapScreen.java`, `Commands.java`, `Storage.java` — Waypoint consumers and data source checked.
- `Module.java`, `ModuleManager.java` — registration and common lifecycle checked.
- `NotificationCenter.java` — synchronized queue and snapshot behavior checked.
- `HudModule.java`, `Projection.java`, `Draw.java` — HUD transform and projection support checked; `Draw.line()` now restores its matrix in `finally`.
- `Config.java`, `ListSetting.java`, `Nightfall.java` — setting persistence, lowercase list normalization, and client message/lifecycle event wiring checked.
- `StaffWatch.java` — inspected only as the `AntiVanishHints` data dependency; full module review remains pending.
- `AntiCheatMonitor.java` — word-boundary matching prevents `inactive`/`deactivated` from being mistaken for active (commit `ae228be`).
- `AntiVanishHints.java` — skips player-list scans and temporary sets when the watch list is empty (commit `b899a36`).

## Constraints and open verification
- Do not modify or optimize automated attack/targeting/aim/click/tap/Crystal/Trap/combo behavior.
- `AutoPvp` currently labels the separate setting `Windcharge -> Mace`; no `Stun-Slam` label was found. The user's separate Stun-Slam definition (axe shield-break, then airborne Mace hit) is not verified as a distinct feature; do not add its combat sequence.
- `DTap` remains a separate module and is not labeled Single-Tap. `ObiTrapBot` is separately registered. `CombatStatusHud` does not currently show Crystal/DTap/ObiTrap status, so no floor/obsidian context is represented there.
- `AutoPvp` labels its Pearl option `Pearl zum Gegner`; the inspected method throws toward the opponent's landing point, not catches a Pearl.
- Watermark uses the player's game-profile name (`method_7334().name()`), not the display component.
- In-game verification is required for visual behavior such as FakeLag ghost skin rendering.
- No `src/test` directory or test suite was found. CI runs `gradle build --no-daemon` on Java 21; record CI status against the exact commit SHA.

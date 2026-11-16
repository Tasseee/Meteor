# Nightfall module audit progress

Inventory snapshot: `a66b3e28430a8c0ae8084c5a25bf2b35bc68afa5` (main, 2026-11-16).
Registered module instances: **101**. Fully reviewed: **12**. UI-/label-only partial reviews: **6**. Pending: **83**.

This file is the shared progress ledger for the scheduled repository audits. Update it only after a module has been checked against registration, settings/defaults, UI/HUD, lifecycle, listeners/resource cleanup, null/boundary handling, logging, threading, performance, compatibility, tests, and documentation. A label-only review is not a full review.

## Fully reviewed
- [x] `Waypoints`
- [x] `FakeBases`
- [x] `Watermark`
- [x] `Coordinates`
- [x] `CooldownHud`
- [x] `NotificationsHud`
- [x] `WaypointHud`

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
- [ ] `Atmosphere`
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
- [ ] `AntiVanishHints`
- [ ] `AntiCheatMonitor`
- [ ] `AutoRespawn`
- [ ] `AutoFish`
- [ ] `MacroManager`
- [ ] `ModuleList`
- [ ] `Minimap`
- [ ] `ScannerHud`
- [ ] `ArmorHud`
- [ ] `LowHealthWarning`
- [ ] `FakeStats`
- [ ] `DebugHud`
- [x] `TextHuds.Fps`
- [x] `TextHuds.Ping`
- [x] `TextHuds.Clock`
- [x] `TextHuds.Direction`
- [x] `TextHuds.BiomeHud`

## Shared/support code inspected
- `WaypointManager.java` — invalid JSON entries are now skipped safely (see commit `a66b3e2`).
- `MapScreen.java`, `Commands.java`, `Storage.java` — Waypoint consumers and data source checked.
- `Module.java`, `ModuleManager.java` — registration and common lifecycle checked.
- `NotificationCenter.java` — synchronized queue and snapshot behavior checked.
- `HudModule.java`, `Projection.java`, `Draw.java` — HUD transform and projection support checked; `Draw.line()` now restores its matrix in `finally`.

## Constraints and open verification
- Do not modify or optimize automated attack/targeting/aim/click/tap/Crystal/Trap/combo behavior.
- In-game verification is required for visual behavior such as FakeLag ghost skin rendering.
- Build/CI status must be recorded against the exact commit SHA; do not infer it from an earlier run.

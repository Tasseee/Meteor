# Feature status

## Implemented in the first scaffold
- WPF desktop shell, category navigation, module filter and inspector
- Local clock
- Local JSON settings model and persistence service
- Explicit module status labels

## Partial
- FPS Display: no supported FPS source is connected; no fabricated value is shown.
- Performance Dashboard: system telemetry is planned as a local Windows-only service.
- HUD Editor: desktop preview only, not a Fortnite overlay.
- Keybind Manager: RightShift is a focused-window shortcut, not a global hook.

## UEFN integration required
- Chest Debug: use map-authored devices/Verse in the creator's own island. Epic documents map_indicator_device for minimap/overview markers and beacon_device for in-world/HUD markers. Do not scan Fortnite memory.
- Private Map Queue: matchmaking thresholds and queue timing are configured in island settings. The desktop app stores reference values; it does not start matchmaking.

## Excluded
- Player ESP
- External auto-join or automated game input
- Fortnite process injection, memory inspection, and game-file modification

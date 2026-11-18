# Feature status

## Implemented in this scaffold
- WPF desktop shell, category navigation, module search and inspector
- System-wide Windows CPU and RAM telemetry
- Local clock and watermark, both with real visibility toggles
- Editable queue reference profile saved under the user's AppData folder
- Explicit status labels and toggle controls that are enabled only when a real handler exists

## Partial / not connected
- Fortnite FPS: no supported data source is connected; no value is fabricated.
- RightShift: works only while the application is focused.
- Queue profile: local settings helper only; it does not start or control matchmaking.

## UEFN map-side sample
- Chest Debug: the Verse sample toggles creator-placed map_indicator_device markers. These are fixed map-authored locations, not a scan for live chest inventory or state. Configure the devices in your own island. Keep the toggle button restricted to your private testing area.

## Placeholders
- HUD Editor
- Chest Counter
- Shader Presets
- Notifications
- Theme selector
- Sound Settings

## Excluded
- Player ESP
- External auto-join and automated game input
- Fortnite process injection, memory inspection, and game-file modification

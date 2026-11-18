# NIGHTFALL FN

Standalone Windows companion client for the NIGHTFALL family.

- Runtime: .NET 10 LTS and WPF
- Target: Windows 10 and Windows 11
- Style: Minimal Dark, restrained violet accents, Steve-inspired branding
- No Fortnite injection, memory access, game-file modification, or synthetic game input

## Build

Build on Windows with the .NET 10 SDK:

    dotnet build NIGHTFALL_FN.sln
    dotnet run --project src/NightfallFN.App/NightfallFN.App.csproj

## Feature status

| Feature | Status | Notes |
|---|---|---|
| Desktop ClickGUI shell | Implemented | Local module list, category filter, search and inspector |
| System CPU/RAM dashboard | Implemented | Whole-system Windows telemetry |
| Clock and watermark | Implemented | Local UI elements, individually toggleable |
| Queue profile | Implemented | Editable local values, saved under AppData |
| Chest Debug | UEFN integration sample | Fixed map-authored markers, toggled by an island button |
| Fortnite FPS | Not connected | No value is fabricated |
| HUD editor | Placeholder | Not yet implemented |
| Chest counter | Placeholder | Not yet implemented |
| Shader presets | Placeholder | Not yet implemented |
| Notifications | Placeholder | Not yet implemented |
| Player ESP | Not included | Deliberately excluded |
| Auto-join | Not included | External game automation is not implemented |

The Minecraft code remains unchanged on the original main branch. NIGHTFALL FN is a separate C# project in its own folder and development branch.

## Folders

- src/NightfallFN.App: WPF app
- Models: module and local configuration models
- Services: module catalog, settings persistence and system telemetry
- Views: WPF queue profile editor
- docs: feature status and decisions
- UEFN: map-side Verse debug marker sample

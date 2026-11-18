# NIGHTFALL FN

Standalone Windows companion client for the NIGHTFALL family.

- Runtime: .NET 10 LTS and WPF
- Target: Windows 10 and Windows 11
- Style: Minimal Dark, restrained violet accents, Steve-inspired branding
- No Fortnite injection, memory access, file modification, or synthetic game input

## Build

Build on Windows with the .NET 10 SDK:

    dotnet build NIGHTFALL_FN.sln
    dotnet run --project src/NightfallFN.App/NightfallFN.App.csproj

## Feature status

| Feature | Status | Notes |
|---|---|---|
| Desktop ClickGUI | Implemented | Local module list, search, inspector |
| Theme presets | Implemented | Changes the NIGHTFALL window only |
| Local CPU/RAM dashboard | Partial | Uses Windows system telemetry |
| Fortnite FPS | Not connected | No value is fabricated |
| HUD editor | Preview-only | Not an in-game overlay |
| Chest Debug | UEFN integration required | Map-authored markers on your own island |
| Private Map Queue | Settings assistant | Saves reference values; does not start matchmaking |
| Player ESP | Not included | Deliberately excluded |
| Auto-join | Not included | No external game automation |

The Minecraft project is kept separate. This app is a new C# project.

## Folders

- src/NightfallFN.App: WPF app
- Models: module and local configuration models
- Services: module catalog and settings persistence
- Views: additional WPF windows
- docs: feature status and decisions
- UEFN: creator-owned island integration notes

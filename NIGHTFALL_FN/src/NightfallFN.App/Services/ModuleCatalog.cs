using NightfallFN.Models;

namespace NightfallFN.Services;

public static class ModuleCatalog
{
    public static IReadOnlyList<ClientModule> Create() =>
    [
        new() { Name = "FPS Display", Category = ModuleCategory.Visuals, Description = "Fortnite FPS is not yet connected to a supported source.", Status = ModuleStatus.Partial },
        new() { Name = "Performance Dashboard", Category = ModuleCategory.Visuals, Description = "Local Windows CPU and memory telemetry; no game-process inspection.", Status = ModuleStatus.Partial },
        new() { Name = "Shader Presets", Category = ModuleCategory.Visuals, Description = "Minimal Violet, Night and High Contrast themes for NIGHTFALL only.", Status = ModuleStatus.Ready },
        new() { Name = "Clock", Category = ModuleCategory.Visuals, Description = "Displays the local Windows time.", Status = ModuleStatus.Ready },
        new() { Name = "Watermark", Category = ModuleCategory.Visuals, Description = "NIGHTFALL FN client branding.", Status = ModuleStatus.Ready },
        new() { Name = "Module List", Category = ModuleCategory.Visuals, Description = "Shows enabled NIGHTFALL desktop modules.", Status = ModuleStatus.Ready },
        new() { Name = "Chest Debug", Category = ModuleCategory.Visuals, Description = "Creator-owned UEFN map markers. Requires map-side device/Verse integration.", Status = ModuleStatus.UefnIntegrationRequired },
        new() { Name = "Private Map Queue", Category = ModuleCategory.Misc, Description = "Local helper profile for UEFN queue settings. Does not start or automate matchmaking.", Status = ModuleStatus.Ready },
        new() { Name = "Chest Counter", Category = ModuleCategory.Misc, Description = "Manual desktop-side count; not connected to live Fortnite state.", Status = ModuleStatus.Ready },
        new() { Name = "ClickGUI", Category = ModuleCategory.Client, Description = "Module browser with categories, search and inspector.", Status = ModuleStatus.Ready },
        new() { Name = "HUD Editor", Category = ModuleCategory.Client, Description = "Desktop layout preview, not an in-game overlay.", Status = ModuleStatus.Partial },
        new() { Name = "Notifications", Category = ModuleCategory.Client, Description = "In-client notifications.", Status = ModuleStatus.Ready },
        new() { Name = "Keybind Manager", Category = ModuleCategory.Client, Description = "RightShift shortcut while the app is focused.", Status = ModuleStatus.Partial },
        new() { Name = "Theme Settings", Category = ModuleCategory.Client, Description = "Changes NIGHTFALL's own color palette.", Status = ModuleStatus.Ready },
        new() { Name = "Module Manager", Category = ModuleCategory.Client, Description = "Enable and disable desktop modules.", Status = ModuleStatus.Ready },
        new() { Name = "Sound Settings", Category = ModuleCategory.Client, Description = "Reserved for a later implementation.", Status = ModuleStatus.Placeholder }
    ];
}

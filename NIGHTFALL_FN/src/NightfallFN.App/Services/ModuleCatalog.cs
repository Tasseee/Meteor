using NightfallFN.Models;

namespace NightfallFN.Services;

public static class ModuleCatalog
{
    public static IReadOnlyList<ClientModule> Create(IReadOnlyDictionary<string, Action<bool>> handlers)
    {
        ClientModule Add(string name, ModuleCategory category, string description, ModuleStatus status) =>
            new()
            {
                Name = name,
                Category = category,
                Description = description,
                Status = status,
                ToggleHandler = handlers.TryGetValue(name, out var handler) ? handler : null
            };

        return
        [
            Add("FPS Display", ModuleCategory.Visuals, "Fortnite FPS is not connected to a supported source yet.", ModuleStatus.Partial),
            Add("Performance Dashboard", ModuleCategory.Visuals, "Displays whole-system CPU and RAM usage from Windows.", ModuleStatus.Ready),
            Add("Shader Presets", ModuleCategory.Visuals, "Theme presets for the NIGHTFALL desktop interface; selector is not built yet.", ModuleStatus.Placeholder),
            Add("Clock", ModuleCategory.Visuals, "Shows the local Windows clock in the dashboard.", ModuleStatus.Ready),
            Add("Watermark", ModuleCategory.Visuals, "NIGHTFALL FN branding in the application header.", ModuleStatus.Ready),
            Add("Module List", ModuleCategory.Visuals, "The local NIGHTFALL module catalogue.", ModuleStatus.Ready),
            Add("Chest Debug", ModuleCategory.Visuals, "Creator-authored chest markers inside your own UEFN island. Requires map-side setup.", ModuleStatus.UefnIntegrationRequired),
            Add("Private Map Queue", ModuleCategory.Misc, "Editable local reference profile for UEFN queue settings; does not launch matchmaking.", ModuleStatus.Partial),
            Add("Chest Counter", ModuleCategory.Misc, "Manual counter UI has not been implemented yet.", ModuleStatus.Placeholder),
            Add("ClickGUI", ModuleCategory.Client, "WPF module browser with categories, search and inspector.", ModuleStatus.Ready),
            Add("HUD Editor", ModuleCategory.Client, "Desktop preview/editor is planned; no in-game overlay is created.", ModuleStatus.Placeholder),
            Add("Notifications", ModuleCategory.Client, "Notification center is planned and not yet connected.", ModuleStatus.Placeholder),
            Add("Keybind Manager", ModuleCategory.Client, "RightShift minimizes/restores the app only while it has focus.", ModuleStatus.Partial),
            Add("Theme Settings", ModuleCategory.Client, "Theme selection UI is planned.", ModuleStatus.Placeholder),
            Add("Module Manager", ModuleCategory.Client, "Module catalogue and status inspector.", ModuleStatus.Ready),
            Add("Sound Settings", ModuleCategory.Client, "Reserved for a future implementation.", ModuleStatus.Placeholder)
        ];
    }
}

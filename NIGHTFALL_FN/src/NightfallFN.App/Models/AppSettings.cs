namespace NightfallFN.Models;

public sealed class AppSettings
{
    public string Theme { get; set; } = "Minimal Violet";
    public int GuiToggleKey { get; set; } = 344;
    public QueueProfile QueueProfile { get; set; } = new();
}

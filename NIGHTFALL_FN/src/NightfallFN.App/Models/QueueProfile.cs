namespace NightfallFN.Models;

/// <summary>
/// Local reference values for island matchmaking settings.
/// This model does not connect to or start Fortnite matchmaking.
/// </summary>
public sealed class QueueProfile
{
    public string Name { get; set; } = "Private Map";
    public int MinimumPlayers { get; set; } = 2;
    public int OvertimePlayerTarget { get; set; } = 4;
    public int MaximumPlayers { get; set; } = 8;
    public int MainDurationSeconds { get; set; } = 5;
    public int OvertimeDurationSeconds { get; set; } = 15;
}

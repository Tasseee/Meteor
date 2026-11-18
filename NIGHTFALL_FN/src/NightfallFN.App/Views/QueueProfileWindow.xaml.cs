using System.Windows;
using NightfallFN.Models;

namespace NightfallFN;

public partial class QueueProfileWindow : Window
{
    private readonly Action<QueueProfile> _save;
    private readonly QueueProfile _initial;

    public QueueProfileWindow(QueueProfile profile, Action<QueueProfile> save)
    {
        InitializeComponent();
        _initial = profile;
        _save = save;
        NameBox.Text = profile.Name;
        MaximumBox.Text = profile.MaximumPlayers.ToString();
        MinimumBox.Text = profile.MinimumPlayers.ToString();
        OvertimeTargetBox.Text = profile.OvertimePlayerTarget.ToString();
        MainDurationBox.Text = profile.MainDurationSeconds.ToString();
        OvertimeDurationBox.Text = profile.OvertimeDurationSeconds.ToString();
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        if (!int.TryParse(MaximumBox.Text, out var maximum) ||
            !int.TryParse(MinimumBox.Text, out var minimum) ||
            !int.TryParse(OvertimeTargetBox.Text, out var target) ||
            !int.TryParse(MainDurationBox.Text, out var mainDuration) ||
            !int.TryParse(OvertimeDurationBox.Text, out var overtimeDuration))
        {
            ValidationText.Text = "Enter whole numbers in all numeric fields.";
            return;
        }

        if (maximum < 1 || minimum < 1 || minimum > maximum ||
            target < minimum || target > maximum ||
            mainDuration is < 0 or > 120 || overtimeDuration is < 0 or > 120)
        {
            ValidationText.Text = "Check the ranges: 1 ≤ minimum ≤ target ≤ maximum; durations must be 0–120 seconds.";
            return;
        }

        var updated = new QueueProfile
        {
            Name = string.IsNullOrWhiteSpace(NameBox.Text) ? "Private Map" : NameBox.Text.Trim(),
            MaximumPlayers = maximum,
            MinimumPlayers = minimum,
            OvertimePlayerTarget = target,
            MainDurationSeconds = mainDuration,
            OvertimeDurationSeconds = overtimeDuration
        };

        try
        {
            _save(updated);
            DialogResult = true;
        }
        catch (IOException ex)
        {
            ValidationText.Text = $"Could not save the profile: {ex.Message}";
        }
    }
}

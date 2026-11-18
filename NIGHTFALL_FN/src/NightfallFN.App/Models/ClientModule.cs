using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace NightfallFN.Models;

public sealed class ClientModule : INotifyPropertyChanged
{
    private bool _isEnabled;

    public required string Name { get; init; }
    public required ModuleCategory Category { get; init; }
    public required string Description { get; init; }
    public required ModuleStatus Status { get; init; }
    public Action<bool>? ToggleHandler { get; init; }

    public string StatusLabel => Status switch
    {
        ModuleStatus.Ready => "READY",
        ModuleStatus.Partial => "PARTIAL",
        ModuleStatus.UefnIntegrationRequired => "UEFN INTEGRATION",
        _ => "PLACEHOLDER"
    };

    public bool CanToggle => ToggleHandler is not null;

    public bool IsEnabled
    {
        get => _isEnabled;
        set
        {
            if (_isEnabled == value || ToggleHandler is null) return;
            ToggleHandler(value);
            _isEnabled = value;
            OnPropertyChanged();
        }
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private void OnPropertyChanged([CallerMemberName] string? propertyName = null) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}

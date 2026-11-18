using System.Collections.ObjectModel;
using System.Windows;
using System.Windows.Data;
using System.Windows.Input;
using System.Windows.Threading;
using NightfallFN.Models;
using NightfallFN.Services;

namespace NightfallFN;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<ClientModule> _modules = new(ModuleCatalog.Create());
    private readonly ICollectionView _moduleView;
    private readonly SettingsStore _settingsStore = new();
    private readonly DispatcherTimer _timer;
    private readonly AppSettings _settings;
    private ModuleCategory? _categoryFilter;

    public MainWindow()
    {
        InitializeComponent();
        _settings = _settingsStore.Load();
        _moduleView = CollectionViewSource.GetDefaultView(_modules);
        _moduleView.Filter = FilterModule;
        ModuleList.ItemsSource = _moduleView;
        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += (_, _) => ClockText.Text = DateTime.Now.ToString("HH:mm:ss");
        _timer.Start();
        ClockText.Text = DateTime.Now.ToString("HH:mm:ss");
        CpuText.Text = "See system dashboard";
        MemoryText.Text = "See system dashboard";
        if (_modules.Count > 0) ModuleList.SelectedIndex = 0;
    }

    private bool FilterModule(object item)
    {
        if (item is not ClientModule module) return false;
        if (_categoryFilter.HasValue && module.Category != _categoryFilter.Value) return false;
        var query = SearchBox?.Text?.Trim();
        return string.IsNullOrWhiteSpace(query)
            || module.Name.Contains(query, StringComparison.OrdinalIgnoreCase)
            || module.Description.Contains(query, StringComparison.OrdinalIgnoreCase);
    }

    private void SearchBox_TextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e) =>
        _moduleView.Refresh();

    private void Category_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { Tag: string tag }) return;
        _categoryFilter = Enum.TryParse<ModuleCategory>(tag, true, out var category) ? category : null;
        PageTitle.Text = tag;
        PageSubtitle.Text = $"Browse {tag.ToLowerInvariant()} modules.";
        _moduleView.Refresh();
    }

    private void Overview_Click(object sender, RoutedEventArgs e)
    {
        _categoryFilter = null;
        PageTitle.Text = "Overview";
        PageSubtitle.Text = "Your NIGHTFALL workspace.";
        SearchBox.Clear();
        _moduleView.Refresh();
    }

    private void ModuleList_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (ModuleList.SelectedItem is not ClientModule module)
        {
            InspectorName.Text = "Select a module";
            InspectorDescription.Text = "Choose a module to inspect its status.";
            InspectorStatus.Text = "—";
            ToggleButton.IsEnabled = false;
            return;
        }

        InspectorName.Text = module.Name;
        InspectorDescription.Text = module.Description;
        InspectorStatus.Text = module.StatusLabel;
        ToggleButton.IsEnabled = module.CanToggle;
        ToggleButton.Content = module.IsEnabled ? "Disable module" : "Enable module";
    }

    private void ToggleSelected_Click(object sender, RoutedEventArgs e)
    {
        if (ModuleList.SelectedItem is not ClientModule module || !module.CanToggle) return;
        module.IsEnabled = !module.IsEnabled;
        ToggleButton.Content = module.IsEnabled ? "Disable module" : "Enable module";
    }

    private void QueueProfile_Click(object sender, RoutedEventArgs e)
    {
        var p = _settings.QueueProfile;
        var message = $"Profile: {p.Name}\nMaximum players: {p.MaximumPlayers}\nMinimum players: {p.MinimumPlayers}\nOvertime target: {p.OvertimePlayerTarget}\nMain duration: {p.MainDurationSeconds}s\nOvertime duration: {p.OvertimeDurationSeconds}s\n\nReference values only. Apply these in your UEFN island settings.";
        MessageBox.Show(this, message, "Private Map Queue Profile", MessageBoxButton.OK, MessageBoxImage.Information);
    }

    private void Window_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.RightShift)
        {
            WindowState = WindowState == WindowState.Minimized ? WindowState.Normal : WindowState.Minimized;
            e.Handled = true;
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        _timer.Stop();
        try { _settingsStore.Save(_settings); }
        catch (IOException ex)
        {
            MessageBox.Show(this, $"Settings could not be saved: {ex.Message}", "NIGHTFALL FN",
                MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        base.OnClosed(e);
    }
}

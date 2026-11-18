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
    private readonly ObservableCollection<ClientModule> _modules;
    private readonly ICollectionView _moduleView;
    private readonly SettingsStore _settingsStore = new();
    private readonly SystemTelemetryService _telemetry = new();
    private readonly DispatcherTimer _timer;
    private readonly AppSettings _settings;
    private ModuleCategory? _categoryFilter;

    public MainWindow()
    {
        InitializeComponent();

        _settings = _settingsStore.Load();
        var handlers = new Dictionary<string, Action<bool>>
        {
            ["Performance Dashboard"] = enabled => MetricsCard.Visibility = enabled ? Visibility.Visible : Visibility.Collapsed,
            ["Clock"] = enabled => ClockPanel.Visibility = enabled ? Visibility.Visible : Visibility.Collapsed,
            ["Watermark"] = enabled => BrandBlock.Visibility = enabled ? Visibility.Visible : Visibility.Collapsed
        };

        _modules = new ObservableCollection<ClientModule>(ModuleCatalog.Create(handlers));
        _moduleView = CollectionViewSource.GetDefaultView(_modules);
        _moduleView.Filter = FilterModule;
        ModuleList.ItemsSource = _moduleView;

        SetInitialState("Performance Dashboard", true);
        SetInitialState("Clock", true);
        SetInitialState("Watermark", true);

        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += (_, _) => UpdateDashboard();
        _timer.Start();
        UpdateDashboard();

        if (_modules.Count > 0) ModuleList.SelectedIndex = 0;
    }

    private void SetInitialState(string moduleName, bool enabled)
    {
        var module = _modules.FirstOrDefault(m => m.Name == moduleName);
        if (module is not null) module.IsEnabled = enabled;
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

    private void UpdateDashboard()
    {
        var (cpu, memory) = _telemetry.Sample();
        CpuText.Text = cpu.ToString("0") + "%";
        MemoryText.Text = memory.ToString("0") + "%";
        ClockText.Text = DateTime.Now.ToString("HH:mm:ss");
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
        var window = new QueueProfileWindow(_settings.QueueProfile, profile =>
        {
            _settings.QueueProfile = profile;
            _settingsStore.Save(_settings);
        }) { Owner = this };
        window.ShowDialog();
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

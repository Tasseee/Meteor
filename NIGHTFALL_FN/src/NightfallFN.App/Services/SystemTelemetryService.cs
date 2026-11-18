using System.Runtime.InteropServices;

namespace NightfallFN.Services;

/// <summary>
/// Samples whole-system CPU and RAM. It does not inspect Fortnite or any game process.
/// Sample on a UI timer while the performance dashboard is visible.
/// </summary>
public sealed class SystemTelemetryService
{
    private ulong _lastIdle;
    private ulong _lastKernel;
    private ulong _lastUser;
    private bool _hasPreviousSample;

    public (double CpuPercent, double MemoryPercent) Sample()
    {
        var cpu = 0d;
        if (GetSystemTimes(out var idle, out var kernel, out var user))
        {
            var idleNow = idle.ToUInt64();
            var kernelNow = kernel.ToUInt64();
            var userNow = user.ToUInt64();

            if (_hasPreviousSample)
            {
                var idleDelta = idleNow - _lastIdle;
                var totalDelta = (kernelNow - _lastKernel) + (userNow - _lastUser);
                if (totalDelta > 0)
                    cpu = Math.Clamp(100d * (totalDelta - idleDelta) / totalDelta, 0d, 100d);
            }

            _lastIdle = idleNow;
            _lastKernel = kernelNow;
            _lastUser = userNow;
            _hasPreviousSample = true;
        }

        var memory = new MemoryStatusEx { Length = (uint)Marshal.SizeOf<MemoryStatusEx>() };
        var memoryPercent = GlobalMemoryStatusEx(ref memory) ? memory.MemoryLoad : 0d;
        return (cpu, memoryPercent);
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct FileTime
    {
        public uint Low;
        public uint High;
        public readonly ulong ToUInt64() => ((ulong)High << 32) | Low;
    }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private struct MemoryStatusEx
    {
        public uint Length;
        public uint MemoryLoad;
        public ulong TotalPhysical;
        public ulong AvailablePhysical;
        public ulong TotalPageFile;
        public ulong AvailablePageFile;
        public ulong TotalVirtual;
        public ulong AvailableVirtual;
        public ulong AvailableExtendedVirtual;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GetSystemTimes(out FileTime idleTime, out FileTime kernelTime, out FileTime userTime);

    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool GlobalMemoryStatusEx(ref MemoryStatusEx buffer);
}

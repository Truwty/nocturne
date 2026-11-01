$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -TypeDefinition @'
using System;
using System.Collections.Concurrent;
using System.Runtime.InteropServices;
using System.Threading;
public static class NocturneInput {
    public static volatile bool Closed;
    static readonly ConcurrentQueue<string> queue = new ConcurrentQueue<string>();
    public static void Start() { var thread = new Thread(() => { string line; while ((line = Console.ReadLine()) != null) { if (line.Length < 16384) queue.Enqueue(line); } Closed = true; }); thread.IsBackground = true; thread.Start(); }
    public static string Poll() { string line; return queue.TryDequeue(out line) ? line : null; }
}
[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class DeviceEnumerator {}
[ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IDevices {
    [PreserveSig] int EnumAudioEndpoints(int flow, uint mask, out IntPtr devices);
    [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IDevice device);
}
[ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IDevice {
    [PreserveSig] int Activate(ref Guid iid, uint context, IntPtr parameters, [MarshalAs(UnmanagedType.IUnknown)] out object result);
    [PreserveSig] int OpenPropertyStore(uint access, out IntPtr properties);
    [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
}
[ComImport, Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IMeter {
    [PreserveSig] int GetPeakValue(out float peak);
}
public static class NocturneMeter {
    public static string DeviceId() {
        object enumerator = null; IDevice device = null;
        try {
            enumerator = new DeviceEnumerator();
            Marshal.ThrowExceptionForHR(((IDevices)enumerator).GetDefaultAudioEndpoint(0, 1, out device));
            string id; Marshal.ThrowExceptionForHR(device.GetId(out id)); return id;
        } finally {
            if (device != null) Marshal.ReleaseComObject(device);
            if (enumerator != null) Marshal.ReleaseComObject(enumerator);
        }
    }
    public static float Peak() {
        object enumerator = null; IDevice device = null; object instance = null;
        try {
            enumerator = new DeviceEnumerator();
            Marshal.ThrowExceptionForHR(((IDevices)enumerator).GetDefaultAudioEndpoint(0, 1, out device));
            Guid iid = typeof(IMeter).GUID;
            Marshal.ThrowExceptionForHR(device.Activate(ref iid, 23, IntPtr.Zero, out instance));
            float peak; Marshal.ThrowExceptionForHR(((IMeter)instance).GetPeakValue(out peak));
            return Math.Max(0, Math.Min(1, peak));
        } finally {
            if (instance != null) Marshal.ReleaseComObject(instance);
            if (device != null) Marshal.ReleaseComObject(device);
            if (enumerator != null) Marshal.ReleaseComObject(enumerator);
        }
    }
}
'@
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) {
    $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    if (-not $task.Wait(5000)) { throw 'Desktop player timed out' }
    return $task.Result
}
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
[NocturneInput]::Start()
$pinned = ''; $audio = $false; $refresh = 0; $commandStatus = ''; $audioError = ''; $o = @{}
while ($true) {
    if ([NocturneInput]::Closed) { exit }
    try {
        while ($line = [NocturneInput]::Poll()) {
            $cmd = $line | ConvertFrom-Json
            if ($cmd.action -eq 'quit') { exit }
            if ($cmd.action -eq 'select') { $pinned = [string]$cmd.app; $refresh = 0; continue }
            if ($cmd.action -eq 'audio') { $audio = [bool]$cmd.enabled; continue }
            $target = @($mgr.GetSessions() | Where-Object { $_.SourceAppUserModelId -eq $cmd.target }) | Select-Object -First 1
            if (-not $target) { $commandStatus = 'Player disconnected'; continue }
            $ok = $false
            switch ($cmd.action) {
                 'toggle' {
                    $info = $target.GetPlaybackInfo()
                    if ($info.Controls.IsPlayPauseToggleEnabled) { $ok = Await ($target.TryTogglePlayPauseAsync()) ([bool]) }
                    elseif ($info.PlaybackStatus -eq 'Playing') { $ok = Await ($target.TryPauseAsync()) ([bool]) }
                    else { $ok = Await ($target.TryPlayAsync()) ([bool]) }
                }
                'next' { $ok = Await ($target.TrySkipNextAsync()) ([bool]) }
                'previous' { $ok = Await ($target.TrySkipPreviousAsync()) ([bool]) }
                'seek' {
                    $ms = [Math]::Max(0, [Math]::Min([double]$cmd.millis, $target.GetTimelineProperties().EndTime.TotalMilliseconds))
                    $ok = Await ($target.TryChangePlaybackPositionAsync([int64]($ms * 10000))) ([bool])
                }
            }
            $commandStatus = if ($ok) { 'Command accepted' } else { 'Player rejected command' }
            $refresh = 0
        }
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        if ($now - $refresh -ge 500) {
            $refresh = $now; $sessions = @($mgr.GetSessions()); $session = $null
            if ($pinned) { $session = $sessions | Where-Object { $_.SourceAppUserModelId -eq $pinned } | Select-Object -First 1 }
            else {
                $session = $sessions | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' -and $_.GetPlaybackInfo().PlaybackStatus -eq 'Playing' } | Select-Object -First 1
                if (-not $session) { $session = $sessions | Where-Object { $_.GetPlaybackInfo().PlaybackStatus -eq 'Playing' } | Select-Object -First 1 }
                if (-not $session) { $session = $sessions | Where-Object { $_.SourceAppUserModelId -like '*Spotify*' } | Select-Object -First 1 }
                if (-not $session) { $session = $mgr.GetCurrentSession() }
            }
            $o = [ordered]@{ apps = @($sessions | ForEach-Object { $_.SourceAppUserModelId }); selected = $pinned; commandStatus = $commandStatus }
            try { $o.deviceId = [NocturneMeter]::DeviceId() } catch { $o.deviceId = '' }
            $o.sessions = @($sessions | ForEach-Object {
                $entry = @{ id = $_.SourceAppUserModelId; playing = $false; issue = '' }
                try {
                    $info = $_.GetPlaybackInfo(); $c = $info.Controls; $entry.playing = $info.PlaybackStatus -eq 'Playing'
                    $entry.toggle = $c.IsPlayPauseToggleEnabled -or $c.IsPlayEnabled -or $c.IsPauseEnabled
                    $entry.next = $c.IsNextEnabled; $entry.previous = $c.IsPreviousEnabled; $entry.seek = $c.IsPlaybackPositionEnabled
                } catch { $entry.issue = 'Player did not provide playback capabilities' }
                $entry
            })
            if ($session) {
                $p = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                $tl = $session.GetTimelineProperties(); $pb = $session.GetPlaybackInfo(); $controls = $pb.Controls
                $o.title = $p.Title; $o.artist = $p.Artist; $o.album = $p.AlbumTitle
                $o.pos = [int64]$tl.Position.TotalMilliseconds; $o.dur = [int64]$tl.EndTime.TotalMilliseconds
                $o.updated = [int64]$tl.LastUpdatedTime.ToUnixTimeMilliseconds(); $o.now = $now
                $o.playing = $pb.PlaybackStatus -eq 'Playing'; $o.app = $session.SourceAppUserModelId
                $o.toggle = $controls.IsPlayPauseToggleEnabled -or ($o.playing -and $controls.IsPauseEnabled) -or ((-not $o.playing) -and $controls.IsPlayEnabled); $o.next = $controls.IsNextEnabled
                $o.previous = $controls.IsPreviousEnabled; $o.seek = $controls.IsPlaybackPositionEnabled
            }
        }
        $o.now = $now
        $o.peak = 0.0; $o.audioAvailable = $false
        if ($audio) {
            try { $o.peak = [NocturneMeter]::Peak(); $o.audioAvailable = $true; $audioError = '' }
            catch { $audioError = 'Desktop output meter unavailable' }
        }
        $o.audioError = $audioError
        $json = $o | ConvertTo-Json -Compress -Depth 4
        $json = [regex]::Replace($json, '[^\x00-\x7F]', { param($m) '\u{0:x4}' -f [int][char]$m.Value })
        [Console]::Out.WriteLine($json); [Console]::Out.Flush()
    } catch {
        [Console]::Out.WriteLine((@{error=$_.Exception.Message} | ConvertTo-Json -Compress))
        [Console]::Out.Flush()
        Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Milliseconds $(if ($audio) { 50 } else { 250 })
}

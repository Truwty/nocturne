$ErrorActionPreference = 'SilentlyContinue'
# Send text as UTF-8 so non-English titles (Russian, Japanese, ...) reach the mod intact
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, [Type]$type) {
    $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    $t.Result
}
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
while ($true) {
    $session = $null
    $sessions = @($mgr.GetSessions())
    foreach ($s in $sessions) {
        if ($s.SourceAppUserModelId -like '*Spotify*' -and $s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $session = $s; break }
    }
    if (-not $session) {
        foreach ($s in $sessions) {
            if ($s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') { $session = $s; break }
        }
    }
    if (-not $session) {
        foreach ($s in $sessions) {
            if ($s.SourceAppUserModelId -like '*Spotify*') { $session = $s; break }
        }
    }
    if (-not $session) { $session = $mgr.GetCurrentSession() }
    if ($session) {
        $p = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
        $tl = $session.GetTimelineProperties()
        $pb = $session.GetPlaybackInfo()
        $o = [ordered]@{
            title   = $p.Title
            artist  = $p.Artist
            album   = $p.AlbumTitle
            pos     = [int64]$tl.Position.TotalMilliseconds
            dur     = [int64]$tl.EndTime.TotalMilliseconds
            updated = [int64]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
            now     = [int64][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            playing = ($pb.PlaybackStatus -eq 'Playing')
            app     = $session.SourceAppUserModelId
        }
        $json = ($o | ConvertTo-Json -Compress)
        # escape every non-ASCII character as \uXXXX, so the line is plain ASCII whatever the code page
        $json = [regex]::Replace($json, '[^\x00-\x7F]', { param($m) '\u{0:x4}' -f [int][char]$m.Value })
        [Console]::Out.WriteLine($json)
    } else {
        [Console]::Out.WriteLine('{}')
    }
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 700
}

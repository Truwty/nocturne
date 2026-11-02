param([string]$SourcePath, [string]$WavePath, [switch]$Capture)
$ErrorActionPreference = 'Stop'
Add-Type -Path $SourcePath
$player = New-Object System.Media.SoundPlayer($WavePath)
$player.Load()
$stream = if ($Capture) { New-Object NocturneAudio.Capture($PID) } else { $null }
try {
    [Console]::Out.WriteLine('ready')
    while ($line = [Console]::ReadLine()) {
        if ($line -eq 'quit') { break }
        if ($line -eq 'play') { $player.PlayLooping(); [Console]::Out.WriteLine('playing') }
        if ($line -eq 'stop') { $player.Stop(); [Console]::Out.WriteLine('stopped') }
        if ($line -eq 'sample') { [Console]::Out.WriteLine((@{ pcm = $stream.Snapshot(); status = $stream.Status } | ConvertTo-Json -Compress)) }
        [Console]::Out.Flush()
    }
} finally { $player.Stop(); $player.Dispose(); if ($stream) { $stream.Dispose() } }

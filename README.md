<div align="center">

![Nocturne](docs/banner.svg)

[Download](https://github.com/Truwty/nocturne/releases) · [Issues](https://github.com/Truwty/nocturne/issues) · [Licensing](LICENSE)

</div>

Cinematic world lyrics and desktop music controls for **Minecraft Java 26.1.2 + Fabric**. Entirely client-side, with automatic desktop music detection. **No Spotify login, developer application, Client ID, or API token.**

![Nocturne Studio](docs/studio.png)

## Install

1. Use **Minecraft 26.1.2**, **Java 25**, **Fabric Loader 0.20.1+**, and [Fabric API 0.155.3+26.1.2](https://modrinth.com/mod/fabric-api/versions?g=26.1.2).
2. Replace older Nocturne JARs with `nocturne-0.7.0-beta.1.jar` from [Releases](https://github.com/Truwty/nocturne/releases). Keep one version in the instance's `mods` folder.
3. Play music in your desktop app. Press **N**, or click **Nocturne** on the title screen, to open Studio.

No server installation is needed. Hypixel itself has not been directly tested; follow the server's own mod rules.

## Recovery, caching and playback

Player diagnoses lookup failure, playback-clock drift and hidden placement separately. The recovery button changes to Retry lyric lookup, Resync playback, Enable lyrics or Reconnect player as appropriate; healthy playback keeps Restore visible lyrics available. Intros and advertisements are not treated as placement failures. Provider timing can still need manual calibration in Lyrics.

Downloaded lyrics and their word timings persist in `config/nocturne/cache/lyrics` across launches. Expiry defaults to 30 days and is adjustable from 1–90 days in Health/advanced settings. The disk cache is bounded to 256 entries / 64 MiB; corrupt entries are preserved separately and fetched again. Retry lyrics bypasses both memory and disk for the current automatic match. Manual corrections remain separate and take priority; Use automatic lyrics again clears that override.

Matching removes remaster/feature/browser noise, extracts artist/title pairs from browser titles when artist metadata is absent, preserves live/acoustic/remix intent and rejects ambiguous artists. If metadata is insufficient, use the alternative-match picker rather than expecting an exact automatic match.

Seeking, pause/resume, track changes, recognized advertisements, session loss and Windows output-device changes reset the lyric clock and stale effects. Health has an explicit resynchronization action. Windows Apps diagnostics show each detected app's advertised play/pause, seek and next/previous support. macOS/Linux display detected apps but explicitly mark per-action capability details unavailable.

Settings use schema version 1. Legacy settings are backed up before migration, unknown settings are preserved, corrupt JSON is quarantined, and valid backups are restored when available. Damaged libraries similarly recover from backup or start safely after preserving the damaged original. Health reports recovery and write failures. Future config versions are read-only to prevent downgrades from overwriting them.

## Tested compatibility

The local suite exercised Adrenaline 26.5.1 and Fabulously Optimized 13.6.0 client configurations, Derivative 25.1.0 and Complementary Reimagined r5.9.3 shader packs, Stratum 128x Build 59, GUI scales 1–4, resource reloads and world reconnects. These runs use Nocturne's pinned Fabric Loader/API and Iris/Sodium versions; see [the exact test matrix and limits](VALIDATION.md). External pack files are not bundled or redistributed.

![Lyrics with shader/resource packs](docs/compatibility-shaders.png)

## If a song is detected but lyrics are missing

Press **N → Player → Retry lyrics** to fetch again. The context-sensitive recovery button offers the appropriate action; **Restore visible lyrics** resets the transform, follows your view, enables through-wall display, and selects the compatible projected renderer. Both controls are on the main player page. No config deletion is needed.

The player page shows the lyric lookup status. Search alternatives or import a local LRC in Lyrics when the providers do not have the track. This release rejects invalid Windows timestamps, retries misses, refreshes lyrics when track duration arrives late, and bounds individual lookup time. Valid sparse timeline updates still advance normally.

## Studio

The seven tabs bring playback, lyric correction and visual editing into one interface. Small windows use scrolling; the coloured scrollbar and Scroll hint indicate additional controls. Advanced settings opens the Glass or classic settings interface. Buttons and sliders follow the selected theme, with keyboard focus and reduced-motion support.

### Player and apps

Play/pause, next/previous, ±10-second seeking and a draggable timeline control the existing desktop player. Controls are disabled when that player does not advertise support, or while the visual demo is active. Rejected commands appear as a status message.

Auto prefers playing Spotify, then another playing session, with paused/current-player fallback. The **Apps** tab lists detected Windows/Linux sessions. Pin a player to retain that selection, including while it is closed; Nocturne reconnects when it returns. Auto clears the pin. Reconnect restarts the media helper with the pin preserved.

Windows uses the bundled PowerShell/Windows media-session helper; no Python installation is required to use the mod. macOS uses Spotify/Music AppleScript integration and may require OS automation permission. Linux requires `playerctl` and MPRIS. Windows is validated locally; macOS/Linux transport integration remains unverified.

### Lyric correction

The **Lyrics** tab searches LRCLIB for alternative synchronized matches. Select a result to keep that correction for the current song. If the song changed during a search, selection is rejected to avoid saving the wrong correction.

Import a UTF-8 `.lrc` or enhanced-LRC file by entering its full path and choosing **Import LRC**. Files must be at most 1 MiB and contain timestamps. Imports and chosen matches take priority over online lookup and work offline. **Use automatic lyrics again** removes that song's override.

**Earlier / Later / Reset offset** saves a separate timing adjustment for each song. Positive offsets show lyrics earlier. The original global offset remains available under Detailed settings.

Lyrics are stored as hashed track identifiers under `config/nocturne/lyrics/`. Presets, offsets and the pinned player are kept in `config/nocturne/library.json`; general settings remain in `config/nocturne.json`. Writes use temporary files and atomic replacement when supported. A malformed library is preserved and becomes read-only with an error message until repaired.

### Theme editor

Set background, accent and text using `#RRGGBB` fields. Adjust opacity, row spacing and interface font. Changes apply immediately, including to the Glass settings interface. Name a preset to save/load it; saved names are listed below. **Copy JSON / Paste JSON** shares themes through the clipboard. Imports validate colour, mode and numeric ranges before applying changes.

### Placement editor

Choose one of seven placement modes, then **Edit in world**. Drag to move, rotate or resize; right-drag adjusts roll; the mouse wheel adjusts distance. Real lyrics remain visible behind the editor. Save or Escape retains the edit. Join a world and start music/the visual demo for a live preview.

Each placement mode maintains its own active transform profile. Named profiles and clipboard JSON provide additional save/share slots for that mode. Line anchors, edited transforms and rotations blend using frame-rate-independent smoothing. Transition duration is configurable.

### Motion, performance and accessibility

- Independent flash and shake switches, both off by default.
- Reduced motion suppresses flash, camera beat zoom, animated styling, particles and ambient animation.
- High-contrast lyrics use white text with a dark readability treatment; the lyric size control supports larger text.
- Lightweight, Balanced and Ultra quality levels change glow passes and cap particles at 20, 48 and 180 respectively. Additional particle limits and distance/behind-camera culling bound effects work.
- Apply lightweight preset disables expensive glow/chromatic/cloud drift effects together.

The original world effects remain: seven placements, eight animations, twelve visual presets, four lyric fonts, chromatic split, sparkles, cinematic bars, battle words, cloud lettering, optional through-wall rendering and rain tinting.

### Optional audio reaction

**Desktop audio reactive** is opt-in and Windows-only. It reads the actual peak level of the default output device using [Windows Core Audio](https://learn.microsoft.com/en-us/windows/win32/api/endpointvolume/nn-endpointvolume-iaudiometerinformation), then smooths it into lyric scale pulses. Adjust Audio sensitivity to suit the output level.

This meter includes **all sound on that output**, including Minecraft and notifications. It does not isolate Spotify, detect BPM, use the microphone, record audio, or transmit audio. Health reports when the output meter is unavailable. Reduced motion suppresses audio-driven movement. With audio reaction off, the original lyric-timing pulse remains available.

## Coverage and privacy

Automatic detection reads metadata published by desktop players; it does not obtain Spotify's own lyric feed. Live lyric availability and synchronization depend on external catalogs. Unsynchronized fallback has estimated timing. The demo animates original text without playing audio.

Track metadata is sent to lyric/artwork providers for lookup (LRCLIB, the upstream NetEase fallback and Apple's artwork catalog). Imported local lyrics bypass lyric lookup; artwork lookup is separate. Audio metering stays local. No Spotify credentials are requested or stored.

Shader-compatible projected text is available in Detailed settings. Automated tests exercise vanilla, Sodium 0.9.2 and Iris 1.11.4 with Minecraft 26.1.2. An Iris test without a shader pack does not establish compatibility with every shader pack.

## Build and test

With Java 25 as `JAVA_HOME`:

```powershell
./gradlew.bat build
./gradlew.bat runClientGameTest
./gradlew.bat runClientGameTest -Pcompat=sodium
./gradlew.bat runClientGameTest -Pcompat=iris
```

Compatibility dependencies are pinned and used only with the requested Gradle property; they are not bundled in the release. GitHub CI runs unit/build checks and a four-profile client test matrix with a virtual display.

The optional Windows transport test creates and controls only its own muted media session:

```powershell
python -m pip install -r scripts/media-test-requirements.txt
python scripts/windows_media_smoke.py
```

Those Python dependencies are for developer tests only. See [validation](VALIDATION.md) for the tested behavior and limits.

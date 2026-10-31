# Validation: 0.2.0-beta.1

Environment: Windows 11 x64, Minecraft 26.1.2, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2, JDK 25, Python 3.14 x64.

## Automated checks

- **27 Java tests**: playback state, LRC timestamps/offsets/Unicode, enhanced word timing, seeks in either direction, plain lyrics, path sanitization, provider request encoding, local overrides, offline cache, stale-response isolation, rate limits, damaged-cache recovery, exact fallback matching, and animation behavior at 30 vs 144 FPS.
- **9 Python tests**: command validation, Unicode, control rejection, idle state, missing manual source, Auto music-vs-speech priority, browser playback priority, and retaining a paused source.
- Packaged executable startup/read/shutdown smoke test against real Windows media sessions.
- Build against actual Minecraft 26.1.2 libraries.

## Interactive checks

- Minecraft launches with Nocturne 0.2.0-beta.1.
- Player, focused lyrics, appearance, and controls screens visually inspected at 1280×800 / GUI scale 2.
- Graphite and Paper themes switched in the running client; palette, labels, and saved settings verified.
- Original timestamped local LRC rendered and scrolled in sync with a separate muted Windows media session.
- Clicking a lyric and dragging the progress bar produced the expected seek positions in the synthetic session.
- Music HUD, lyric subtitles, and the N hotkey checked in a single-player development world.
- World pin control places the lyric display at the projected camera target in the development world. Block occlusion and large camera/distance changes still need broader testing.
- Real Spotify metadata/artwork display; direct LRCLIB network lookup returned synced lyrics for a known catalog track. Other lookups also returned temporary provider failures, so network/coverage limits are real.

## Remaining limits

- This is an early beta; full compatibility and perfect timing are not claimed.
- Every music service has not been tested end to end. LRCLIB coverage and availability vary.
- Hypixel and other multiplayer servers have not been tested or approved.
- World pins use screen-space camera projection and a local collision raycast, not GPU depth-tested text.
- Windows 10/ARM, unusual DPI/window sizes, screen readers, long sessions, suspend/resume, and mod/resource-pack combinations need broader testing.
- The bundled executable is unsigned.

The development launcher uses a dummy Fabric identity; its Realms/authentication warnings do not validate multiplayer login.

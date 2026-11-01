# Validation — Nocturne 0.6.0-beta.1

Validated locally on Windows with Java 25, Minecraft 26.1.2, Fabric Loader 0.20.1 and Fabric API 0.155.3+26.1.2.

## Automated checks

- **32 JUnit tests** (one live-provider test is opt-in): timestamp selection, word timing, chunking, empty lyrics, session metadata/Unicode, paused and bounded playback positions, invalid/future timestamps and valid sparse updates, exact-to-normalized lyric fallback, artist mismatch rejection and prompt plain fallback, per-song offsets, local imports/override clearing, invalid-file preservation, preset round trips/isolation, pinned-player persistence, invalid names, malformed-library preservation, non-finite offset rejection, frame-rate independence, shortest-angle rotation and alternative-match parsing, and demo/desktop correction isolation.
- **Windows transport integration**: `scripts/windows_media_smoke.py` creates its own muted media session and checks app selection, pause, seek, next, previous, resume, real output-meter availability, strict missing-player pinning, reconnection, and helper termination on parent EOF. It never controls another desktop player.
- **Minecraft client game test**, locally passed with vanilla, Sodium **0.9.2** and Iris **1.11.4**: real world lyric geometry, rain hook, all seven placements/four fonts, projected HUD fallback, both original settings styles, all seven Studio tabs, local correction application, per-song offsets, theme persistence and palette shortcuts, visibility-recovery persistence, reduced-motion flash/FOV suppression, placement drag/rotation/profile restoration, resized 320×240 GUI bounds/scrolling, resource reload and world disconnect/reconnect.
- Client screenshots are actual framebuffer captures. Fabric's test screenshot utility re-renders outside Sodium's terrain-uniform lifecycle, so compatibility runs use Minecraft's framebuffer capture instead; the gameplay assertions remain enabled.
- GitHub workflow runs Windows build/unit checks and a Linux virtual-display client test matrix for vanilla, Sodium and Iris. Screenshots and logs are retained as artifacts.

## Limits

Live Spotify accounts and every third-party player were not directly tested. Capability flags and command rejection vary by player. macOS/Linux integration code is included but its real desktop transport behavior is not validated by the Linux rendering tests. Windows media-session testing uses an isolated real Windows fixture.

The real Core Audio output meter was exercised for availability with a muted fixture. It meters the default output device (including Minecraft), not an isolated Spotify stream, frequency bands or BPM. Exclusive-mode output and device/driver behavior can affect metering. No microphone or audio recording is used.

Iris was tested without an external shader pack; this does not prove compatibility with every pack. Hypixel itself was not joined. [Windows timeline positions](https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessiontimelineproperties) are extrapolated from their update timestamps; impossible timestamps are rejected. [LRCLIB lookup](https://lrclib.net/docs) uses track metadata and optional duration. Network lyric coverage/matching depends on provider availability. Deterministic tests do not call live catalogs. With `NOCTURNE_LIVE_LYRICS=true`, an additional test resolves known desktop metadata through the real public provider; it passed locally. An opt-in Minecraft check also sent known media metadata with an invalid timeline timestamp through the real service lookup and confirmed visible world geometry.


## Performance changes

Idle Windows helper output now runs at 4 Hz rather than 20 Hz; audio-enabled metering retains 20 Hz. Environment/light sampling runs at most 10 Hz, cloud ring sine/cosine values are precomputed, Glass setting rows are cached, and frames without lyrics skip environment/effect preparation. Balanced particles are capped at 48 (previously 90); Lightweight and Ultra retain 20/180 caps. Successful and confirmed instrumental lyric caching is limited to 128 entries, and misses retry rather than remaining cached indefinitely. The lookup request budget is 18 seconds per attempt.

These are bounded-work improvements, not a measured FPS gain across all hardware or modpacks. No live Hypixel session or external shader pack was used.

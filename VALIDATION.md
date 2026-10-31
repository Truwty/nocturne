# Validation — Nocturne 0.5.0-beta.1

Validated locally on Windows with Java 25, Minecraft 26.1.2, Fabric Loader 0.20.1 and Fabric API 0.155.3+26.1.2.

## Automated checks

- **23 JUnit tests**: timestamp selection, word timing, chunking, empty lyrics, session metadata/Unicode, paused and bounded playback positions, per-song offsets, local imports/override clearing, invalid-file preservation, preset round trips/isolation, pinned-player persistence, invalid names, malformed-library preservation, non-finite offset rejection, frame-rate independence, shortest-angle rotation and alternative-match parsing.
- **Windows transport integration**: `scripts/windows_media_smoke.py` creates its own muted media session and checks app selection, pause, seek, next, previous, resume, real output-meter availability, strict missing-player pinning, reconnection, and helper termination on parent EOF. It never controls another desktop player.
- **Minecraft client game test**, locally passed with vanilla, Sodium **0.9.2** and Iris **1.11.4**: real world lyric geometry, rain hook, all seven placements/four fonts, projected HUD fallback, both original settings styles, all seven Studio tabs, local correction application, per-song offsets, theme persistence, reduced-motion flash/FOV suppression, placement drag/rotation/profile restoration, resized 320×240 GUI bounds/scrolling, resource reload and world disconnect/reconnect.
- Client screenshots are actual framebuffer captures. Fabric's test screenshot utility re-renders outside Sodium's terrain-uniform lifecycle, so compatibility runs use Minecraft's framebuffer capture instead; the gameplay assertions remain enabled.
- GitHub workflow runs Windows build/unit checks and a Linux virtual-display client test matrix for vanilla, Sodium and Iris. Screenshots and logs are retained as artifacts.

## Limits

Live Spotify accounts and every third-party player were not directly tested. Capability flags and command rejection vary by player. macOS/Linux integration code is included but its real desktop transport behavior is not validated by the Linux rendering tests. Windows media-session testing uses an isolated real Windows fixture.

The real Core Audio output meter was exercised for availability with a muted fixture. It meters the default output device (including Minecraft), not an isolated Spotify stream, frequency bands or BPM. Exclusive-mode output and device/driver behavior can affect metering. No microphone or audio recording is used.

Iris was tested without an external shader pack; this does not prove compatibility with every pack. Hypixel itself was not joined. Network lyric coverage/matching depends on provider availability; deterministic tests do not call live lyric catalogs.

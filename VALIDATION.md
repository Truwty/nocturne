# Validation — Nocturne 0.9.0-beta.1

Validated locally on Windows with Java 25, Minecraft 26.1.2, Fabric Loader 0.20.1 and Fabric API 0.155.3+26.1.2.

## Automated checks

- **86 JUnit tests** (one live-provider test is opt-in): timestamp selection, word timing, chunking, empty lyrics, session metadata/Unicode, paused and bounded playback positions, invalid/future timestamps and valid sparse updates, exact-to-normalized lyric fallback, artist mismatch rejection and prompt plain fallback, per-song offsets, local imports/override clearing, invalid-file preservation, preset round trips/isolation, pinned-player persistence, invalid names, malformed-library quarantine and recovery, non-finite offset rejection, frame-rate independence, shortest-angle rotation and alternative-match parsing, and demo/desktop correction isolation.
- **Windows transport integration**: `scripts/windows_media_smoke.py` creates its own muted media session and checks app selection, pause, seek, next, previous, resume, real output-meter availability, strict missing-player pinning, reconnection, and helper termination on parent EOF. It never controls another desktop player.
- **Minecraft client game test**, locally passed with vanilla, Sodium **0.9.2** and Iris **1.11.4**: real world lyric geometry, rain hook, all seven placements/four fonts, projected HUD fallback, both original settings styles, all seven Studio tabs, local correction application, per-song offsets, theme persistence and palette shortcuts, visibility-recovery persistence, reduced-motion flash/FOV suppression, placement drag/rotation/profile restoration, resized 320×240 GUI bounds/scrolling, resource reload and world disconnect/reconnect.
- Client screenshots are actual framebuffer captures. Fabric's test screenshot utility re-renders outside Sodium's terrain-uniform lifecycle, so compatibility runs use Minecraft's framebuffer capture instead; the gameplay assertions remain enabled.
- GitHub workflow runs Windows build/unit checks and a Linux virtual-display client test matrix for vanilla, Sodium and Iris. Screenshots and logs are retained as artifacts.

## Limits

Live Spotify accounts and every third-party player were not directly tested. Capability flags and command rejection vary by player. macOS/Linux integration code is included but its real desktop transport behavior is not validated by the Linux rendering tests. Windows media-session testing uses an isolated real Windows fixture.

The real Core Audio output meter was exercised for availability with a muted fixture. It meters the default output device (including Minecraft), not an isolated Spotify stream, frequency bands or BPM. Exclusive-mode output and device/driver behavior can affect metering. No microphone or audio recording is used.

Real pack combinations are listed below; these do not prove compatibility with every shader pack. Hypixel itself was not joined. [Windows timeline positions](https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessiontimelineproperties) are extrapolated from their update timestamps; impossible timestamps are rejected. [LRCLIB lookup](https://lrclib.net/docs) uses track metadata and optional duration. Network lyric coverage/matching depends on provider availability. Deterministic tests do not call live catalogs. With `NOCTURNE_LIVE_LYRICS=true`, an additional test resolves known desktop metadata through the real public provider; it passed locally. An opt-in Minecraft check also sent known media metadata with an invalid timeline timestamp through the real service lookup and confirmed visible world geometry.


## Performance changes

Idle Windows helper output now runs at 4 Hz rather than 20 Hz; audio-enabled metering retains 20 Hz. Environment/light sampling runs at most 10 Hz, cloud ring sine/cosine values are precomputed, Glass setting rows are cached, and frames without lyrics skip environment/effect preparation. Balanced particles are capped at 48 (previously 90); Lightweight and Ultra retain 20/180 caps. Successful and confirmed instrumental lyric caching is limited to 128 entries, and misses retry rather than remaining cached indefinitely. The lookup request budget is 18 seconds per attempt.

These are bounded-work improvements, not a measured FPS gain across all hardware or modpacks. No live Hypixel session was used. Shader pack coverage is listed below.


## Reliability upgrade coverage

Additional tests cover disk cache reuse across instances, enhanced word-timing/estimated-flag preservation, expiry, manual eviction, corrupt cache quarantine, config migration/backup restore/future-version protection, recovery classification, browser metadata, remasters/features, live-version intent, shared-word artist rejection, seek/pause/resume/device transitions and per-player capability isolation. The optional live lookup also passed locally. The Windows integration fixture passed after adding per-session capability reporting and default output-device identity.

| Local combination | Shader/resource packs | Result |
| --- | --- | --- |
| Sodium 0.9.2 + Lithium 0.24.7 + FerriteCore 9.0.0 | Vanilla resources | Client checks passed |
| Adrenaline 26.5.1+mc26.1.2.fabric, client-supported files + Iris | Derivative 25.1.0; Stratum 128x Early Access Build 59 | Shader and resource activation asserted; lyric/UI/reload/reconnect checks passed |
| Fabulously Optimized 13.6.0 | Complementary Reimagined r5.9.3; Stratum 128x Early Access Build 59 | Shader and resource activation asserted; lyric/UI/reload/reconnect checks passed |

The modpack manifests request Fabric Loader 0.19.5; these test instances use Nocturne's pinned 0.20.1 and Fabric API 0.155.3, with the same pinned Sodium/Iris versions supplied by Gradle to avoid duplicate mods. Adrenaline files explicitly marked client-unsupported were omitted. These are isolated integrated-server client tests, not a test of every feature in either pack or a live server certification. Shader packs emitted non-fatal compatibility warnings; activation and Nocturne rendering were verified. External pack files remain outside the repository and release.

Each client test covers GUI scales 1, 2, 3 and 4 at a 1280×960 window, plus a 640×480 / GUI-scale-2 window, all Studio tabs, resource reload and world reconnection. The GitHub matrix now also runs the pinned optimized combination; external shader/modpack tests are local hardware checks and are not silently substituted for CI coverage.

For reproducible local fixture runs, `runClientGameTest` accepts `NOCTURNE_TEST_INSTANCE` (directory containing client mods/config), `NOCTURNE_TEST_SHADER` (shader ZIP) and `NOCTURNE_TEST_RESOURCE` (resource ZIP). Fixtures are copied only into the verified `build/run/clientGameTest` scratch directory. Its mod/config/resource/shader folders are reset before each run. No installed Minecraft profile is modified.

## Lyrics workspace upgrade (0.8)

The unit suite has 68 cases: 67 pass offline and one live-catalog case is opt-in. New cases cover source word starts/independent ends, Enhanced LRC round trips/repeated timestamps/offsets, safe YAML rejection and legacy fallback, estimated provenance, optional language alignment, provider YRC language layers, line/word edit validation and undo/redo, one-step drag undo, lossless local documents across instances, language sidecars, per-song profile storage and logical RTL timing order. No machine translation or guaranteed catalog coverage is implied.

The Minecraft client suite now also opens all four workspace tabs, saves and undoes line edits, drags a word boundary and saves it through the real screen callbacks, checks song profiles preserve global settings, and exercises Arabic/Hebrew/CJK text with the Unicode font. Workspace widgets are checked at GUI scales 1–4 and after scrolling. The current local vanilla run passed; both Fabulously Optimized + Complementary + Stratum and Adrenaline + Derivative + Stratum runs also passed with real shader/resource activation asserted. Existing pack validation above remains evidence for the 0.7 baseline; CI repeats the four pinned renderer profiles for 0.8.

Confidence text and merged vocal intervals are computed once per immutable lyric document. Line and upcoming-vocal lookup use binary search; shaping keeps at most 512 cached components. These bounds avoid rescanning complete lyric documents on every HUD frame; no hardware-independent FPS claim is made. Source timings determine progress; line-only lyrics keep estimated word timing. Countdown gaps are derived only from provided line/word bounds. Enhanced LRC exports cannot retain every overlapping word end; local JSON documents retain those bounds, language layers and provenance.

## Interface/customization upgrade (0.9)

86 JUnit cases: 85 pass offline and one live-provider test remains opt-in. Eighteen new cases cover HSVA/ARGB round trips and invalid values, bounded defensive undo histories, persistent/reordered recent colors, category-scoped favorites, gameplay/menu isolation, layout serialization and rejection, viewport scaling/safe margins, snapping/clamping, preset envelope limits, category/description search and gamepad edge/repeat behavior.

The Minecraft suite tests that preview/color changes do not mutate applied settings, Cancel discards drafts, Apply commits, theme/world-placement Undo/Redo restore values, preset JSON exports/imports, numeric settings remain searchable, controller dispatch focuses and activates native controls, HUD boundary dragging saves actual layout positions, and menu visibility/positions remain separate. New screens are visited at GUI scales 1–4 and after scrolling; resource reload and reconnect coverage remains enabled. Screenshots are captured from the actual Minecraft framebuffer.

Controller coverage uses the same action-dispatch path as native GLFW input plus deterministic edge/repeat tests. Physical gamepad hardware, device-specific mappings and Controlify/MidnightControls hardware behavior are unverified. GLFW polling yields when either controller mod is installed; no controller library is bundled. Text entry needs a keyboard or external input facility.

HUD defaults hide optional widgets while menus are open; each context is configurable. Safe margins refer to the screen edge, not every other mod's overlay. User placement can overlap other HUD content; alignment guides and the editor provide the correction. Cinematic bars and world/projected lyric geometry are not 2D draggable HUD widgets.

Local 0.9 runs passed on vanilla plus Fabulously Optimized/Complementary/Stratum and Adrenaline/Derivative/Stratum, with real shader/resource activation asserted. The final screenshot pass uses Fabric's framebuffer-resize hook and waits for rendered frames before capture, avoiding stale/distorted captures after GUI resizing. CI repeats Windows packaging and the four pinned renderer profiles for the release commit.

## Playback/integrations upgrade (0.10)

113 JUnit cases: 112 pass offline and one live lyric-catalog case is opt-in. New tests cover deterministic player ranking/pins/ties, PKCE challenge/state/callback validation, credential encryption/corruption/deletion, artwork byte/dimension limits and thumbnails, Spotify playback decoding/capability isolation, refresh-token retention, mocked volume/shuffle/repeat/queue endpoints, 403 messages, 429 backoff and disconnect cleanup. API fixtures run on an isolated loopback server with dummy credentials; no Spotify account is authorized by the tests.

The isolated muted Windows SMTC fixture passed real pause/resume, seek, previous/next, shuffle, repeat off/track/list, priority command acceptance, strict missing pins, output metering and EOF shutdown. Only the fixture session is targeted. Native Minecraft checks visit all four integration pages, open the real keybind screen, render original demo artwork and check widget bounds at GUI scales 1–4; resource reload/world reconnect coverage remains enabled.

Actual Spotify browser consent, Premium/account eligibility and remote device behavior require an authorized account and remain unverified. macOS AppleScript and Linux playerctl commands remain unverified on those operating systems. Queue and device volume are implemented for the optional Spotify account route; desktop media detection remains the default. Windows advertises shuffle/repeat per app; an app can still reject an advertised command. No unsupported queue reordering/removal or Windows master-volume substitution is offered.

Local 0.10 vanilla and Fabulously Optimized + Complementary Reimagined r5.9.3 + Stratum 128x runs passed, including asserted shader/resource activation, the new integration pages, resource reload and world reconnection. CI repeats Windows packaging and the four pinned renderer profiles on the release commit.

## Performance upgrade (0.11)

132 JUnit cases: 131 pass offline and one live lyric-provider case remains opt-in. Nineteen new cases cover adaptive degradation, recovery hysteresis, short/very slow/invalid frames, idle-to-foreground recovery, distance scaling, independent cache byte/entry limits, LRU reuse, TTL/clock changes and defensive image data. Real loopback HTTP fixtures verify superseded catalog and image exchanges are cancelled, cannot apply stale artwork, and repeated tracks reuse a bounded thumbnail without network traffic. A delayed-body fixture confirms the deadline remains enforced after response headers arrive.

The native client test sets particle/glow/cloud/lingering limits to zero, verifies all counters stay at zero, and confirms primary lyric text still submits. It asserts a positive finite CPU-side elapsed metric, opens the new panel through the actual Motion button, captures the HUD/panel and checks screen bounds at GUI scales 1–4. Existing lyric/editor/resource-reload/world-reconnect tests remain active. The Windows media fixture also verifies the slower idle-polling command while retaining transport and audio capabilities.

The performance counter is high-resolution elapsed time within Nocturne's submission/preparation, custom-geometry callbacks and two HUD hooks. It is neither GPU time nor hardware thread CPU utilization; it excludes the engine's later text batching and network workers. Custom vertex totals exclude engine-generated font glyph vertices; text and projected submissions are reported separately. Metrics describe the previous completed frame. There is no universal FPS improvement claim.

Artwork bounds: 24 LRU entries and 4 MiB compressed payload, 24-hour TTL, 256 KiB catalog response, 4 MiB image response, 2048² decoded source and 128² stored/GPU thumbnail. Request completion remains covered by the HTTP timeout. Interrupted lookup/search tasks preserve their interrupt status, and generation checks prevent stale publication. No persistent artwork cache or multi-cover GPU cache is introduced.

Local 0.11 vanilla and Fabulously Optimized + Complementary Reimagined r5.9.3 + Stratum 128x client runs passed, with shader/resource activation asserted. Windows packaging and four pinned renderer profiles are repeated in CI on the release commit. Physical minimize/restore power consumption, macOS/Linux media polling and live Spotify account/device timing were not hardware-certified.

## Gameplay/accessibility upgrade (0.12)

150 JUnit cases: 149 pass offline; one live-catalog test remains opt-in. Eighteen new cases cover context priority, independent screen rules, exact-class validation and defensive sets, non-finite/behind-camera fallback, edge hysteresis and safe bounds, context schema exclusions, nested render overrides, failure rollback and immutable profile documents.

Native Minecraft checks open chat and inventory and activate F3, assert lyric geometry is suppressed, then move world lyrics off-screen and back to verify fallback recovery. They exercise contrast controls, preview/save a Combat draft, simulate a local combat event, check screenshot-context priority, and deliberately request effects in a saved profile while friendly mode is on. The lock must still suppress particles/glow/cloud layers and preserve primary lyric submissions. All accessibility tabs and the profile editor are included in GUI-scale 1–4, small-window, resource-reload and reconnect checks.

Context overrides run synchronously within render callbacks and restore values in reverse order, including nested scopes and failed loads. They do not save preferences; profile libraries retain validated JSON separately. Context selection uses local client combat events, screen state and an explicit screenshot toggle. It does not classify servers, send gameplay packets or automatically intercept F2. User-supplied modded screen rules require exact runtime Java class names; class-name remapping can require editing a rule on another version.

The friendly preset covers Nocturne effects only. Minecraft weather, damage flashes, shader effects and other mods are outside its control. It is not a medical safety certification. Outlines add four text submissions per word; each world backplate adds one quad. Screen fallback tracks the current world anchor, not occlusion behind walls or every word's exact visible bounds. It does not guarantee avoidance of another mod's HUD.

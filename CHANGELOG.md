# Changelog

## 1.0.1 - Reliability and polish

- Validate automatic lyric matches, preserve meaningful metadata, rank manual alternatives and preview before saving a persistent song selection.
- Route recovery to search, retry, timing, resynchronization or placement repair; respect intentional screen hiding.
- Compare playback samples at a shared instant, accept small corrections and reject stale session samples.
- Preserve slow editor movement and gesture undo/redo/save; use calmer defaults for new installations.
- Bound lyric responses, honor provider cooldowns, clip long previews and add native crowded/soak performance gates.
- Target: Windows, Minecraft 26.1.2, Fabric, Java 25, client only. [Release notes](docs/releases/1.0.1.md).


## 1.0.0 — Official Release Version 1

Windows / Minecraft 26.1.2 / Fabric / client only. This is a Release build, not a beta.

- Add a first-run setup wizard with live desktop-player, lyric and visibility checks; reopen it from Help & setup.
- Add an optional Spotify music-library browser: track search, playlist paging, track playback, queue insertion and Spotify links. Existing connections may need reconnection for playlist permissions. Spotify access, Premium and active-device restrictions still apply.
- Add a lyrics-free visualizer with spectrum, signed waveform and bass/mid/treble views. It can appear between lyric lines or stay enabled throughout playback.
- Replace the all-desktop peak meter with Windows process-tree PCM capture for the selected supported player. Minecraft and unrelated applications do not drive effects. Browser capture includes that browser's audio tabs; cloud playback must be local. Unsupported or ambiguous processes report an unavailable state instead of capturing all output.
- Analyze real PCM with a Hann-windowed 2048-point FFT, independent frequency-band response controls and optional detected-beat pulses. Tempo appears only after sufficiently consistent onsets; uncertain tempo remains unlabelled.
- Add terrain raycasts at a bounded rate and readable screen-edge captions when a lyric anchor is behind scenery.
- Move HUD elements away from the hotbar, subtitle area and scoreboard. Add eight user-defined overlay exclusion zones and a bounded registration API for other mods.
- Add a controller on-screen keyboard for Studio text fields, with letters, numbers, symbols, case, spaces, backspace and cancel/apply.
- Persist compressed album thumbnails across game launches with a seven-day expiry and limits of 64 entries / 8 MiB. Keep bounded memory caching and cancel obsolete artwork requests.
- Add a cache manager with song labels, sizes, dates, paging, selection and confirmed removal. Local lyric edits, profiles and account files are preserved.
- Add in-game step-by-step guides linked to the timing, theme and world-placement editors.
- Suspend capture and spectrum work while paused, minimized, unfocused or without matching local music; honor reduced-motion and photosensitivity settings.

The new application capture requires a Windows build supporting process loopback (20348 or later; Windows 11 supported). General player detection does not require Spotify login. Existing historical beta downloads for other Minecraft targets remain available; this release artifact targets 26.1.2 exactly.

## 0.14.0-beta.1

- Add exact-target Fabric builds for Minecraft 26.1, 26.1.1, 26.2 and 26.3 alongside 26.1.2, with compile-time client/rendering adapters.
- Adapt 26.3 keyboard/controller input to SDL and its updated weather rendering hooks.
- Match update notifications and diagnostics to the running Minecraft version.
- Add a screenshot-led GitHub Pages showcase and version/release download selector backed by audited artifacts.
- Deduplicate website assets, remove the unused greeting file and reorganize the repository guide.
- Run native client and repeat-build regression checks separately for each modern target.


## 0.13.0-beta.1

- Add optional Mod Menu config integration without bundling it or requiring it at runtime.
- Add opt-in, bounded asynchronous update checks with semantic-version/channel/Minecraft filtering, toast/local chat notifications and trusted changelog links.
- Replace shared diagnostics with a typed privacy allowlist; add preview, clipboard and local JSON export.
- Gate warmed demo workloads in every renderer CI job and upload CPU percentiles/submission counts.
- Add explicit version support, compatibility release manifests, deterministic archives, twice-clean-build verification, checksums and Windows/Linux byte comparisons.

## 0.12.0-beta.1

- Add render-scoped Exploration, Combat, Menus and Screenshots profiles with searchable drafts, preview, undo/redo and explicit save/enable. Preserve global/per-song settings.
- Add independent chat, inventory, F3, pause, other-screen and F1 hiding, plus persistent exact-class rules for modded screens. Suppress related lyric effects while hidden.
- Add a safe-margin screen-edge lyric caption with behind-camera handling and recovery hysteresis.
- Add world/projected backplates, adjustable opacity, four-sided outlines and dark/light contrast presets, including secondary HUD text.
- Separate flashes, camera/FOV movement, particles and animated text. Add steady whole-line rendering and a global photosensitivity-friendly lock that profiles cannot bypass.
- Add an optional screenshot-context binding without replacing native F2 capture.
- Test context priority, override restoration, screen policies, edge placement and actual native UI/render flows.

## 0.11.0-beta.1

- Add smoothed adaptive effect budgets with fast reductions, gradual recovery and a configurable frame-time target.
- Add independent particle, glow-pass, cloud-lettering and lingering-line limits; scale distant decoration while preserving core lyrics.
- Reduce decoration, environment sampling, media polling and audio metering while paused, backgrounded or without music; skip minimized lyric submissions.
- Add a live performance/budget screen and movable Diagnostics HUD readout for CPU-side elapsed submission time and geometry counters.
- Cache up to 24 compressed covers / 4 MiB for 24 hours, retaining one GPU texture. Cancel superseded catalog/image downloads and obsolete lyric/search work.
- Test controller hysteresis, hard budgets, cache eviction/expiry, real HTTP cancellation and native UI/rendering. Keep new Studio controls reachable at small GUI scales.

## 0.10.0-beta.1

- Add nine configurable, initially unbound gameplay playback shortcuts in native Minecraft Controls.
- Add explicit playing/priority/sticky/ID desktop selection rules, editable priorities and strict app pins.
- Display bounded album-art textures in Studio and the karaoke HUD; preview and apply artwork-derived theme colors.
- Add advertised Windows shuffle/repeat controls and optional Spotify device volume, queue, shuffle and repeat.
- Add opt-in Spotify PKCE authorization with loopback/state validation, refresh tokens, private encrypted storage, backoff and disconnect cleanup; desktop detection remains default.
- Add four integration pages with capability gating and explicit desktop/account routing.
- Test selection, artwork limits, PKCE persistence, mocked API errors and real isolated Windows shuffle/repeat. Live Spotify account and macOS/Linux controls remain unverified.

## 0.9.0-beta.1

- Add non-destructive live theme previews, explicit Apply/Cancel, draft history and applied-theme undo/redo.
- Add HSVA picker, saturation/value canvas, opacity checkerboard, validated RGB/ARGB hex input and twelve persistent recent colors.
- Add searchable categorized settings with native controls, range descriptions and focus/hover tooltips.
- Add preset browsers with schematic/theme/HUD previews, favorites, validated clipboard import/export and reset previews.
- Add draggable HUD layouts with grid/neighbor snapping, alignment guides, safe margins, scale/visibility and separate gameplay/menu drafts.
- Add placement drag/reset/wheel history, keyboard nudging and native gamepad navigation through the bundled GLFW API.
- Render saved HUD placement/scale/visibility in game and menus; preserve global feature toggles and world/projected lyric placement.
- Test the new models and actual Minecraft UI flows, including controller action dispatch and GUI-scale bounds. No physical controller certification is claimed.


## 0.8.0-beta.1

- Add source word karaoke through safe Lyricsfile 1.0 parsing, Enhanced LRC and provider YRC/KLYRIC. Keep source, mixed, estimated and manually corrected timing distinguishable.
- Add lyric workspace with history, line/word bounds, draggable boundary timeline, playhead actions, 40-step undo/redo, persistent local documents and Enhanced LRC export.
- Retain optional provider translation/romanization; import and export synchronized language sidecars and toggle secondary HUD lines independently.
- Add automatic per-song visual profiles that preserve global settings across saves and track changes.
- Add bidirectional shaping/visual word order, Unicode font choice, known-gap vocal countdowns and confidence labels.
- Preserve word ends, whitespace, language layers and provenance in local/cache data; support LRC offsets and repeated Enhanced LRC timestamps. Validate bounded YAML with SnakeYAML 2.7, with legacy fallback for unsupported documents.


## 0.7.0-beta.1

- Diagnose lookup, clock drift, disabled lyrics, missing players and hidden active lines separately; offer the matching repair action.
- Persist synchronized/estimated lyrics and word timings across launches, with configurable 30-day expiry, manual refresh, corruption quarantine and bounded storage.
- Normalize remasters, featured artists and browser titles; preserve performance-version intent and avoid ambiguous artist matches.
- Resynchronize clocks and clear stale effects after seeks, pause/resume, ads, session loss and Windows output-device changes. Cancel stale lookup completions after refresh or track changes.
- Add per-session Windows transport capabilities and device identity; show explicit unknown capability details on other platforms.
- Add schema migration, unknown-setting preservation, rolling backups, corrupt-file recovery and read-only protection for future config versions. Recover damaged preset libraries safely.
- Extend GUI-scale tests, add the optimized CI profile, and support isolated external modpack/shader/resource fixtures. Validate Adrenaline and Fabulously Optimized with real shader and resource packs.

## 0.6.0-beta.1

- Theme-native buttons and sliders across Studio, classic settings and the placement editor, with smooth hover, keyboard focus and native narration.
- Compact Player page with visible retry/recovery controls; Midnight, Lagoon and Ember theme shortcuts.
- Reject invalid Windows timeline timestamps without breaking sparse valid updates; refresh lookups when duration arrives late.
- Retry failed lookups instead of permanently caching misses, bound each lookup to 18 seconds, prefer available plain lyrics before another provider, and bound successful lyric cache entries to 128.
- Default new installations to Follow view; add persistent visibility recovery that resets transforms and selects the compatible renderer.
- Reduce idle helper telemetry from 20 to 4 updates/second; keep 20 Hz optional audio metering. Cache cloud-circle trigonometry, sample environment attributes at 10 Hz, skip effects without lyrics, cache Glass rows and lower Balanced particle cap to 48.
- Remove upstream names from runtime metadata and ordinary product branding; retain copyright and font license notices in legal files.
- Add lyric lookup fixtures, live-provider opt-in validation, timestamp regressions, theme shortcut and visibility-recovery client checks.

## 0.5.0-beta.1

- Add Studio with Player, Lyrics, Apps, Theme, Placement, Motion and Health tabs; scrollable small-window layout and keyboard-accessible native widgets.
- Restore desktop play/pause, next/previous, relative seek and drag-to-seek with capability checks and command feedback.
- Add persistent player pinning, detected-app selection, automatic reconnect and helper shutdown on parent exit.
- Add alternative synchronized lyric matches, local/enhanced LRC imports, per-song offsets and saved corrections.
- Add theme colours/opacity/font/spacing, named presets, validated clipboard sharing, and per-placement transform profiles.
- Add an in-world move/rotate/resize editor and frame-independent line/transform transitions.
- Add reduced motion, independent flash/shake switches, high contrast, effect quality, particle limits, distance/behind-camera culling and a lightweight preset.
- Add optional real Windows output-level reaction, with sensitivity and availability feedback; all desktop sound contributes and audio is never recorded.
- Separate playback polling from lyric/artwork network work; protect local corrections from stale lookup completion.
- Add unit, Windows media-session and expanded Minecraft client tests; vanilla/Sodium/Iris CI matrix covers resizing, resource reload and reconnect.

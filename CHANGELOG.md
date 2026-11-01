# Changelog

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

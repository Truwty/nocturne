# Changelog

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

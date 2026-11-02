# 1.0.1 reliability checks

Target: Windows, Minecraft 26.1.2, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2, Java 25. Standard renderer; 1280 x 958 test window. These are checks of the current target, not claims about other Minecraft versions or desktop platforms.

## Regression coverage

- 206 JUnit tests passed with the live-provider test enabled. Matching regressions cover wrong exact-endpoint artist/duration/version, remasters, features, browser inference, meaningful parentheses, ordinary words such as Live Forever, artist punctuation, ambiguous/missing artists and malformed manual results.
- Playback checks cover shared sample timestamps, seek/pause/resume/device transitions, clamped clocks, stale session samples, advertisement return, persisted manual lyrics and a stopped poll attempting to overwrite playback.
- Native client checks passed for a real public-provider lookup reaching visible lyric submissions, all Studio pages, lyric preview/cancel, accumulated small mouse drags, keyboard nudges, grouped undo/redo/save, themes, HUD layouts, accessibility, recovery and capability-gated playback pages.
- The screen-edge test isolates projection from terrain obstruction. A separate terrain-obstruction test checks a deliberately placed block.
- The Windows media fixture passed selection, pause/resume, seeking, next/previous, shuffle/repeat, priority routing, idle polling, strict pinning, reconnect and parent-exit shutdown using its own muted player.
- Download catalog checks: 10 Python tests and 4 Node tests passed.

## CPU rendering measurements

These are Nocturne CPU submission times sampled once per client tick, not game frame times, FPS or GPU measurements. The decorated fixture keeps the same controlled lyric line; it does not saturate every possible visual effect.

| Scenario | Samples | Median ms | p95 ms | Max particles |
| --- | ---: | ---: | ---: | ---: |
| steady-v2 | 60 | 0.1442 | 0.1981 | 0 |
| decorated-v2 | 60 | 0.1016 | 0.1468 | 1 |
| crowded-v1 | 60 | 0.1059 | 0.1801 | 0 |
| soak-v1 | 500 | 0.0859 | 0.1520 | 1 |

Crowded adds 48 armor stands around the player in the native world. Soak collects 500 tick samples, approximately 25 seconds, in that populated world. Effect and geometry caps remain checked by the regression budget.

The unchanged baseline steady scene measured 0.1177 ms median / 0.1654 ms p95. Differences between runs include normal host load and changed defaults; these numbers do not establish a renderer speedup. The test world also emitted server-tick hitch warnings, so these CPU figures do not rule out other sources of stutter. No broad renderer rewrite was made on the strength of a sub-millisecond baseline. The patch reduces default decoration and clips long preview documents to visible rows.

[Raw baseline and release measurements](performance-1.0.1.json).

## Reproduce

Use the pinned Java 25 toolchain and Gradle wrapper. Run `./gradlew.bat test` for offline regressions. Set `NOCTURNE_LIVE_LYRICS=true` to enable the known-track public-provider check, then run `./gradlew.bat test runClientGameTest -PdisablePacketSync=true -PclientTestProcessors=8 --console=plain`. Native checks write screenshots and `nocturne-performance.json` below `build/run/clientGameTest`. The Windows media fixture is `scripts/windows_media_smoke.py`; its dependencies are pinned in `scripts/media-test-requirements.txt`.

Release archives are compared with two clean, uncached builds using `scripts/verify_release.py`. Published release assets include checksums and reproducibility evidence.

## Limits

This is a short synthetic soak, not an hours-long session or a crowded multiplayer/server benchmark. Actual Spotify-account playback, every desktop app, real shader packs and total GPU frame time were not revalidated in this patch. Provider outages and missing/inaccurate timestamps remain possible; manual search, import and calibration remain available. No Hypixel compatibility or server-policy guarantee is implied.

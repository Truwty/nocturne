# Minecraft support and release builds

## Supported versions

| Component | Supported / pinned build |
| --- | --- |
| Minecraft Java | **26.1.2 only** |
| Mod environment | Fabric client only; no server installation |
| Java runtime | Java 25 or newer; release compiler is Temurin **25.0.5+8** |
| Fabric Loader | **0.20.1+**; CI uses 0.20.1 |
| Fabric API | **0.155.3+26.1.2**; match the Minecraft version |
| Optional Mod Menu | **18.0.2** is tested; configuration opens Nocturne Studio |
| Gradle / Loom | Wrapper **9.8.2** / Fabric Loom **1.19.3** |

1.21.11, other 1.21 versions, 26.1, 26.1.1, 26.2+, snapshots, Forge and NeoForge are not supported by this artifact. A version appearing newer does not establish binary compatibility. The loader metadata deliberately requires exact Minecraft 26.1.2. Keep only one Nocturne JAR installed. Server-specific rules still apply; no Hypixel certification is claimed.

Renderer CI covers vanilla, Sodium 0.9.2, Iris 1.11.4 and Sodium/Lithium 0.24.7/FerriteCore 9.0.0. Vanilla CI also installs Mod Menu and exercises the real registered config factory; other matrix jobs verify it remains optional. Shader/modpack limits and hardware checks are recorded in [VALIDATION.md](VALIDATION.md).

## Updates and diagnostics

Open **N → Health → Releases & diagnostics**. Automatic update checks start **off**. Enable them for one asynchronous GitHub check on startup and at most every 24 hours while running. **Check now** works without enabling automatic checks, with a one-minute cooldown. Preview releases require a separate opt-in. Preferences changes cancel pending requests and invalidate stale results. A request has a 12-second deadline, a 256 KiB body limit and no redirects. Errors and rate limits remain visible without blocking rendering or repeatedly retrying.

The request uses the public GitHub releases endpoint without authentication. GitHub receives the connection IP and a User-Agent containing the Nocturne version; no song, server, account or configuration payload is sent. Only the latest 20 published releases are considered. A newer semantic version must include both its matching mod JAR and `nocturne-mc26.1.2.json` compatibility marker. The changelog link is constructed for this repository rather than trusting arbitrary API URLs. Available updates produce a toast, a local clickable chat link when a world is open, and a changelog/download button in the maintenance screen. No installer or automatic download runs.

**Copy diagnostics**, the legacy **Copy debug** action and **Export filtered diagnostics** all use the same typed allowlist. The maintenance screen previews the shared JSON. Exports are new files under `config/nocturne/diagnostics`; they are never uploaded. Reports contain Nocturne/Minecraft/Java versions, normalized OS family, GUI dimensions, playback/lyric availability booleans, player count, renderer/recovery categories and numeric performance counters. They exclude song/artist/lyrics, app names/identifiers, account identifiers, device names, usernames, server addresses, paths, credentials, tokens, arbitrary settings and raw log/error text. Review exported technical facts before sharing; retain or remove local exports as needed.

## Reproducible releases

Use a clean checkout of the intended tag, Git, Python 3.10+ and **Temurin JDK 25.0.5+8**. Set `JAVA_HOME` and put that JDK's `bin` directory on PATH. The checked-in Gradle wrapper verifies its distribution SHA-256. Dependency versions, compiler release, archive order/timestamps and checkout line endings are pinned. Mod Menu is compile-only and never bundled.

```powershell
# Windows: resets generated build/ output during each clean build.
python scripts/verify_release.py
```

```bash
# Linux/macOS: same release inputs; resets generated build/ output.
python3 scripts/verify_release.py
```

The script performs **two clean builds** with task/build-cache reuse disabled, runs the offline tests twice, and fails unless the mod JAR, sources JAR and support manifest are byte-identical. `build/release/SHA256SUMS` and `reproducibility.json` record digests, source commit, worktree status and the comparison count. A dirty checkout can be checked during development; publish only evidence with `dirty: false` from the release commit. This proves repeatability for the recorded inputs, not independence from unavailable or changed upstream repositories.

CI repeats the two Linux builds and compares their release bytes with the separate Windows build. Review all renderer/performance checks before publishing. Release assets are the mod JAR, sources JAR, compatibility manifest, checksum list and reproducibility evidence. The compatibility marker is required for future in-game update notifications. Never upload local configs, authentication stores or test fixtures.

## Automated performance checks

Every renderer job runs `steady-v1` and `decorated-v1` demo workloads after warm-up, collecting 60 distinct completed-frame samples each. Source-controlled [budgets](src/gametest/resources/performance-budgets.json) require core lyric submissions and bound text, custom vertices, particles, glow, cloud layers and lingering lines. The steady workload permits no decorative submissions. Increasing work beyond a limit fails CI, as do missing/invalid samples. JUnit verifies the checks actually reject CPU, geometry and missing-text regressions.

Each run uploads `nocturne-performance.json` with CPU-side elapsed submission p50/p95 and maximum counts. The 50 ms p95 ceiling is a coarse runaway-work guard for shared CI machines, not a tight latency target; work-count limits provide the deterministic regression gate. Compare reports across commits for smaller timing changes. Measurements exclude GPU execution and Minecraft's later text batching and are not FPS benchmarks. These two short demo scenarios do not exhaust every effect, custom document, cloud placement or modpack workload; extend/version the fixtures when those workloads change.

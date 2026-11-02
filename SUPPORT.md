# Minecraft support and release builds

## Supported versions

| Component | Supported / pinned build |
| --- | --- |
| Minecraft Java | **26.1, 26.1.1, 26.1.2, 26.2 and 26.3**, using a separate exact-version JAR |
| Mod environment | Fabric client only; no server installation |
| Java runtime | Java 25 or newer; release compiler is Temurin **25.0.5+8** |
| Fabric Loader | **0.20.1+**; CI uses 0.20.1 |
| Fabric API | 26.1.x: **0.155.3+26.1.2**; 26.2: **0.161.0+26.2**; 26.3: **0.165.0+26.3** |
| Optional Mod Menu | **18.0.2** is tested on 26.1.2; configuration opens Nocturne Studio |
| Gradle / Loom | Wrapper **9.8.2** / Fabric Loom **1.19.3** |

Download a build for the **exact** Minecraft version from the [download selector](https://truwty.github.io/nocturne/downloads.html). The unclassified JAR targets 26.1.2; other targets have `-mc<version>` in the filename. Loader metadata deliberately rejects different versions. Keep only one Nocturne JAR installed. Server-specific rules still apply; no Hypixel certification is claimed.

The development branch adds Fabric adapters for Minecraft 1.21.10/1.21.11 and a NeoForge adapter for 1.21.11. Native checks and release reproducibility are tracked in [compatibility/README.md](compatibility/README.md). They are not published downloads yet. Earlier versions, snapshots and Forge remain unported. Wider compatibility requires separate adapters, rather than relaxed metadata.

Renderer CI covers vanilla, Sodium 0.9.2, Iris 1.11.4 and Sodium/Lithium 0.24.7/FerriteCore 9.0.0. Vanilla CI also installs Mod Menu and exercises the real registered config factory; other matrix jobs verify it remains optional. Shader/modpack limits and hardware checks are recorded in [VALIDATION.md](VALIDATION.md).

## Updates and diagnostics

Open **N → Health → Releases & diagnostics**. Automatic update checks start **off**. Enable them for one asynchronous GitHub check on startup and at most every 24 hours while running. **Check now** works without enabling automatic checks, with a one-minute cooldown. Preview releases require a separate opt-in. Preferences changes cancel pending requests and invalidate stale results. A request has a 12-second deadline, a 256 KiB body limit and no redirects. Errors and rate limits remain visible without blocking rendering or repeatedly retrying.

The request uses the public GitHub releases endpoint without authentication. GitHub receives the connection IP and a User-Agent containing the Nocturne version; no song, server, account or configuration payload is sent. Only the latest 20 published releases are considered. A newer semantic version must include both its exact-target mod JAR and `nocturne-mc<running-version>.json` compatibility marker. The changelog link is constructed for this repository rather than trusting arbitrary API URLs. Available updates produce a toast, a local clickable chat link when a world is open, and a changelog/download button in the maintenance screen. No installer or automatic download runs.

**Copy diagnostics**, the legacy **Copy debug** action and **Export filtered diagnostics** all use the same typed allowlist. The maintenance screen previews the shared JSON. Exports are new files under `config/nocturne/diagnostics`; they are never uploaded. Reports contain Nocturne/Minecraft/Java versions, normalized OS family, GUI dimensions, playback/lyric availability booleans, player count, renderer/recovery categories and numeric performance counters. They exclude song/artist/lyrics, app names/identifiers, account identifiers, device names, usernames, server addresses, paths, credentials, tokens, arbitrary settings and raw log/error text. Review exported technical facts before sharing; retain or remove local exports as needed.

## Reproducible releases

Use a clean checkout of the intended tag, Git, Python 3.10+ and **Temurin JDK 25.0.5+8**. Set `JAVA_HOME` and put that JDK's `bin` directory on PATH. The checked-in Gradle wrapper verifies its distribution SHA-256. Dependency versions, compiler release, archive order/timestamps and checkout line endings are pinned. Mod Menu is compile-only and never bundled.

```powershell
# Windows: resets generated build/ output during each clean build.
python scripts/verify_release.py --minecraft 26.1.2
```

```bash
# Linux/macOS: same release inputs; resets generated build/ output.
python3 scripts/verify_release.py --minecraft 26.1.2
```

Select `--minecraft 26.1`, `26.1.1`, `26.1.2`, `26.2` or `26.3`. Non-default output is isolated under `build/minecraft/<version>/`; the default remains `build/`. Each JAR is compiled against that target’s actual Minecraft and Fabric APIs. The 26.3 adapter uses SDL gamepad input and its current weather/rendering hooks; earlier targets use GLFW. Controller hardware still needs manual testing.

For development 1.21.11 ports, install Temurin 21.0.13+9 alongside the Java 25 build launcher and set `NOCTURNE_JAVA21` to its JDK directory. Run `python scripts/verify_release.py --minecraft 1.21.11` for Fabric or append `--loader neoforge` for NeoForge. NeoForge output is isolated under `build/neoforge/1.21.11`. Its development-only smoke mod is enabled with `-Psmoke=true` and requires `NOCTURNE_SMOKE_WORLD` pointing to a disposable vanilla 1.21.11 world; it is excluded from release JARs.

The script performs **two clean builds** with task/build-cache reuse disabled, runs the offline tests twice, and fails unless the mod JAR, sources JAR and support manifest are byte-identical. `build/release/SHA256SUMS` and `reproducibility.json` record digests, source commit, worktree status and the comparison count. A dirty checkout can be checked during development; publish only evidence with `dirty: false` from the release commit. This proves repeatability for the recorded inputs, not independence from unavailable or changed upstream repositories.

CI repeats the two Linux builds for every target and runs the native Minecraft client, lyric rendering, Studio flows, accessibility, reload/reconnect and performance checks for each. The 26.1.2 target additionally compares Linux release bytes with a separate Windows build and runs the renderer compatibility matrix. Other targets do not inherit those shader/modpack claims. Review all renderer/performance checks before publishing. Release assets are the mod JAR, sources JAR, compatibility manifest, checksum list and reproducibility evidence. The compatibility marker is required for future in-game update notifications. Never upload local configs, authentication stores or test fixtures.

## Automated performance checks

Every renderer job runs `steady-v2` and `decorated-v2` continuous-line demo workloads after warm-up (v2 removes intentional demo lyric gaps from the measurement window), collecting 60 distinct completed-frame samples each. Source-controlled [budgets](src/gametest/resources/performance-budgets.json) require core lyric submissions and bound text, custom vertices, particles, glow, cloud layers and lingering lines. The steady workload permits no decorative submissions. Increasing work beyond a limit fails CI, as do missing/invalid samples. JUnit verifies the checks actually reject CPU, geometry and missing-text regressions.

Each run uploads `nocturne-performance.json` with CPU-side elapsed submission p50/p95 and maximum counts. The 50 ms p95 ceiling is a coarse runaway-work guard for shared CI machines, not a tight latency target; work-count limits provide the deterministic regression gate. Compare reports across commits for smaller timing changes. Measurements exclude GPU execution and Minecraft's later text batching and are not FPS benchmarks. These two short demo scenarios do not exhaust every effect, custom document, cloud placement or modpack workload; extend/version the fixtures when those workloads change.

## Website development

The static GitHub Pages site is in `site/`. Screenshots, fonts and licensing are copied from their canonical repository locations instead of storing duplicates. The catalog only lists audited historical JARs or published releases with validated exact-target support manifests.

```powershell
python scripts/build_site_assets.py
python -m unittest discover -s scripts/tests -v
python scripts/build_site_catalog.py
python -m http.server 8766 --bind 127.0.0.1 --directory site
```

The Pages workflow rebuilds on site changes and published/edited releases. A missing or invalid catalog produces an explicit GitHub Releases fallback instead of inventing compatible downloads.

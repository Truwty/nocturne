# Port readiness

Published support is defined by exact-target release assets and manifests, not a broad Minecraft metadata range. The website filters Minecraft version and loader independently and rejects mismatched filenames/manifests.

| Minecraft | Loader | State |
| --- | --- | --- |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Fabric | Published in 0.14.0-beta.1; native client validation |
| 1.21.10, 1.21.11 | Fabric | Development: build, unit suite, Windows/Linux native suite and repeat-build checks passed; unpublished |
| 1.21.11 | NeoForge | Development: build, unit suite and native demo/UI smoke passed locally; CI/reproducibility pending |
| Earlier Fabric versions | Fabric | Not ported |
| 1.8.9 onward | Forge | Not ported |
| Other Minecraft versions | NeoForge | Not ported |

## Adapter boundaries

Shared client behavior lives in `src/client/java`. Loader bootstrap, lifecycle events, HUD registration, key registration, paths and mod discovery live in `src/loader/<loader>`. Version adapters provide Minecraft input, weather and rendering access. The shared world-render mixin submits lyric geometry through vanilla's entity submission phase.

Minecraft 1.21.10/1.21.11 use official Mojang mappings and explicit reviewed source-renaming maps. Generated sources compile against the actual target dependencies; they are not reflection fallbacks. These Fabric targets use the remapping Loom plugin. NeoForge uses ModDevGradle and its own metadata. Both use Java 21; modern 26.x targets use Java 25. The build launcher requires Java 25, with Java 21 supplied through `NOCTURNE_JAVA21`.

Linux CI for the remapped Fabric targets opts out of the test API's packet synchronizer using `-PdisablePacketSync=true`. Thread dumps showed test-world startup/reconnect stalls at its phase barrier; the opt-out completed both targets' suites. This affects the development test harness only. Worlds still run a local integrated server, and the rendering, UI, accessibility, performance and reconnect assertions remain enabled. Local Windows suites also pass with the synchronizer's default behavior. A test-only watchdog captures thread stacks when world creation takes more than 45 seconds.

## Before publishing a port

1. Build against the exact game and loader APIs, with client-only entry points and metadata.
2. Run the offline unit suite and a native client render/UI smoke test. Keep fixtures and smoke mods out of release artifacts.
3. Compare two clean builds, audit bundled dependencies and archive contents, and publish exact-target manifests and checksums.
4. Record renderer coverage for that target. Other versions do not inherit shader, resource-pack or modpack claims.
5. Update the support table only after evidence passes. The download catalog includes published artifacts, never planned ports.

Public release-candidate testing and real music-service/OS integration validation are outside the currently authorized work. Native smoke tests use local demo lyrics. Hardware controller and physical low-end GPU coverage remain separate from software-rendered CI.

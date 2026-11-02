# Port readiness

Published support is defined by exact-target release assets and manifests, not a broad Minecraft metadata range. The website filters Minecraft version and loader independently and rejects mismatched filenames/manifests.

| Minecraft | Loader | State |
| --- | --- | --- |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | Fabric | Published in 0.14.0-beta.1; native client validation |
| 1.21.11 | Fabric | Development: build, unit suite and native client suite passed locally; CI/reproducibility pending |
| 1.21.11 | NeoForge | Development: build, unit suite and native demo/UI smoke passed locally; CI/reproducibility pending |
| Earlier Fabric versions | Fabric | Not ported |
| 1.8.9 onward | Forge | Not ported |
| Other Minecraft versions | NeoForge | Not ported |

## Adapter boundaries

Shared client behavior lives in `src/client/java`. Loader bootstrap, lifecycle events, HUD registration, key registration, paths and mod discovery live in `src/loader/<loader>`. Version adapters provide Minecraft input, weather and rendering access. The shared world-render mixin submits lyric geometry through vanilla's entity submission phase.

Minecraft 1.21.11 uses official Mojang mappings and an explicit reviewed source-renaming map. Generated sources compile against the actual target dependencies; they are not reflection fallbacks. Fabric 1.21.11 uses the remapping Loom plugin. NeoForge uses ModDevGradle and its own metadata. Both use Java 21; modern 26.x targets use Java 25. The build launcher requires Java 25, with Java 21 supplied through `NOCTURNE_JAVA21`.

## Before publishing a port

1. Build against the exact game and loader APIs, with client-only entry points and metadata.
2. Run the offline unit suite and a native client render/UI smoke test. Keep fixtures and smoke mods out of release artifacts.
3. Compare two clean builds, audit bundled dependencies and archive contents, and publish exact-target manifests and checksums.
4. Record renderer coverage for that target. Other versions do not inherit shader, resource-pack or modpack claims.
5. Update the support table only after evidence passes. The download catalog includes published artifacts, never planned ports.

Public release-candidate testing and real music-service/OS integration validation are outside the currently authorized work. Native smoke tests use local demo lyrics. Hardware controller and physical low-end GPU coverage remain separate from software-rendered CI.

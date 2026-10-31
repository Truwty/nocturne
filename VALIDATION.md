# Validation — Nocturne 0.4.0-beta.1

Locally checked on Windows with Java 25, Minecraft 26.1.2, Fabric Loader 0.20.1 and Fabric API 0.155.3+26.1.2.

- `gradlew.bat clean build`: passed; eight JUnit tests cover lyric timestamp boundaries, enhanced word timestamps, chunking, empty lyrics, Spotify media-session identification, Unicode, paused timing, stale timeline bounds and missing sessions.
- `gradlew.bat runClientGameTest`: passed; creates an isolated world, submits lyric geometry, executes the rain mixin, exercises all seven placement modes/four fonts, validates shader-compatible HUD drawing, and renders Glass/vanilla settings pages.
- Client game test with `NOCTURNE_MEDIA_SMOKE=true`: the actual Java detection service found a live desktop media session without OAuth.
- Windows PowerShell helper, using the same stdin launch protocol as the mod: detected an actual original, muted Windows media test session without a login or credentials.
- Screenshots in `docs/` were captured from the renamed mod running in Minecraft, using its original demo text. They are not design mockups.

Live Spotify desktop playback itself, macOS/Linux players, multiplayer/Hypixel, and external shader packs have not been end-to-end tested. Automatic detection depends on the player publishing media metadata. Live lyric coverage depends on external services. Shader-fallback testing does not prove compatibility with every shader pack.

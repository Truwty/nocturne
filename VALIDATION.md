# Validation: 0.1.0-beta.1

Tested on Windows 11 x64, JDK 25.0.5, Minecraft 26.1.2, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2, Python 3.14.3.

## Verified

- Java build against the actual Minecraft 26.1.2 client libraries.
- Five Java tests: time formatting, idle capability reset, partial snapshots, progress clamping, source naming.
- Five Python tests: protocol validation, accepted commands, missing selected app isolation, rejected command feedback, connected idle state.
- Frozen executable starts, reads real Windows sessions including Spotify metadata/artwork, and exits cleanly via the pipe protocol.
- Minecraft launches with the client initializer and title-screen mixin; Nocturne's cockpit renders in the running client.
- Live Spotify title, artist, album artwork, playback state, and progress are visible in Minecraft.
- Pause and seek succeed through the native Windows API against a separate, muted synthetic MediaPlayer session. This did not exercise controls against each third-party app.

## Not verified

- Full in-world HUD placement, hotkey behavior, and interaction with other HUD mods.
- Hypixel or other multiplayer servers; compatibility with the server's supported client versions and mod policy.
- End-to-end controls for Spotify, Cider, Apple Music, YouTube Music, TIDAL, or foobar2000 individually.
- Windows 10, ARM systems, different DPI settings, very small windows, resource packs, and screen readers.
- Long-duration sessions, suspend/resume, and reconnect after OS media service failure.
- Code signing and reputation-based antivirus behavior; the bundled executable is unsigned.

Computer Use inspection was stopped by the user with Escape before the remaining interactive checks. This release is deliberately marked **beta**. A successful compilation and a visually inspected cockpit do not establish perfect behavior or server approval.

The development launcher uses a dummy Fabric login, so its Realms/authentication warnings are expected and do not validate multiplayer login.

# Changelog

## 0.4.0-beta.1

- Replace the previous implementation with the permitted Kangetsu-derived Minecraft 26.1.2 port, preserving its world lyrics, effects, presets, fonts and settings interfaces.
- Rebrand mod identity, Java packages, resource paths, settings, key category, icon and menus as Nocturne; retain original attribution and license notices.
- Remove Spotify OAuth, developer Client ID setup, credential storage and the callback HTTP server. Automatically detect desktop music instead.
- Prefer playing Spotify on Windows, then another playing media session; retain paused-player fallback.
- Default to Glass settings; open with N or the title-screen Nocturne button.
- Add desktop rescan, detection diagnostics, bounded timestamps, reconnect fixes and current Fabric rendering/mixin integration.
- Update build/CI and add eight unit tests plus an automated Minecraft client test.

This replaces the previous player UI and transport controls. Historical 0.1/0.2 releases remain available under their existing tags and licenses.

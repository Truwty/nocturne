# Third-party notices

- Bundled fonts retain their separate SIL Open Font License 1.1 notices under `META-INF/licenses`.

- Fabric Loader and Fabric API are separately installed dependencies, not bundled.
- Gradle wrapper components retain their upstream Apache-2.0 licensing. Minecraft is not distributed with this project.

This release does not include the prior Nocturne Python/PyInstaller media bridge or Noto Sans font.

Version 0.5 uses Windows media-session and Core Audio APIs through locally compiled C# interface declarations. No audio library, Python runtime, Sodium or Iris JAR is bundled. PyWinRT dependencies listed under scripts/ are solely for the optional developer integration test. Sodium/Iris compatibility dependencies are separately downloaded for test runs.

SnakeYAML 2.7 is bundled as a nested JAR to parse LRCLIB Lyricsfile YAML using its safe constructor. Licensed under Apache License 2.0; the license is included at `META-INF/licenses/Apache-2.0-SnakeYAML.txt`. Source: https://codeberg.org/snakeyaml/snakeyaml . The built-in Unicode font is referenced from Minecraft resources and is not redistributed.

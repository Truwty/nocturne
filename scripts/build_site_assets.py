"""Copy canonical repository assets into the deployable static website."""
from pathlib import Path
from shutil import copyfile

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    destination = ROOT / "site/assets"
    destination.mkdir(parents=True, exist_ok=True)
    assets = {
        **{name: f"docs/{name}" for name in (
            "world.png", "compatibility-shaders.png", "lyric-workspace.png",
            "interface-picker.png", "placement-editor.png", "playback.png", "studio.png",
            "experience-orbit.png", "experience-replay.png",
        )},
        "icon.png": "src/main/resources/assets/nocturne/icon.png",
        "smooth.ttf": "src/main/resources/assets/nocturne/font/smooth.ttf",
        "smooth_bold.ttf": "src/main/resources/assets/nocturne/font/smooth_bold.ttf",
        "OFL-HankenGrotesk.txt": "src/main/resources/META-INF/licenses/OFL-HankenGrotesk.txt",
    }
    for name, source in assets.items():
        copyfile(ROOT / source, destination / name)


if __name__ == "__main__":
    main()

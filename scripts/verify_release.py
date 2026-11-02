"""Build twice without task/build-cache reuse and compare release bytes."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]

def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()

def build(minecraft: str, loader: str) -> dict[str, Path]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(wrapper), "clean", "build", "--no-build-cache", "--rerun-tasks", "--console=plain", f"-PminecraftVersion={minecraft}", f"-Ploader={loader}"], cwd=ROOT, check=True)
    version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("version="))
    directory = ROOT / "build" if minecraft == "26.1.2" else ROOT / "build/minecraft" / minecraft
    classifier = "" if minecraft == "26.1.2" else f"-mc{minecraft}"
    marker = f"nocturne-mc{minecraft}.json"
    if loader == "neoforge":
        directory = ROOT / "build/neoforge" / minecraft
        classifier = f"-neoforge-mc{minecraft}"
        marker = f"nocturne-neoforge-mc{minecraft}.json"
    paths = [directory / "libs" / f"nocturne-{version}{classifier}{suffix}.jar" for suffix in ("", "-sources")]
    paths.append(directory / "release" / marker)
    if not all(path.is_file() for path in paths):
        raise RuntimeError("Missing release artifact")
    return {path.name: path for path in paths}

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft", default="26.1.2", choices=("1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"))
    parser.add_argument("--loader", default="fabric", choices=("fabric", "neoforge"))
    args = parser.parse_args()
    minecraft, loader = args.minecraft, args.loader
    if loader == "neoforge" and minecraft != "1.21.11":
        parser.error("NeoForge currently has an adapter only for Minecraft 1.21.11")
    with tempfile.TemporaryDirectory(prefix="nocturne-repro-") as temporary:
        first = build(minecraft, loader)
        original = {name: digest(path) for name, path in first.items()}
        for name, path in first.items():
            shutil.copyfile(path, Path(temporary) / name)
        second = build(minecraft, loader)
        repeated = {name: digest(path) for name, path in second.items()}
        if original != repeated:
            raise RuntimeError(f"Reproducibility failed: {original} != {repeated}")
        destination = next(iter(second.values())).parent.parent / "release"
        (destination / "SHA256SUMS").write_text("".join(f"{sha}  {name}\n" for name, sha in sorted(repeated.items())), encoding="utf-8", newline="\n")
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT, text=True).strip())
        evidence = {"schema": 1, "minecraft": minecraft, "loader": loader, "commit": commit, "dirty": dirty, "buildsCompared": 2, "sha256": repeated}
        (destination / "reproducibility.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8", newline="\n")
        print(json.dumps(evidence, indent=2))

if __name__ == "__main__":
    main()

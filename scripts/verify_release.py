"""Build twice without task/build-cache reuse and compare release bytes."""
from __future__ import annotations
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

def build() -> dict[str, Path]:
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(wrapper), "clean", "build", "--no-build-cache", "--rerun-tasks", "--console=plain"], cwd=ROOT, check=True)
    version = next(line.split("=", 1)[1] for line in (ROOT / "gradle.properties").read_text().splitlines() if line.startswith("version="))
    paths = [ROOT / "build/libs" / f"nocturne-{version}{suffix}.jar" for suffix in ("", "-sources")]
    paths.append(ROOT / "build/release/nocturne-mc26.1.2.json")
    if not all(path.is_file() for path in paths):
        raise RuntimeError("Missing release artifact")
    return {path.name: path for path in paths}

def main() -> None:
    with tempfile.TemporaryDirectory(prefix="nocturne-repro-") as temporary:
        first = build()
        original = {name: digest(path) for name, path in first.items()}
        for name, path in first.items():
            shutil.copyfile(path, Path(temporary) / name)
        second = build()
        repeated = {name: digest(path) for name, path in second.items()}
        if original != repeated:
            raise RuntimeError(f"Reproducibility failed: {original} != {repeated}")
        destination = ROOT / "build/release"
        (destination / "SHA256SUMS").write_text("".join(f"{sha}  {name}\n" for name, sha in sorted(repeated.items())), encoding="utf-8", newline="\n")
        commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        dirty = bool(subprocess.check_output(["git", "status", "--porcelain", "--untracked-files=no"], cwd=ROOT, text=True).strip())
        evidence = {"schema": 1, "commit": commit, "dirty": dirty, "buildsCompared": 2, "sha256": repeated}
        (destination / "reproducibility.json").write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8", newline="\n")
        print(json.dumps(evidence, indent=2))

if __name__ == "__main__":
    main()

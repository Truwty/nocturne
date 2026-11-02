"""Compare a downloaded build artifact with this checkout's rebuilt release."""
from __future__ import annotations
import argparse
import hashlib
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("reference", type=Path)
parser.add_argument("--local-directory", type=Path, help="Downloaded artifact containing one exact-target release")
args = parser.parse_args()
reference = args.reference.resolve()
if args.local_directory:
    manifests = list(args.local_directory.rglob("nocturne-*mc*.json"))
    if len(manifests) != 1:
        raise RuntimeError("Expected one local support manifest")
    manifest = manifests[0]
else:
    manifest = root / "build/release/nocturne-mc26.1.2.json"
local = list((manifest.parent.parent / "libs").glob("*.jar")) + [manifest]
if len(local) != 3 or not all(path.is_file() for path in local):
    raise RuntimeError("Expected a mod JAR, sources JAR and support manifest")
for path in local:
    candidates = list(reference.rglob(path.name))
    if len(candidates) != 1:
        raise RuntimeError(f"Expected one reference for {path.name}")
    if hashlib.sha256(path.read_bytes()).digest() != hashlib.sha256(candidates[0].read_bytes()).digest():
        if path.suffix == ".jar":
            with zipfile.ZipFile(path) as left, zipfile.ZipFile(candidates[0]) as right:
                for name in sorted(set(left.namelist()) | set(right.namelist())):
                    if name not in left.namelist() or name not in right.namelist() or left.read(name) != right.read(name):
                        print(f"Different archive entry: {name}", flush=True)
        raise RuntimeError(f"Cross-platform artifact mismatch: {path.name}")
    print(f"Identical across platforms: {path.name}")

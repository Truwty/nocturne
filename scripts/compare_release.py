"""Compare a downloaded build artifact with this checkout's rebuilt release."""
from __future__ import annotations
import hashlib
from pathlib import Path
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
reference = Path(sys.argv[1]).resolve()
local = list((root / "build/libs").glob("*.jar")) + [root / "build/release/nocturne-mc26.1.2.json"]
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

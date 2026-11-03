"""Generate Nocturne's original pixel art; no vanilla or third-party texture inputs."""
import json
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/resourcepacks"
THEMES = {"mint": (125, 240, 201), "cyber": (174, 117, 255), "amber": (255, 187, 99)}


def png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    def chunk(kind: bytes, value: bytes) -> bytes:
        return struct.pack(">I", len(value)) + kind + value + struct.pack(">I", zlib.crc32(kind + value))
    raw = b"".join(b"\0" + bytes(v for pixel in row for v in pixel) for row in pixels)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def texture(path: Path, kind: str, tint: tuple[int, int, int]) -> None:
    frames = []
    for frame in range(8):
        for y in range(16):
            row = []
            for x in range(16):
                pulse = .72 + .28 * math.sin((x + y) * .5 - frame * math.pi / 4)
                ink = tuple(round(c * pulse) for c in tint) + (255,)
                if kind == "sword":
                    blade = 3 <= x <= 13 and 2 <= y <= 12 and abs(x + y - 15) <= 1
                    guard = 2 <= x <= 7 and 8 <= y <= 13 and abs(x - y + 6) <= 1
                    handle = 1 <= x <= 4 and 11 <= y <= 14 and abs(x + y - 15) <= 1
                    value = ink if blade else (239, 215, 153, 255) if guard else (73, 66, 92, 255) if handle else (0, 0, 0, 0)
                elif kind == "totem":
                    star = abs(x - 7.5) + abs(y - 7.5) * .65 < 4 or (abs(x - 7.5) < 1.5 and 2 <= y <= 13)
                    value = ink if star else (0, 0, 0, 0)
                    if 6 <= x <= 9 and 6 <= y <= 9:
                        value = (245, 255, 251, 255)
                else:
                    border = x in (0, 15) or y in (0, 15)
                    value = (24, 29, 42, 255) if border else ink if (x + y + frame) % 8 <= 1 else tuple(round(c * .27) for c in tint) + (255,)
                row.append(value)
            frames.append(row)
    png(path, frames)
    path.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": 12, "interpolate": True}}) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    for theme, tint in THEMES.items():
        for kind in ("swords", "totems", "blocks"):
            pack = ROOT / (theme + "_" + kind)
            pack.mkdir(parents=True, exist_ok=True)
            (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"description": "Nocturne / " + theme.title() + " animated " + kind, "min_format": [84, 0], "max_format": [84, 0]}}) + "\n", encoding="utf-8", newline="\n")
            names = [name + "_sword" for name in ("wooden", "stone", "iron", "golden", "diamond", "netherite")] if kind == "swords" else ["totem_of_undying"] if kind == "totems" else ["diamond_block", "emerald_block", "amethyst_block"]
            for name in names:
                texture(pack / "assets/minecraft/textures" / ("block" if kind == "blocks" else "item") / (name + ".png"), "sword" if kind == "swords" else "totem" if kind == "totems" else "block", tint)
    pack=ROOT / "hd_weather"
    pack.mkdir(parents=True,exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"description": "Nocturne / HD rain and snow", "min_format": [84,0], "max_format": [84,0]}})+"\n",encoding="utf-8",newline="\n")
    for kind in ("rain","snow"):
        pixels=[]
        for y in range(128):
            row=[]
            for x in range(128):
                if kind=="rain":
                    line=(x*17+13)%127
                    opacity=max(0,1-abs((x%16)-8)/2)*(.3+.7*((y+line)%64)/64)
                    value=(183,210,238,round(130*opacity))
                else:
                    opacity=0
                    for cx,cy in ((18,18),(70,42),(38,98),(104,106),(112,8)):
                        dx=abs(x-cx);dy=abs(y-cy)
                        opacity=max(opacity,max(0,1-min(dx,dy)*.6)*max(0,1-max(dx,dy)/5))
                    value=(235,245,255,round(220*opacity))
                row.append(value)
            pixels.append(row)
        png(pack / "assets/minecraft/textures/environment" / (kind+".png"),pixels)
    print("Generated 9 animated texture packs and one optional HD weather pack.")


if __name__ == "__main__":
    main()

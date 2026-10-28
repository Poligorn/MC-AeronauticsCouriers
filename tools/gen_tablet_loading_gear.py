#!/usr/bin/env python3
"""16x16 brass loading gear for tablet UI spinner (Create-style pixel art)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites/tablet_loading_gear.png"
SIZE = 20

BRASS_HI = (0xFF, 0xD2, 0x5D)
BRASS_MID = (0xB8, 0x7E, 0x3C)
BRASS_LO = (0x69, 0x38, 0x26)
SHADOW = (0x3E, 0x26, 0x14)
HOLE = (0x2C, 0x09, 0x00)


def main() -> None:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy = SIZE // 2, SIZE // 2
    # 8-tooth gear silhouette
    outer_r = 9
    inner_r = 5
    tooth = 2
    for i in range(8):
        a0 = i * 45
        a1 = a0 + 45 - 8
        for a in range(a0 + 4, a1):
            import math

            rad = math.radians(a)
            x = cx + int(math.cos(rad) * outer_r)
            y = cy + int(math.sin(rad) * outer_r)
            d.point((x, y), BRASS_MID)
    d.ellipse((cx - inner_r, cy - inner_r, cx + inner_r - 1, cy + inner_r - 1), fill=BRASS_HI, outline=BRASS_LO)
    d.ellipse((cx - 2, cy - 2, cx + 1, cy + 1), fill=HOLE)
    # highlight on top-left teeth
    for x, y in [(cx - 6, cy - 8), (cx - 8, cy - 5), (cx - 7, cy - 7)]:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img.putpixel((x, y), BRASS_HI)
    for x, y in [(cx + 6, cy + 7), (cx + 7, cy + 8)]:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img.putpixel((x, y), SHADOW)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()

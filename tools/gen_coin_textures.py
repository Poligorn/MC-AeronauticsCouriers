#!/usr/bin/env python3
"""Generate original 9×9 spur denomination coin textures for the tablet GUI."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/coins"

# (fill, rim, highlight) per denomination — distinct hues, not Numismatics assets.
PALETTES: dict[str, tuple[tuple[int, ...], tuple[int, ...], tuple[int, ...]]] = {
    "sun": ((0xFF, 0xD5, 0x4A, 255), (0xB8, 0x6A, 0x10, 255), (0xFF, 0xF0, 0xA8, 255)),
    "crown": ((0xF7, 0xCB, 0x6C, 255), (0x8A, 0x52, 0x18, 255), (0xFF, 0xE8, 0x9A, 255)),
    "cog": ((0x6A, 0xC4, 0x5A, 255), (0x2E, 0x6B, 0x28, 255), (0xA8, 0xE8, 0x98, 255)),
    "sprocket": ((0x5A, 0x9E, 0xE8, 255), (0x1E, 0x4A, 0x88, 255), (0x98, 0xC8, 0xFF, 255)),
    "bevel": ((0xB8, 0xC8, 0xD8, 255), (0x5A, 0x68, 0x78, 255), (0xE8, 0xF0, 0xF8, 255)),
    "spur": ((0xC4, 0x7C, 0x25, 255), (0x5C, 0x30, 0x12, 255), (0xE8, 0xA8, 0x58, 255)),
}


def draw_coin(name: str, fill: tuple[int, ...], rim: tuple[int, ...], hi: tuple[int, ...]) -> None:
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 4, 4
    for y in range(9):
        for x in range(9):
            dx, dy = abs(x - cx), abs(y - cy)
            if dx + dy > 5:
                continue
            if dx + dy == 5:
                px[x, y] = rim
            elif dx + dy <= 2 and x + y <= 8:
                px[x, y] = hi
            else:
                px[x, y] = fill
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png")


def main() -> None:
    for coin_id, colors in PALETTES.items():
        draw_coin(coin_id, *colors)
    print(f"gen_coin_textures: wrote {len(PALETTES)} coins to {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

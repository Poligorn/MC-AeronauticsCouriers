#!/usr/bin/env python3
"""Legacy generator for 16x16 rust/steel cargo faces.

In-game S/M/L/XL textures are the approved E2 set (no doors). Do not overwrite
`textures/block/cargo_container_*.png` from this script unless regenerating a
scratch palette. Apply authored files with `tools/apply_e2_cargo_textures.py`.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/block"
SIZE = 16

PALETTES = {
    "rust": {
        "rib_highlight": (0xA8, 0x53, 0x3A),
        "rib_mid": (0x8A, 0x3F, 0x2C),
        "rib_shadow": (0x5E, 0x2A, 0x1F),
        "rib_deep": (0x3E, 0x1C, 0x16),
        "rib_subtle_hi": (0xB8, 0x65, 0x4A),
        "frame": (0x3E, 0x1C, 0x16),
        "frame_hi": (0x5E, 0x2A, 0x1F),
        "frame_lo": (0x2E, 0x14, 0x10),
        "door_base": (0x6E, 0x40, 0x30),
        "door_dark": (0x42, 0x26, 0x1C),
        "door_bar": (0x2E, 0x2A, 0x28),
        "rivet": (0x5E, 0x2A, 0x1F),
        "rivet_hi": (0x8A, 0x3F, 0x2C),
    },
    "steel": {
        "rib_highlight": (0x7D, 0x8A, 0x94),
        "rib_mid": (0x5F, 0x6B, 0x74),
        "rib_shadow": (0x43, 0x4C, 0x53),
        "rib_deep": (0x2E, 0x34, 0x39),
        "rib_subtle_hi": (0x7D, 0x8A, 0x94),
        "frame": (0x2E, 0x34, 0x39),
        "frame_hi": (0x43, 0x4C, 0x53),
        "frame_lo": (0x22, 0x26, 0x2A),
        "door_base": (0x5C, 0x64, 0x6E),
        "door_dark": (0x38, 0x3E, 0x46),
        "door_bar": (0x2A, 0x2E, 0x34),
        "rivet": (0x43, 0x4C, 0x53),
        "rivet_hi": (0x5F, 0x6B, 0x74),
    },
}

PAPER = (0xE4, 0xD4, 0xB0)
PAPER_SHADE = (0xC8, 0xB8, 0x94)
PAPER_LINE = (0x3A, 0x32, 0x28)
STAMP_RED = (0x8A, 0x2A, 0x28)
CLIP = (0x5A, 0x5A, 0x5A)
CLIP_HI = (0x8A, 0x8A, 0x8A)


def px(img: Image.Image, x: int, y: int, c: tuple[int, ...]) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), c)


def frame_border(draw: ImageDraw.ImageDraw, pal: dict) -> None:
    draw.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=pal["frame_lo"])
    draw.rectangle((0, 0, 1, SIZE - 1), fill=pal["frame"])
    draw.rectangle((SIZE - 2, 0, SIZE - 1, SIZE - 1), fill=pal["frame_hi"])
    draw.rectangle((0, 0, SIZE - 1, 1), fill=pal["frame_hi"])
    draw.rectangle((0, SIZE - 2, SIZE - 1, SIZE - 1), fill=pal["frame_lo"])


def fill_inner(img: Image.Image, pal: dict) -> None:
    draw = ImageDraw.Draw(img)
    draw.rectangle((2, 2, SIZE - 3, SIZE - 3), fill=pal["rib_deep"])


def draw_ribs(img: Image.Image, pal: dict, vertical: bool = True) -> None:
    fill_inner(img, pal)
    draw = ImageDraw.Draw(img)
    frame_border(draw, pal)
    inner_lo, inner_hi = 2, SIZE - 3
    tones = (pal["rib_shadow"], pal["rib_mid"], pal["rib_highlight"], pal["rib_mid"])
    if vertical:
        x = 2
        while x <= inner_hi:
            col_tone = tones[(x - 2) % len(tones)]
            for y in range(inner_lo, inner_hi + 1):
                px(img, x, y, col_tone)
            if x + 1 <= inner_hi:
                for y in range(inner_lo, inner_hi + 1):
                    px(img, x + 1, y, pal["rib_shadow"] if (x - 2) % 4 < 2 else pal["rib_deep"])
            px(img, x, inner_lo, pal["rib_subtle_hi"])
            x += 2
    else:
        y = 2
        while y <= inner_hi:
            row_tone = tones[(y - 2) % len(tones)]
            for x in range(inner_lo, inner_hi + 1):
                px(img, x, y, row_tone)
            if y + 1 <= inner_hi:
                for x in range(inner_lo, inner_hi + 1):
                    px(img, x, y + 1, pal["rib_shadow"] if (y - 2) % 4 < 2 else pal["rib_deep"])
            px(img, inner_lo, y, pal["rib_subtle_hi"])
            y += 2
    for c in ((1, 1), (SIZE - 2, 1), (1, SIZE - 2), (SIZE - 2, SIZE - 2)):
        px(img, c[0], c[1], pal["frame_hi"])
    for i in range(4, SIZE - 4, 3):
        px(img, 1, i, pal["rivet_hi"])
        px(img, SIZE - 2, i, pal["rivet"])
        px(img, i, 1, pal["rivet_hi"])
        px(img, i, SIZE - 2, pal["rivet"])


def draw_door(img: Image.Image, pal: dict) -> None:
    img.paste(Image.new("RGBA", (SIZE, SIZE), pal["rib_deep"] + (255,)))
    draw = ImageDraw.Draw(img)
    frame_border(draw, pal)
    draw.rectangle((2, 2, SIZE - 3, SIZE - 3), fill=pal["door_base"])
    # vertical corrugation on doors
    for x in range(4, SIZE - 4, 2):
        shade = pal["rib_shadow"] if x % 4 else pal["rib_mid"]
        draw.line((x, 3, x, SIZE - 4), fill=shade, width=1)
    # center seam
    mid = SIZE // 2
    draw.line((mid, 2, mid, SIZE - 3), fill=pal["door_dark"], width=1)
    # locking bars
    for bar_x in (mid - 3, mid + 2):
        draw.rectangle((bar_x, 3, bar_x, SIZE - 4), fill=pal["door_bar"])
        px(img, bar_x, 5, pal["rivet_hi"])
        px(img, bar_x, SIZE - 5, pal["rivet_hi"])
    # handles
    for hx in (mid - 5, mid + 4):
        draw.rectangle((hx, SIZE // 2 - 1, hx + 1, SIZE // 2 + 1), fill=pal["frame_hi"])
    # rivets on frame
    for i in range(3, SIZE - 3, 4):
        px(img, 2, i, pal["rivet"])
        px(img, SIZE - 3, i, pal["rivet"])


def draw_side_waybill(img: Image.Image, pal: dict) -> None:
    draw_ribs(img, pal, vertical=True)
    draw = ImageDraw.Draw(img)
    # clipboard clip at top
    draw.rectangle((9, 2, 12, 3), fill=CLIP_HI)
    draw.rectangle((10, 3, 11, 4), fill=CLIP)
    # paper sheet
    draw.rectangle((6, 4, 13, 12), fill=PAPER, outline=PAPER_SHADE)
    for row, y in enumerate((6, 8, 10)):
        draw.line((7, y, 12, y), fill=PAPER_LINE, width=1)
        if row == 0:
            draw.line((7, y, 10, y), fill=PAPER_LINE, width=1)
    # stamp / seal
    draw.ellipse((9, 9, 12, 12), outline=STAMP_RED, width=1)
    draw.line((9, 10, 12, 11), fill=STAMP_RED, width=1)


def generate(palette_name: str, suffix: str = "") -> dict[str, Image.Image]:
    pal = PALETTES[palette_name]
    base = pal["rib_deep"] + (255,)
    top = Image.new("RGBA", (SIZE, SIZE), base)
    draw_ribs(top, pal, vertical=False)
    bottom = top.copy()
    side = Image.new("RGBA", (SIZE, SIZE), base)
    draw_ribs(side, pal, vertical=True)
    door = Image.new("RGBA", (SIZE, SIZE), base)
    draw_door(door, pal)
    side_wb = Image.new("RGBA", (SIZE, SIZE), base)
    draw_side_waybill(side_wb, pal)
    tag = suffix or ""
    return {
        f"cargo_container_top{tag}.png": top,
        f"cargo_container_bottom{tag}.png": bottom,
        f"cargo_container_side{tag}.png": side,
        f"cargo_container_door{tag}.png": door,
        f"cargo_container_side_waybill{tag}.png": side_wb,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--palette", choices=("rust", "steel"), default="rust")
    parser.add_argument("--write-alt", action="store_true", help="Also write steel palette as *_steel.png")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, img in generate(args.palette).items():
        img.save(OUT / name)
        print("wrote", OUT / name)
    if args.write_alt:
        alt = "steel" if args.palette == "rust" else "rust"
        for name, img in generate(alt, suffix="_alt").items():
            img.save(OUT / name)
            print("wrote alt", OUT / name)


if __name__ == "__main__":
    main()

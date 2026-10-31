#!/usr/bin/env python3
"""Original 16×16 post-station textures for concept A «Стойка».

Minecraft-style pixels drawn from scratch (andesite casing, oak planks, copper roof,
brass fittings). No pixels copied from vanilla or other mods.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/block"
SHEET = ROOT / "artifacts" / "post_station_texture_sheet.png"
SIZE = 16

# Original palette — Create-adjacent, not sampled from Create/vanilla files.
ANDESITE_HI = (0x9C, 0x98, 0x92)
ANDESITE = (0x7C, 0x78, 0x72)
ANDESITE_MID = (0x68, 0x64, 0x5E)
ANDESITE_LO = (0x52, 0x4E, 0x4A)
ANDESITE_DEEP = (0x3A, 0x38, 0x36)
ANDESITE_SPECK = (0x8A, 0x86, 0x80)

BRASS_HI = (0xE8, 0xC4, 0x5A)
BRASS = (0xC4, 0x96, 0x3C)
BRASS_LO = (0x8A, 0x68, 0x24)
BRASS_DEEP = (0x5C, 0x42, 0x16)

COPPER_HI = (0xE2, 0x8C, 0x6A)
COPPER = (0xB8, 0x68, 0x4C)
COPPER_MID = (0x9A, 0x52, 0x3C)
COPPER_LO = (0x72, 0x3A, 0x2A)
COPPER_OX = (0x4A, 0x6A, 0x4E)

PLANK_HI = (0xC8, 0x9E, 0x64)
PLANK = (0xA6, 0x7C, 0x48)
PLANK_MID = (0x8E, 0x66, 0x3A)
PLANK_LO = (0x6A, 0x4C, 0x2C)
PLANK_GAP = (0x4A, 0x34, 0x1E)

BOARD = (0x3E, 0x2A, 0x1A)
BOARD_HI = (0x5A, 0x3E, 0x26)
BOARD_LO = (0x2A, 0x1C, 0x12)
PAPER = (0xF2, 0xE8, 0xCC)
PAPER_SHADE = (0xD6, 0xC8, 0xA8)
PAPER_LINE = (0x5A, 0x4A, 0x38)
PAPER_PIN = (0x8C, 0x2C, 0x28)
PAPER_2 = (0xE8, 0xDC, 0xB8)
PAPER_3 = (0xF6, 0xF0, 0xDC)

SLOT = (0x14, 0x12, 0x10)
SLOT_INNER = (0x22, 0x20, 0x1C)

LANTERN_FRAME = (0x3A, 0x32, 0x2A)
LANTERN_FRAME_HI = (0x5A, 0x50, 0x44)
LANTERN_GLOW = (0xFF, 0xD0, 0x4A)
LANTERN_CORE = (0xFF, 0xF2, 0xA8)
LANTERN_GLASS = (0xF0, 0xC0, 0x40)

WINDOW_FRAME = (0x4A, 0x3A, 0x28)
WINDOW_GLASS = (0xC8, 0xE0, 0xE8)
WINDOW_GLOW = (0xFF, 0xE0, 0x8A)

ANTENNA = COPPER
ANTENNA_HI = COPPER_HI
TIP = (0xFF, 0xF6, 0xB0)
TIP_CORE = (0xFF, 0xFF, 0xE0)

TRANSPARENT = (0, 0, 0, 0)


def new_img(fill=None) -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), fill or TRANSPARENT)
    return img


def px(img: Image.Image, x: int, y: int, c: tuple[int, ...]) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        if len(c) == 3:
            img.putpixel((x, y), (*c, 255))
        else:
            img.putpixel((x, y), c)


def fill(img: Image.Image, color: tuple[int, int, int]) -> None:
    for y in range(SIZE):
        for x in range(SIZE):
            px(img, x, y, color)


def andesite_fill(img: Image.Image) -> None:
    """Speckled andesite casing — not a copy of vanilla andesite."""
    base = [
        ANDESITE, ANDESITE_MID, ANDESITE, ANDESITE_LO,
        ANDESITE_MID, ANDESITE, ANDESITE_HI, ANDESITE,
    ]
    speck = [(2, 3, ANDESITE_SPECK), (5, 1, ANDESITE_HI), (8, 6, ANDESITE_LO),
             (11, 2, ANDESITE_DEEP), (1, 9, ANDESITE_HI), (14, 8, ANDESITE_SPECK),
             (4, 12, ANDESITE_DEEP), (9, 11, ANDESITE_HI), (13, 14, ANDESITE_LO),
             (6, 8, ANDESITE_SPECK), (3, 5, ANDESITE_LO), (12, 4, ANDESITE_MID),
             (7, 14, ANDESITE_SPECK), (15, 10, ANDESITE_DEEP), (0, 13, ANDESITE_HI)]
    for y in range(SIZE):
        for x in range(SIZE):
            px(img, x, y, base[(x + y * 3) % len(base)])
    for x, y, c in speck:
        px(img, x, y, c)
    # Casing edge bevel
    for i in range(SIZE):
        px(img, i, 0, ANDESITE_HI)
        px(img, 0, i, ANDESITE_MID)
        px(img, i, SIZE - 1, ANDESITE_DEEP)
        px(img, SIZE - 1, i, ANDESITE_LO)


def plank_fill(img: Image.Image, vertical: bool = False) -> None:
    boards = (PLANK_HI, PLANK, PLANK_MID, PLANK, PLANK_HI)
    for y in range(SIZE):
        for x in range(SIZE):
            band = (y if not vertical else x) // 4
            tone = boards[band % len(boards)]
            if (x if not vertical else y) % 5 == 0:
                tone = PLANK_GAP
            px(img, x, y, tone)
    # grain ticks
    for y in range(1, SIZE, 4):
        for x in range(1, SIZE, 3):
            if img.getpixel((x, y))[:3] != PLANK_GAP:
                px(img, x, y, PLANK_LO if (x + y) % 2 == 0 else PLANK_HI)


def copper_fill(img: Image.Image) -> None:
    for y in range(SIZE):
        for x in range(SIZE):
            v = (x * 3 + y * 5) % 7
            if v == 0:
                c = COPPER_HI
            elif v in (1, 2):
                c = COPPER
            elif v == 6:
                c = COPPER_OX if (x + y) % 11 == 0 else COPPER_LO
            else:
                c = COPPER_MID
            px(img, x, y, c)
    for i in range(SIZE):
        px(img, i, 0, COPPER_HI)
        px(img, i, SIZE - 1, COPPER_LO)


def front_lower() -> Image.Image:
    img = new_img()
    andesite_fill(img)
    # Brass mail-slot frame (horizontal, mid-front)
    for x in range(3, 13):
        for y in range(8, 13):
            px(img, x, y, BRASS if y in (8, 12) or x in (3, 12) else BRASS_LO)
    for x in range(3, 13):
        px(img, x, 8, BRASS_HI)
        px(img, x, 12, BRASS_DEEP)
    px(img, 3, 8, BRASS_HI)
    px(img, 12, 8, BRASS)
    # Slot opening
    for x in range(4, 12):
        px(img, x, 9, SLOT)
        px(img, x, 10, SLOT_INNER)
        px(img, x, 11, SLOT)
    px(img, 4, 10, SLOT)
    px(img, 11, 10, SLOT)
    # Corner rivets
    for x, y in ((3, 8), (12, 8), (3, 12), (12, 12)):
        px(img, x, y, BRASS_HI)
    return img


def front_upper() -> Image.Image:
    img = new_img()
    plank_fill(img)
    # Dark order board
    for x in range(1, 15):
        for y in range(1, 14):
            px(img, x, y, BOARD)
    for x in range(1, 15):
        px(img, x, 1, BOARD_HI)
        px(img, x, 13, BOARD_LO)
    for y in range(1, 14):
        px(img, 1, y, BOARD_HI)
        px(img, 14, y, BOARD_LO)
    # Paper sheet 1 (left, slightly tilted look via offset)
    for x in range(2, 7):
        for y in range(3, 12):
            px(img, x, y, PAPER if y > 3 else PAPER_SHADE)
    for y in range(5, 11, 2):
        for x in range(3, 6):
            px(img, x, y, PAPER_LINE)
    px(img, 4, 3, PAPER_PIN)
    # Paper sheet 2 (center-right)
    for x in range(7, 12):
        for y in range(2, 10):
            px(img, x, y, PAPER_2 if y > 2 else PAPER_SHADE)
    for y in range(4, 9, 2):
        for x in range(8, 11):
            px(img, x, y, PAPER_LINE)
    px(img, 9, 2, PAPER_PIN)
    # Paper sheet 3 (bottom-right, smaller)
    for x in range(10, 14):
        for y in range(8, 13):
            px(img, x, y, PAPER_3)
    for y in (9, 11):
        for x in range(11, 13):
            px(img, x, y, PAPER_LINE)
    px(img, 12, 8, PAPER_PIN)
    return img


def side_upper() -> Image.Image:
    img = new_img()
    plank_fill(img)
    # Small window (glows at night via block light)
    for x in range(5, 11):
        for y in range(4, 11):
            border = x in (5, 10) or y in (4, 10)
            px(img, x, y, WINDOW_FRAME if border else WINDOW_GLOW)
    px(img, 7, 6, WINDOW_GLASS)
    px(img, 8, 7, WINDOW_CORE if False else LANTERN_CORE)
    px(img, 6, 8, WINDOW_GLASS)
    # Crossbar
    for x in range(5, 11):
        px(img, x, 7, WINDOW_FRAME)
    for y in range(4, 11):
        px(img, 7, y, WINDOW_FRAME)
    return img


def side_lantern() -> Image.Image:
    """East face of the upper half: planks + lantern mount (lantern is a model extra)."""
    img = new_img()
    plank_fill(img)
    # Brass mounting plate
    for x in range(10, 15):
        for y in range(6, 11):
            px(img, x, y, BRASS_LO if x in (10, 14) or y in (6, 10) else BRASS)
    px(img, 12, 8, BRASS_HI)
    return img


def roof_top() -> Image.Image:
    img = new_img()
    copper_fill(img)
    # Ridge along Z (center line)
    for i in range(SIZE):
        px(img, 7, i, COPPER_HI)
        px(img, 8, i, COPPER_HI)
        px(img, 6, i, COPPER)
        px(img, 9, i, COPPER)
    # Antenna foot
    for x in range(6, 10):
        for y in range(6, 10):
            px(img, x, y, BRASS if (x in (6, 9) or y in (6, 9)) else BRASS_HI)
    return img


def roof_side() -> Image.Image:
    img = new_img()
    copper_fill(img)
    for x in range(SIZE):
        px(img, x, 0, COPPER_HI)
        px(img, x, 1, COPPER)
        px(img, x, SIZE - 1, COPPER_LO)
    return img


def lantern() -> Image.Image:
    img = new_img()
    # Transparent background; lantern body in the middle
    # Frame
    for x in range(4, 12):
        px(img, x, 2, LANTERN_FRAME_HI)
        px(img, x, 13, LANTERN_FRAME)
    for y in range(2, 14):
        px(img, 4, y, LANTERN_FRAME)
        px(img, 11, y, LANTERN_FRAME)
    # Glass
    for x in range(5, 11):
        for y in range(3, 13):
            px(img, x, y, LANTERN_GLOW if (x + y) % 2 == 0 else LANTERN_GLASS)
    for x in range(6, 10):
        for y in range(5, 11):
            px(img, x, y, LANTERN_CORE)
    px(img, 7, 7, TIP_CORE)
    px(img, 8, 8, TIP_CORE)
    # Hook
    px(img, 7, 1, LANTERN_FRAME_HI)
    px(img, 8, 1, LANTERN_FRAME_HI)
    px(img, 7, 0, LANTERN_FRAME)
    px(img, 8, 0, LANTERN_FRAME)
    return img


def antenna() -> Image.Image:
    img = new_img()
    # Vertical copper rod down the center, brass rings, glowing tip
    for y in range(SIZE):
        px(img, 7, y, ANTENNA)
        px(img, 8, y, ANTENNA_HI)
    # Rings
    for y in (4, 10):
        for x in range(5, 11):
            px(img, x, y, BRASS_HI if x in (7, 8) else BRASS)
            px(img, x, y + 1, BRASS_LO)
    # Tip glow (top)
    for x in range(6, 10):
        for y in range(0, 3):
            px(img, x, y, TIP if y else TIP_CORE)
    return img


def antenna_tip() -> Image.Image:
    img = new_img()
    for x in range(5, 11):
        for y in range(5, 11):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 1.5:
                px(img, x, y, TIP_CORE)
            elif d < 3:
                px(img, x, y, TIP)
            elif d < 4:
                px(img, x, y, LANTERN_GLOW)
    return img


def save(img: Image.Image, name: str) -> Path:
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    img.save(path)
    return path


def contact_sheet(named: list[tuple[str, Image.Image]]) -> None:
    cols = 4
    rows = (len(named) + cols - 1) // cols
    pad = 4
    label_h = 10
    cell = SIZE * 4 + pad
    sheet = Image.new("RGBA", (cols * cell + pad, rows * (cell + label_h) + pad), (24, 22, 20, 255))
    for i, (name, src) in enumerate(named):
        r, c = divmod(i, cols)
        big = src.resize((SIZE * 4, SIZE * 4), Image.Resampling.NEAREST)
        x = pad + c * cell
        y = pad + r * (cell + label_h)
        sheet.paste(big, (x, y))
        # tiny label bar
        bar = Image.new("RGBA", (SIZE * 4, label_h), (40, 36, 32, 255))
        sheet.paste(bar, (x, y + SIZE * 4))
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(SHEET)
    print(f"contact sheet {SHEET} {sheet.size}")


def main() -> None:
    named: list[tuple[str, Image.Image]] = []

    def add(name: str, img: Image.Image) -> None:
        save(img, name)
        named.append((name, img))
        colors = {img.getpixel((x, y))[:3] for y in range(SIZE) for x in range(SIZE)
                  if img.getpixel((x, y))[3] > 0}
        print(f"{name}: 16x16 unique_rgb={len(colors)}")

    andesite = new_img()
    andesite_fill(andesite)
    add("post_station_andesite.png", andesite)

    andesite_top = new_img()
    andesite_fill(andesite_top)
    add("post_station_andesite_top.png", andesite_top)

    andesite_bottom = new_img()
    andesite_fill(andesite_bottom)
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = andesite_bottom.getpixel((x, y))
            px(andesite_bottom, x, y, (max(0, r - 20), max(0, g - 20), max(0, b - 20)))
    add("post_station_andesite_bottom.png", andesite_bottom)

    add("post_station_front_lower.png", front_lower())

    planks = new_img()
    plank_fill(planks)
    add("post_station_planks.png", planks)

    planks_back = new_img()
    plank_fill(planks_back)
    add("post_station_planks_back.png", planks_back)

    add("post_station_front_upper.png", front_upper())
    add("post_station_side_upper.png", side_upper())
    add("post_station_side_lantern.png", side_lantern())
    add("post_station_roof.png", roof_top())
    add("post_station_roof_side.png", roof_side())
    add("post_station_lantern.png", lantern())
    add("post_station_antenna.png", antenna())
    add("post_station_antenna_tip.png", antenna_tip())

    contact_sheet(named)
    print(f"wrote {len(named)} textures to {OUT}")


if __name__ == "__main__":
    main()

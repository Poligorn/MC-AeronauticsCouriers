#!/usr/bin/env python3
"""Original 16x16 order tablet item + Curios slot icon (PIL). Not derived from FTB Quests Create art."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ITEM_OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/item/order_tablet.png"
SLOT_OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/slot/order_tablet.png"
SIZE = 16

# Brass bezel
BRASS_HI = (0xFF, 0xE9, 0x7C)
BRASS_MID = (0xFF, 0xD2, 0x5D)
BRASS_LO = (0xB8, 0x7E, 0x3C)
BRASS_DK = (0x69, 0x38, 0x26)
# Screen
SCREEN = (0x2C, 0x09, 0x00)
GLOW_HI = (0xFF, 0xAF, 0x55)
GLOW_LO = (0xFF, 0x7A, 0x2F)
# Copper antenna
COPPER_HI = (0xE3, 0x82, 0x6C)
COPPER_MID = (0xB2, 0x62, 0x47)
COPPER_DK = (0x79, 0x46, 0x31)
# Gear
GEAR_HI = (0x82, 0x61, 0x3A)
GEAR_LO = (0x5A, 0x44, 0x24)
IRON = (0x6A, 0x6A, 0x6E)


def px(img: Image.Image, x: int, y: int, c: tuple[int, ...]) -> None:
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), c + (255,) if len(c) == 3 else c)


def draw_tablet() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Outer brass frame
    d.rectangle((2, 3, 13, 14), outline=BRASS_DK)
    d.rectangle((3, 4, 12, 13), fill=BRASS_MID, outline=BRASS_LO)
    d.line([(3, 4), (12, 4)], fill=BRASS_HI)
    d.line([(3, 4), (3, 13)], fill=BRASS_HI)
    d.line([(12, 4), (12, 13)], fill=BRASS_DK)
    d.line([(3, 13), (12, 13)], fill=BRASS_DK)

    # Screen recess
    d.rectangle((5, 6, 10, 11), fill=SCREEN)
    # Glowing UI lines
    for x in range(6, 10):
        px(img, x, 7, GLOW_HI)
    px(img, 6, 8, GLOW_LO)
    px(img, 7, 8, GLOW_HI)
    px(img, 8, 9, GLOW_LO)
    px(img, 9, 10, GLOW_HI)
    d.line([(6, 10), (9, 10)], fill=GLOW_LO)

    # Copper antenna (top)
    px(img, 7, 1, COPPER_DK)
    px(img, 8, 1, COPPER_MID)
    px(img, 7, 2, COPPER_MID)
    px(img, 8, 2, COPPER_HI)
    d.point((7, 0), COPPER_HI)
    d.point((8, 0), COPPER_HI)

    # Side gear (left)
    gear_pts = [
        (1, 8, GEAR_LO),
        (2, 7, GEAR_HI),
        (2, 8, GEAR_LO),
        (2, 9, GEAR_HI),
        (1, 9, GEAR_LO),
        (0, 8, GEAR_LO),
    ]
    for x, y, c in gear_pts:
        px(img, x, y, c)
    px(img, 1, 7, GEAR_HI)
    px(img, 0, 7, GEAR_LO)
    px(img, 0, 9, GEAR_LO)

    # Grip hint (right)
    px(img, 13, 7, BRASS_LO)
    px(img, 14, 7, BRASS_MID)
    px(img, 13, 8, BRASS_DK)
    px(img, 14, 8, BRASS_LO)
    px(img, 13, 9, IRON)
    px(img, 14, 9, IRON)

    return img


def draw_slot_icon() -> Image.Image:
    """Small brass tablet silhouette for the Curios slot."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((3, 2, 12, 13), radius=2, fill=BRASS_MID, outline=BRASS_DK, width=1)
    d.rectangle((5, 5, 10, 10), fill=SCREEN)
    d.point((7, 7), GLOW_HI)
    d.point((8, 8), GLOW_LO)
    d.point((9, 9), GLOW_HI)
    d.point((7, 1), COPPER_MID)
    d.point((8, 1), COPPER_HI)
    return img


def main() -> None:
    ITEM_OUT.parent.mkdir(parents=True, exist_ok=True)
    SLOT_OUT.parent.mkdir(parents=True, exist_ok=True)
    draw_tablet().save(ITEM_OUT)
    draw_slot_icon().save(SLOT_OUT)
    print("Wrote", ITEM_OUT)
    print("Wrote", SLOT_OUT)


if __name__ == "__main__":
    main()

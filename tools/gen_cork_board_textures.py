#!/usr/bin/env python3
"""Original pixel-art cork-board GUI atlas. Palette only; no third-party assets."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites/cork"

TRANSPARENT = (0, 0, 0, 0)

# Warm oak frame
WOOD = (0x8A, 0x58, 0x2C, 255)
WOOD_HI = (0xB4, 0x74, 0x3C, 255)
WOOD_MID = (0x7A, 0x4A, 0x22, 255)
WOOD_DK = (0x4E, 0x2C, 0x12, 255)
WOOD_LINE = (0x3A, 0x1E, 0x0A, 255)
GRAIN = (0x6A, 0x3E, 0x1A, 255)

# Bright cork (mockup 01)
CORK = (0xE2, 0xB8, 0x6E, 255)
CORK_HI = (0xF0, 0xD0, 0x8A, 255)
CORK_MID = (0xD2, 0xA4, 0x5C, 255)
CORK_DK = (0xB8, 0x88, 0x48, 255)
CORK_SPECK = (0x8E, 0x64, 0x28, 255)
CORK_PIT = (0x6A, 0x48, 0x1C, 255)

BRASS_HI = (0xF4, 0xD8, 0x5A, 255)
BRASS = (0xD0, 0xA4, 0x28, 255)
BRASS_DK = (0x8A, 0x68, 0x14, 255)
BRASS_RING = (0x5A, 0x40, 0x0C, 255)

CREAM = (0xF7, 0xEA, 0xC8, 255)
CREAM_SHADE = (0xE6, 0xD2, 0xA4, 255)
CREAM_EDGE = (0xC4, 0xA8, 0x70, 255)
GOLD_PAPER = (0xF4, 0xD4, 0x78, 255)
GOLD_SHADE = (0xE0, 0xB8, 0x48, 255)
GOLD_EDGE = (0xC4, 0x96, 0x20, 255)
PINK_PAPER = (0xF4, 0xD4, 0xE6, 255)
PINK_SHADE = (0xE0, 0xB0, 0xC8, 255)
PINK_EDGE = (0xC0, 0x70, 0x98, 255)
BLEND_PAPER = (0xF4, 0xD8, 0xB0, 255)
BLEND_EDGE = (0xC4, 0x88, 0x50, 255)
LOCK_PAPER = (0xD4, 0xD0, 0xC6, 255)
LOCK_SHADE = (0xB8, 0xB4, 0xAA, 255)
LOCK_EDGE = (0x8A, 0x86, 0x7C, 255)

PANEL = (0x4A, 0x2E, 0x16, 255)
PANEL_HI = (0x6A, 0x42, 0x20, 255)
PANEL_DK = (0x2A, 0x18, 0x0A, 255)
GOLD_TRIM = (0xE0, 0xC0, 0x48, 255)

RED = (0xC0, 0x38, 0x28, 255)
RED_HI = (0xE0, 0x58, 0x40, 255)
RED_DK = (0x7A, 0x18, 0x14, 255)
TEAL = (0x2A, 0x9A, 0x94, 255)
TEAL_DK = (0x1A, 0x64, 0x60, 255)
PINK = (0xD0, 0x48, 0x80, 255)
PINK_HI = (0xF0, 0x78, 0xA8, 255)
INK = (0x3A, 0x28, 0x14, 255)


def save(name: str, img: Image.Image, meta: dict | None = None) -> None:
    path = OUT / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if meta:
        (OUT / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


def nine(width: int, height: int, border: int | dict) -> dict:
    return {"gui": {"scaling": {"type": "nine_slice", "width": width, "height": height, "border": border}}}


def tile(width: int, height: int) -> dict:
    return {"gui": {"scaling": {"type": "tile", "width": width, "height": height}}}


def put(img: Image.Image, x: int, y: int, color: tuple[int, ...]) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def fill(img: Image.Image, x0: int, y0: int, x1: int, y1: int, color: tuple[int, ...]) -> None:
    ImageDraw.Draw(img).rectangle((x0, y0, x1 - 1, y1 - 1), fill=color)


def rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, color: tuple[int, ...]) -> None:
    d = ImageDraw.Draw(img)
    d.rectangle((x0, y0, x1 - 1, y1 - 1), outline=color)


def hash2(x: int, y: int, salt: int = 0) -> int:
    return (x * 13 + y * 37 + salt * 17 + (x ^ y) * 7) & 255


def rivet(img: Image.Image, cx: int, cy: int) -> None:
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) > 3:
                continue
            put(img, cx + dx, cy + dy, BRASS_RING)
    for dy in range(-1, 2):
        for dx in range(-1, 2):
            put(img, cx + dx, cy + dy, BRASS)
    put(img, cx, cy, BRASS_HI)
    put(img, cx + 1, cy + 1, BRASS_DK)


def gen_frame() -> None:
    w = h = 48
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, WOOD_DK)
    fill(img, 1, 1, w - 1, h - 1, WOOD)
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            hsh = hash2(x, y, 3)
            if y < 4 or y > h - 5 or x < 4 or x > w - 5:
                if hsh % 11 == 0:
                    put(img, x, y, WOOD_HI if (x + y) % 2 == 0 else GRAIN)
                elif hsh % 9 == 0:
                    put(img, x, y, WOOD_DK)
            else:
                put(img, x, y, WOOD_MID if hsh % 5 == 0 else WOOD)
    fill(img, 2, 2, w - 2, 4, WOOD_HI)
    fill(img, 2, h - 4, w - 2, h - 2, WOOD_DK)
    fill(img, 8, 8, w - 8, h - 8, WOOD_LINE)
    fill(img, 9, 9, w - 9, h - 9, TRANSPARENT)
    rivet(img, 4, 4)
    rivet(img, w - 5, 4)
    rivet(img, 4, h - 5)
    rivet(img, w - 5, h - 5)
    save("frame", img, nine(48, 48, {"left": 8, "top": 8, "right": 8, "bottom": 8}))


def gen_header() -> None:
    w, h = 64, 16
    img = Image.new("RGBA", (w, h), WOOD)
    for y in range(h):
        for x in range(w):
            hsh = hash2(x, y, 5)
            if y == 0:
                put(img, x, y, WOOD_HI)
            elif y == h - 1:
                put(img, x, y, GOLD_TRIM)
            elif hsh % 8 == 0:
                put(img, x, y, WOOD_HI if y < 4 else GRAIN)
            elif hsh % 11 == 0:
                put(img, x, y, WOOD_DK)
    save("header", img, nine(64, 16, {"left": 4, "top": 2, "right": 4, "bottom": 2}))


def gen_cork() -> None:
    w = h = 32
    img = Image.new("RGBA", (w, h), CORK)
    for y in range(h):
        for x in range(w):
            hsh = hash2(x, y, 1)
            n = hash2(x + 3, y * 2, 9)
            if hsh < 18:
                put(img, x, y, CORK_HI)
            elif hsh < 40:
                put(img, x, y, CORK_MID)
            elif hsh < 52:
                put(img, x, y, CORK_DK)
            if n < 10:
                put(img, x, y, CORK_SPECK)
            elif n < 14:
                put(img, x, y, CORK_PIT)
            # faint diagonal grain
            if (x + y * 2) % 17 == 0:
                put(img, x, y, CORK_MID)
    save("tile", img, tile(32, 32))


def gen_paper(name: str, fill_c: tuple[int, ...], shade: tuple[int, ...], edge: tuple[int, ...]) -> None:
    w = h = 32
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 1, 1, w - 1, h - 1, fill_c)
    fill(img, 2, h - 4, w - 2, h - 1, shade)
    fill(img, w - 4, 2, w - 1, h - 2, shade)
    for y in range(3, h - 4, 3):
        for x in range(3, w - 4):
            if hash2(x, y, 2) % 5 != 0:
                put(img, x, y, shade if hash2(x, y, 4) % 7 == 0 else fill_c)
    rect(img, 0, 0, w, h, edge)
    put(img, 1, 1, (255, 255, 255, 80))
    save(name, img, nine(32, 32, {"left": 3, "top": 3, "right": 3, "bottom": 3}))


def gen_pin(name: str, head: tuple[int, ...], hi: tuple[int, ...], dk: tuple[int, ...]) -> None:
    img = Image.new("RGBA", (9, 9), TRANSPARENT)
    # metal needle
    fill(img, 4, 5, 5, 9, (0x8A, 0x8A, 0x8A, 255))
    fill(img, 3, 6, 6, 8, (0x6A, 0x6A, 0x6A, 255))
    # round head
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if dx * dx + dy * dy <= 9:
                put(img, 4 + dx, 3 + dy, dk)
    for dy in range(-2, 3):
        for dx in range(-2, 3):
            if dx * dx + dy * dy <= 5:
                put(img, 4 + dx, 3 + dy, head)
    put(img, 3, 2, hi)
    put(img, 4, 2, hi)
    save(name, img)


def gen_wax() -> None:
    img = Image.new("RGBA", (14, 14), TRANSPARENT)
    for y in range(14):
        for x in range(14):
            dx, dy = x - 6.5, y - 6.5
            r2 = dx * dx + dy * dy
            if r2 <= 36:
                put(img, x, y, RED_DK)
            if r2 <= 28:
                put(img, x, y, RED)
            if r2 <= 16:
                put(img, x, y, RED_HI)
    # gold imprint
    fill(img, 5, 4, 9, 10, BRASS)
    fill(img, 4, 5, 10, 9, BRASS_HI)
    put(img, 6, 6, BRASS_DK)
    put(img, 7, 7, BRASS_DK)
    save("wax", img)


def gen_ribbon() -> None:
    img = Image.new("RGBA", (12, 16), TRANSPARENT)
    # bow
    fill(img, 0, 0, 5, 6, PINK)
    fill(img, 7, 0, 12, 6, PINK)
    fill(img, 1, 1, 4, 5, PINK_HI)
    fill(img, 8, 1, 11, 5, PINK_HI)
    fill(img, 4, 2, 8, 7, RED_DK)
    fill(img, 5, 3, 7, 6, PINK_HI)
    # tails
    fill(img, 3, 7, 6, 16, PINK)
    fill(img, 6, 8, 9, 16, (0xA0, 0x30, 0x60, 255))
    fill(img, 4, 8, 5, 15, PINK_HI)
    save("ribbon", img)


def gen_lock() -> None:
    img = Image.new("RGBA", (10, 12), TRANSPARENT)
    fill(img, 3, 0, 7, 5, (0x7A, 0x68, 0x48, 255))
    fill(img, 2, 1, 8, 4, (0x9A, 0x86, 0x60, 255))
    fill(img, 4, 2, 6, 5, (0x2A, 0x1A, 0x0C, 255))
    fill(img, 1, 5, 9, 12, INK)
    fill(img, 2, 6, 8, 11, (0x5A, 0x48, 0x30, 255))
    fill(img, 4, 8, 6, 10, (0xE8, 0xDC, 0xB8, 255))
    save("lock", img)


def gen_stamp() -> None:
    w, h = 18, 13
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, (0xE8, 0xD4, 0x98, 255))
    fill(img, 1, 1, w - 1, h - 1, (0xF4, 0xE4, 0xB0, 255))
    rect(img, 0, 0, w, h, (0xB0, 0x90, 0x48, 255))
    fill(img, 1, 1, w - 1, 2, (0xC8, 0xA8, 0x58, 255))
    fill(img, 1, h - 2, w - 1, h - 1, (0xC8, 0xA8, 0x58, 255))
    save("stamp", img)


def gen_progress() -> None:
    w, h = 48, 22
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, TEAL_DK)
    fill(img, 1, 1, w - 1, h - 1, (0xE4, 0xF4, 0xF0, 255))
    fill(img, 2, 2, w - 2, 3, TEAL)
    save("progress", img, nine(48, 22, {"left": 3, "top": 3, "right": 3, "bottom": 3}))


def gen_panel() -> None:
    w = h = 32
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, PANEL_DK)
    fill(img, 1, 1, w - 1, h - 1, PANEL)
    fill(img, 2, 2, w - 2, 4, GOLD_TRIM)
    fill(img, 2, 4, w - 2, 6, PANEL_HI)
    fill(img, 2, h - 3, w - 2, h - 1, PANEL_DK)
    rect(img, 0, 0, w, h, BRASS_DK)
    save("panel", img, nine(32, 32, {"left": 4, "top": 6, "right": 4, "bottom": 4}))


def gen_button(name: str, fill_c: tuple[int, ...], hi: tuple[int, ...], dk: tuple[int, ...],
               edge: tuple[int, ...]) -> None:
    w, h = 32, 16
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, edge)
    fill(img, 1, 1, w - 1, h - 1, fill_c)
    fill(img, 1, 1, w - 1, 3, hi)
    fill(img, 1, h - 3, w - 1, h - 1, dk)
    save(name, img, nine(32, 16, {"left": 3, "top": 3, "right": 3, "bottom": 3}))


def gen_switch(on: bool) -> None:
    w, h = 22, 12
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    track = TEAL if on else (0x5A, 0x40, 0x28, 255)
    track_dk = TEAL_DK if on else (0x2A, 0x18, 0x0C, 255)
    fill(img, 1, 2, w - 1, h - 2, track_dk)
    fill(img, 2, 3, w - 2, h - 3, track)
    rect(img, 0, 1, w, h - 1, WOOD_LINE)
    knob_x = 11 if on else 1
    fill(img, knob_x, 1, knob_x + 10, 11, (0xF4, 0xE8, 0xC8, 255))
    fill(img, knob_x + 1, 2, knob_x + 9, 10, (0xE8, 0xD4, 0x98, 255) if on else (0xC8, 0xB0, 0x80, 255))
    fill(img, knob_x + 2, 3, knob_x + 6, 6, (255, 255, 255, 180))
    rect(img, knob_x, 1, knob_x + 10, 11, BRASS_DK)
    save("switch_on" if on else "switch_off", img)


def gen_chip() -> None:
    w, h = 32, 14
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill(img, 0, 0, w, h, BRASS)
    fill(img, 1, 1, w - 1, h - 1, (0x4A, 0x2E, 0x16, 255))
    fill(img, 1, 1, w - 1, 3, (0x6A, 0x42, 0x20, 255))
    save("chip", img, nine(32, 14, {"left": 3, "top": 2, "right": 3, "bottom": 2}))


def gen_interior() -> None:
    w, h = 32, 32
    img = Image.new("RGBA", (w, h), (0x2A, 0x18, 0x0C, 255))
    for y in range(h):
        for x in range(w):
            if hash2(x, y, 8) % 13 == 0:
                put(img, x, y, (0x36, 0x20, 0x10, 255))
    save("interior", img, tile(32, 32))


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    gen_frame()
    gen_header()
    gen_cork()
    gen_interior()
    gen_paper("paper", CREAM, CREAM_SHADE, CREAM_EDGE)
    gen_paper("paper_gold", GOLD_PAPER, GOLD_SHADE, GOLD_EDGE)
    gen_paper("paper_pink", PINK_PAPER, PINK_SHADE, PINK_EDGE)
    gen_paper("paper_blend", BLEND_PAPER, GOLD_SHADE, BLEND_EDGE)
    gen_paper("paper_locked", LOCK_PAPER, LOCK_SHADE, LOCK_EDGE)
    gen_pin("pin_red", RED, RED_HI, RED_DK)
    gen_pin("pin_teal", TEAL, (0x6C, 0xD0, 0xC8, 255), TEAL_DK)
    gen_pin("pin_brass", BRASS, BRASS_HI, BRASS_DK)
    gen_pin("pin_pink", PINK, PINK_HI, (0x80, 0x20, 0x48, 255))
    gen_wax()
    gen_ribbon()
    gen_lock()
    gen_stamp()
    gen_progress()
    gen_panel()
    gen_chip()
    gen_button("button", WOOD, WOOD_HI, WOOD_DK, WOOD_LINE)
    gen_button("button_hover", WOOD_HI, (0xC8, 0x88, 0x48, 255), WOOD, BRASS)
    gen_button("button_danger", (0x6A, 0x28, 0x20, 255), RED_HI, RED_DK, RED)
    gen_button("button_danger_hover", RED, RED_HI, RED_DK, (0xF0, 0x80, 0x60, 255))
    gen_switch(True)
    gen_switch(False)
    print(f"wrote cork sprites to {OUT}")


if __name__ == "__main__":
    main()

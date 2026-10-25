#!/usr/bin/env python3
"""Offline preview of CreateGui window assembly (UVs match CreateGuiTextures / Create 6.0.10)."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image

ATLAS = 256
GUI_DIR = Path(os.environ.get("CREATE_GUI_DIR", "/tmp/create-jar-gui/assets/create/textures/gui"))

# (file, u, v, w, h) — mirrors CreateGuiTextures
SLICES = {
    "BRASS_TL": ("value_settings", 65, 9, 4, 4),
    "BRASS_TR": ("value_settings", 70, 9, 4, 4),
    "BRASS_BL": ("value_settings", 65, 19, 4, 4),
    "BRASS_BR": ("value_settings", 70, 19, 4, 4),
    "BRASS_LEFT": ("value_settings", 65, 14, 3, 4),
    "BRASS_RIGHT": ("value_settings", 71, 14, 3, 4),
    "BRASS_TOP": ("value_settings", 0, 24, 256, 3),
    "BRASS_BOTTOM": ("value_settings", 0, 27, 256, 3),
    "PARCHMENT_FILL": ("stock_keeper", 64, 4, 8, 8),
    "BUTTON": ("widgets", 0, 0, 18, 18),
    "BUTTON_GREEN": ("widgets", 72, 0, 18, 18),
    "STOCK_SLOT": ("stock_keeper", 32, 200, 18, 18),
    "TEXTBOX_TOP": ("assemble", 1, 179, 150, 18),
    "TEXTBOX_MIDDLE": ("assemble", 1, 198, 150, 1),
    "TEXTBOX_BOTTOM": ("assemble", 1, 200, 150, 4),
}

_atlases: dict[str, Image.Image] = {}


def atlas(name: str) -> Image.Image:
    if name not in _atlases:
        path = GUI_DIR / f"{name}.png"
        _atlases[name] = Image.open(path).convert("RGBA")
    return _atlases[name]


def crop_slice(key: str) -> Image.Image:
    file, u, v, w, h = SLICES[key]
    return atlas(file).crop((u, v, u + w, v + h))


def tile_region(canvas: Image.Image, patch: Image.Image, x: int, y: int, w: int, h: int) -> None:
    if w <= 0 or h <= 0:
        return
    pw, ph = patch.size
    for yy in range(y, y + h, ph):
        dh = min(ph, y + h - yy)
        for xx in range(x, x + w, pw):
            dw = min(pw, x + w - xx)
            region = patch.crop((0, 0, dw, dh))
            canvas.alpha_composite(region, (xx, yy))


def nine_slice_button(canvas: Image.Image, x: int, y: int, w: int, h: int, key: str = "BUTTON") -> None:
    slice_img = crop_slice(key)
    left, top, right, bottom = 4, 4, 4, 4
    sw, sh = slice_img.size
    mid_w, mid_h = max(1, sw - left - right), max(1, sh - top - bottom)
    tl = slice_img.crop((0, 0, left, top))
    tr = slice_img.crop((sw - right, 0, sw, top))
    bl = slice_img.crop((0, sh - bottom, left, sh))
    br = slice_img.crop((sw - right, sh - bottom, sw, sh))
    canvas.alpha_composite(tl, (x, y))
    canvas.alpha_composite(tr, (x + w - right, y))
    canvas.alpha_composite(bl, (x, y + h - bottom))
    canvas.alpha_composite(br, (x + w - right, y + h - bottom))
    top_mid = slice_img.crop((left, 0, left + mid_w, top))
    bot_mid = slice_img.crop((left, sh - bottom, left + mid_w, sh))
    left_mid = slice_img.crop((0, top, left, top + mid_h))
    right_mid = slice_img.crop((sw - right, top, sw, top + mid_h))
    center = slice_img.crop((left, top, left + mid_w, top + mid_h))
    tile_region(canvas, top_mid, x + left, y, w - left - right, top)
    tile_region(canvas, bot_mid, x + left, y + h - bottom, w - left - right, bottom)
    tile_region(canvas, left_mid, x, y + top, left, h - top - bottom)
    tile_region(canvas, right_mid, x + w - right, y + top, right, h - top - bottom)
    tile_region(canvas, center, x + left, y + top, w - left - right, h - top - bottom)


def draw_brass_frame(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    for key, px, py in [
        ("BRASS_TL", x, y),
        ("BRASS_TR", x + w - 4, y),
        ("BRASS_BL", x, y + h - 4),
        ("BRASS_BR", x + w - 4, y + h - 4),
    ]:
        canvas.alpha_composite(crop_slice(key), (px, py))
    tile_region(canvas, crop_slice("BRASS_TOP"), x + 4, y, max(0, w - 8), 3)
    tile_region(canvas, crop_slice("BRASS_BOTTOM"), x + 4, y + h - 3, max(0, w - 8), 3)
    tile_region(canvas, crop_slice("BRASS_LEFT"), x, y + 4, 3, max(0, h - 8))
    tile_region(canvas, crop_slice("BRASS_RIGHT"), x + w - 3, y + 4, 3, max(0, h - 8))


def draw_window(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    inset = 4
    if w > inset * 2 and h > inset * 2:
        tile_region(canvas, crop_slice("PARCHMENT_FILL"), x + inset, y + inset, w - inset * 2, h - inset * 2)
    draw_brass_frame(canvas, x, y, w, h)


def draw_field(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    top = crop_slice("TEXTBOX_TOP")
    mid = crop_slice("TEXTBOX_MIDDLE")
    bot = crop_slice("TEXTBOX_BOTTOM")
    top_h = min(top.height, max(8, h - 4))
    bot_h = min(bot.height, max(2, h - top_h))
    mid_h = h - top_h - bot_h
    cap = 8
    for strip, sh in [(top, top_h), (mid, mid_h), (bot, bot_h)]:
        if sh <= 0:
            continue
        region = strip.crop((0, 0, strip.width, min(sh, strip.height)))
        # horizontal cap + tile middle
        left = region.crop((0, 0, cap, region.height))
        right = region.crop((region.width - cap, 0, region.width, region.height))
        mid_patch = region.crop((cap, 0, cap + 1, region.height))
        canvas.alpha_composite(left, (x, y))
        canvas.alpha_composite(right, (x + w - cap, y))
        tile_region(canvas, mid_patch, x + cap, y, w - cap * 2, region.height)
        y += sh


def render_board(w: int, h: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 40
    px, py = margin, margin
    pw, ph = w - margin * 2, h - margin * 2
    draw_window(canvas, px, py, pw, ph)
    draw_parchment = crop_slice("PARCHMENT_FILL")
    tile_region(canvas, draw_parchment, px + 6, py + 26, pw - 12, 20)
    tab_w = 70
    for i, green in enumerate([True, False, False]):
        nine_slice_button(canvas, px + 8 + i * (tab_w + 4), py + 6, tab_w, 18, "BUTTON_GREEN" if green else "BUTTON")
    nine_slice_button(canvas, px + pw - 8 - 120, py + 6, 120, 18, "BUTTON")
    list_y = py + 50
    for row in range(4):
        y = list_y + row * 24
        nine_slice_button(canvas, px + pw - 156, y, 68, 18, "BUTTON")
    return canvas


def render_stamp(w: int, h: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 40
    px, py = margin, margin
    pw, ph = w - margin * 2, h - margin * 2
    draw_window(canvas, px, py, pw, ph)
    tile_region(canvas, crop_slice("PARCHMENT_FILL"), px + 8, py + 6, pw - 16, 20)
    for col in range(3):
        draw_field(canvas, px + 8 + col * 100, py + 120, 90, 20)
    for col in range(4):
        draw_field(canvas, px + 8 + col * 72, py + 180, 64, 20)
    for i in range(6):
        canvas.alpha_composite(crop_slice("STOCK_SLOT"), (px + 8 + i * 18, py + 70))
    nine_slice_button(canvas, px + pw - 180, py + ph - 32, 80, 20, "BUTTON_GREEN")
    nine_slice_button(canvas, px + pw - 92, py + ph - 32, 80, 20, "BUTTON")
    return canvas


def main() -> None:
    out = Path(os.environ.get("ARTIFACTS", "/opt/cursor/artifacts"))
    out.mkdir(parents=True, exist_ok=True)
    sizes = [(400, 240, "board"), (480, 300, "board"), (400, 300, "stamp"), (480, 360, "stamp")]
    for width, height, kind in sizes:
        img = render_board(width, height) if kind == "board" else render_stamp(width, height)
        path = out / f"create_gui_{kind}_{width}x{height}.png"
        img.save(path)
        print(path)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Offline preview of board/stamp layouts using generated aerocouriers GUI sprites."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"


def load(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def nine_slice(canvas: Image.Image, patch: Image.Image, x: int, y: int, w: int, h: int, border: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = border
    pw, ph = patch.size
    mid_w, mid_h = max(1, pw - left - right), max(1, ph - top - bottom)

    def blit(sx: int, sy: int, sw: int, sh: int, dx: int, dy: int, dw: int, dh: int) -> None:
        region = patch.crop((sx, sy, sx + sw, sy + sh)).resize((dw, dh), Image.Resampling.NEAREST)
        canvas.alpha_composite(region, (dx, dy))

    blit(0, 0, left, top, x, y, left, top)
    blit(pw - right, 0, right, top, x + w - right, y, right, top)
    blit(0, ph - bottom, left, bottom, x, y + h - bottom, left, bottom)
    blit(pw - right, ph - bottom, right, bottom, x + w - right, y + h - bottom, right, bottom)
    if w > left + right:
        blit(left, 0, mid_w, top, x + left, y, w - left - right, top)
        blit(left, ph - bottom, mid_w, bottom, x + left, y + h - bottom, w - left - right, bottom)
    if h > top + bottom:
        blit(0, top, left, mid_h, x, y + top, left, h - top - bottom)
        blit(pw - right, top, right, mid_h, x + w - right, y + top, right, h - top - bottom)
    if w > left + right and h > top + bottom:
        blit(left, top, mid_w, mid_h, x + left, y + top, w - left - right, h - top - bottom)


def draw_window(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    nine_slice(canvas, load("panel"), x, y, w, h, (4, 6, 4, 4))


def draw_title(canvas: Image.Image, x: int, y: int, w: int) -> None:
    nine_slice(canvas, load("title_label"), x, y, w, 18, (6, 4, 6, 4))


def draw_inset(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    nine_slice(canvas, load("inset"), x, y, w, h, (3, 3, 3, 3))


def draw_button(canvas: Image.Image, x: int, y: int, w: int, h: int, highlighted: bool = False) -> None:
    key = "button_highlighted" if highlighted else "button"
    nine_slice(canvas, load(key), x, y, w, h, (3, 4, 3, 3))


def draw_tab(canvas: Image.Image, x: int, y: int, w: int, selected: bool) -> None:
    patch = load("tab_selected" if selected else "tab")
    dh = 20 if selected else 16
    dy = y if selected else y + 4
    border = (2, 2, 2, 2 if selected else 1)
    nine_slice(canvas, patch, x, dy, w, dh, border)


def render_board(w: int, h: int, tab: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 40
    px, py = margin, margin
    pw, ph = w - margin * 2, h - margin * 2
    draw_window(canvas, px, py, pw, ph)
    draw_title(canvas, px + 6, py + 26, pw - 12)
    tab_w = 70
    for i in range(3):
        draw_tab(canvas, px + 8 + i * (tab_w + 4), py + 6, tab_w, i == tab)
    canvas.alpha_composite(load("icon/envelope"), (px + pw - 28, py + 5))
    draw_inset(canvas, px + 6, py + 50, pw - 12, 96)
    for row in range(4):
        y = py + 52 + row * 24
        draw_button(canvas, px + pw - 156, y, 68, 18)
        draw_button(canvas, px + pw - 84, y, 76, 18, highlighted=row == 0)
    return canvas


def render_stamp(w: int, h: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 40
    px, py = margin, margin
    pw, ph = w - margin * 2, h - margin * 2
    draw_window(canvas, px, py, pw, ph)
    draw_title(canvas, px + 8, py + 6, pw - 16)
    draw_inset(canvas, px + 6, py + 68, 170, 40)
    for i in range(6):
        canvas.alpha_composite(load("container_slot"), (px + 8 + i * 18, py + 70))
    for col in range(3):
        nine_slice(canvas, load("text_field"), px + 8 + col * 100, py + 120, 90, 20, (2, 2, 2, 2))
    for col in range(4):
        nine_slice(canvas, load("text_field"), px + 8 + col * 72, py + 180, 64, 20, (2, 2, 2, 2))
    draw_button(canvas, px + pw - 180, py + ph - 32, 80, 20, highlighted=True)
    draw_button(canvas, px + pw - 92, py + ph - 32, 80, 20)
    return canvas


def main() -> None:
    out = Path(os.environ.get("ARTIFACTS", "/opt/cursor/artifacts"))
    out.mkdir(parents=True, exist_ok=True)
    for width, height in [(400, 240), (480, 300)]:
        for tab in range(3):
            path = out / f"board_tab{tab}_{width}x{height}.png"
            render_board(width, height, tab).save(path)
            print(path)
    for width, height in [(400, 300), (480, 360)]:
        path = out / f"stamp_window_{width}x{height}.png"
        render_stamp(width, height).save(path)
        print(path)


if __name__ == "__main__":
    main()

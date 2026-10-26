#!/usr/bin/env python3
"""Preview stamp/order window as clipboard with sample labels."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"

CLIP_W, CLIP_H, CLIP_OVER = 128, 38, 20
FIELD_H = 20
BOTTOM_MARGIN = 6


def load(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def font(size: int = 12) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


TEXT = (0x3E, 0x28, 0x12, 255)
TEXT_MUTED = (0x6B, 0x45, 0x3E, 255)
TEXT_HINT = (0x8A, 0x74, 0x58, 255)
FIELD_VALUES = ("45", "", "", "10", "0", "30", "8")
BTN_TEXT = (255, 255, 255, 255)


def clamp(value: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, value))


def stamp_panel_geometry(screen_w: int, screen_h: int) -> tuple[int, int, int, int, int, int]:
    """Match StampCreateLayout vertical fit: panel size, left, top, clipY."""
    max_w = max(16, screen_w - 8)
    max_h = max(16, screen_h - 8)
    panel_w = clamp(screen_w - 40, min(360, max_w), min(520, max_w))
    panel_w = min(panel_w, max_w)
    panel_h = clamp(screen_h - 24, min(220, max_h), min(440, max_h))
    panel_h = min(panel_h, max_h)
    max_panel_h = screen_h - 2 * CLIP_OVER - 2 - BOTTOM_MARGIN
    if panel_h > max_panel_h:
        panel_h = max(FIELD_H * 6, max_panel_h)
    left = max(0, (screen_w - panel_w) // 2)
    clip_y = max(CLIP_OVER + 2, (screen_h - panel_h - CLIP_OVER) // 2)
    top = clip_y + CLIP_OVER
    return panel_w, panel_h, left, top, clip_y, clip_y + CLIP_H + 4


def nine_slice(canvas: Image.Image, patch: Image.Image, x: int, y: int, w: int, h: int, border: tuple[int, int, int, int]) -> None:
    left, top_b, right, bottom = border
    pw, ph = patch.size
    mid_w, mid_h = max(1, pw - left - right), max(1, ph - top_b - bottom)

    def blit(sx: int, sy: int, sw: int, sh: int, dx: int, dy: int, dw: int, dh: int) -> None:
        region = patch.crop((sx, sy, sx + sw, sy + sh)).resize((dw, dh), Image.Resampling.NEAREST)
        canvas.alpha_composite(region, (dx, dy))

    blit(0, 0, left, top_b, x, y, left, top_b)
    blit(pw - right, 0, right, top_b, x + w - right, y, right, top_b)
    blit(0, ph - bottom, left, bottom, x, y + h - bottom, left, bottom)
    blit(pw - right, ph - bottom, right, bottom, x + w - right, y + h - bottom, right, bottom)
    if w > left + right:
        blit(left, 0, mid_w, top_b, x + left, y, w - left - right, top_b)
        blit(left, ph - bottom, mid_w, bottom, x + left, y + h - bottom, w - left - right, bottom)
    if h > top_b + bottom:
        blit(0, top_b, left, mid_h, x, y + top_b, left, h - top_b - bottom)
        blit(pw - right, top_b, right, mid_h, x + w - right, y + top_b, right, h - top_b - bottom)
    if w > left + right and h > top_b + bottom:
        blit(left, top_b, mid_w, mid_h, x + left, y + top_b, w - left - right, h - top_b - bottom)


def draw_button(canvas: Image.Image, x: int, y: int, w: int, h: int, label: str, fnt: ImageFont.ImageFont) -> None:
    nine_slice(canvas, load("button"), x, y, w, h, (3, 4, 3, 3))
    d = ImageDraw.Draw(canvas)
    tw = fnt.getlength(label)
    d.text((x + (w - tw) / 2, y + (h - 12) / 2), label, fill=BTN_TEXT, font=fnt)


def render(screen_w: int, screen_h: int) -> Image.Image:
    fnt = font(11)
    fnt_sm = font(9)
    canvas = Image.new("RGBA", (screen_w, screen_h), (32, 32, 48, 255))
    panel_w, panel_h, left, top, clip_y, header_y = stamp_panel_geometry(screen_w, screen_h)
    frame_pad = 6
    paper_l = left + frame_pad
    paper_t = top + frame_pad
    paper_w = panel_w - frame_pad * 2
    paper_h = panel_h - frame_pad * 2
    clip_x = left + (panel_w - CLIP_W) // 2

    nine_slice(canvas, load("panel"), left, top, panel_w, panel_h, (4, 6, 4, 4))
    nine_slice(canvas, load("paper_sheet"), paper_l, paper_t, paper_w, paper_h, (5, 5, 5, 5))
    canvas.alpha_composite(load("clipboard_clip"), (clip_x, clip_y))

    pad = 8
    tx = paper_l + pad
    y = header_y
    d = ImageDraw.Draw(canvas)
    d.text((tx, y), "Создать заказ", fill=TEXT, font=fnt)
    d.text((paper_l + paper_w - pad - 90, y), "Баланс: 200 сп", fill=TEXT_MUTED, font=fnt_sm)
    y += 18
    d.text((tx, y), "Погрузка: 120 64 -340", fill=TEXT, font=fnt_sm)
    d.text((tx + 200, y), "Вес: 12.0", fill=TEXT_MUTED, font=fnt_sm)
    y += 22
    nine_slice(canvas, load("paper_field"), tx, y, paper_w - pad * 2, 38, (2, 2, 2, 2))
    for i in range(6):
        canvas.alpha_composite(load("paper_slot"), (tx + i * 18, y + 10))
    y += 48
    d.text((tx, y), "Доставка", fill=TEXT, font=fnt_sm)
    y += 12
    labels = ["X", "Y", "Z"]
    cw = (paper_w - pad * 2 - 12) // 3
    for i, lab in enumerate(labels):
        cx = tx + i * (cw + 6)
        d.text((cx, y), lab, fill=TEXT_HINT, font=fnt_sm)
        nine_slice(canvas, load("paper_field"), cx, y + 11, cw, 20, (2, 2, 2, 2))
        val = FIELD_VALUES[i]
        if val:
            d.text((cx + 5, y + 15), val, fill=TEXT, font=fnt_sm)
    y += 40
    money = ["Награда", "Залог", "Мин.", "Радиус"]
    mw = (paper_w - pad * 2 - 18) // 4
    for i, lab in enumerate(money):
        mx = tx + i * (mw + 6)
        d.text((mx, y), lab, fill=TEXT_HINT, font=fnt_sm)
        nine_slice(canvas, load("paper_field"), mx, y + 11, mw, 20, (2, 2, 2, 2))
        d.text((mx + 5, y + 15), FIELD_VALUES[3 + i], fill=TEXT, font=fnt_sm)
    y += 44
    d.text((tx, y), "Залог и награда уходят заказчику при провале.", fill=TEXT_MUTED, font=fnt_sm)
    by = paper_t + paper_h - pad - FIELD_H
    draw_button(canvas, tx, by, 80, FIELD_H, "Отмена", fnt_sm)
    draw_button(canvas, paper_l + paper_w - pad - 100, by, 100, FIELD_H, "Создать", fnt_sm)
    return canvas


def main() -> None:
    out = Path(os.environ.get("ARTIFACTS", "/opt/cursor/artifacts"))
    out.mkdir(parents=True, exist_ok=True)
    for w, h in [(400, 300), (480, 360), (427, 240)]:
        path = out / f"stamp_clipboard_{w}x{h}.png"
        render(w, h).save(path)
        print(path)


if __name__ == "__main__":
    main()

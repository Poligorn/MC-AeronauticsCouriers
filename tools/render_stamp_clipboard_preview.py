#!/usr/bin/env python3
"""Preview stamp/order window as clipboard with sample labels."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"

TEXT = (0x3E, 0x28, 0x12, 255)
TEXT_MUTED = (0x6B, 0x45, 0x3E, 255)
BTN_TEXT = (255, 255, 255, 255)


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


def draw_button(canvas: Image.Image, x: int, y: int, w: int, h: int, label: str, fnt: ImageFont.ImageFont) -> None:
    nine_slice(canvas, load("button"), x, y, w, h, (3, 4, 3, 3))
    tw = fnt.getlength(label)
    canvas.alpha_composite(
        Image.new("RGBA", (1, 1)),
        (0, 0),
    )
    d = ImageDraw.Draw(canvas)
    d.text((x + (w - tw) / 2, y + (h - 12) / 2), label, fill=BTN_TEXT, font=fnt)


def render(screen_w: int, screen_h: int) -> Image.Image:
    fnt = font(11)
    fnt_sm = font(9)
    canvas = Image.new("RGBA", (screen_w, screen_h), (32, 32, 48, 255))
    clip_w, clip_h, clip_over = 128, 38, 20
    panel_w, panel_h = min(480, screen_w - 40), min(360, screen_h - 24)
    left = (screen_w - panel_w) // 2
    top = max(clip_over + 6, (screen_h - panel_h) // 2)
    frame_pad = 6
    paper_l = left + frame_pad
    paper_t = top + frame_pad
    paper_w = panel_w - frame_pad * 2
    paper_h = panel_h - frame_pad * 2
    clip_x = left + (panel_w - clip_w) // 2
    clip_y = top - clip_over

    nine_slice(canvas, load("panel"), left, top, panel_w, panel_h, (4, 6, 4, 4))
    nine_slice(canvas, load("paper_sheet"), paper_l, paper_t, paper_w, paper_h, (5, 5, 5, 5))
    canvas.alpha_composite(load("clipboard_clip"), (clip_x, clip_y))

    pad = 8
    tx = paper_l + pad
    y = clip_y + clip_h + 4
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
        d.text((cx, y), lab, fill=TEXT_MUTED, font=fnt_sm)
        nine_slice(canvas, load("paper_field"), cx, y + 11, cw, 20, (2, 2, 2, 2))
    y += 40
    money = ["Награда", "Залог", "Мин.", "Радиус"]
    mw = (paper_w - pad * 2 - 18) // 4
    for i, lab in enumerate(money):
        mx = tx + i * (mw + 6)
        d.text((mx, y), lab, fill=TEXT_MUTED, font=fnt_sm)
        nine_slice(canvas, load("paper_field"), mx, y + 11, mw, 20, (2, 2, 2, 2))
    y += 44
    d.text((tx, y), "Залог и награда уходят заказчику при провале.", fill=TEXT_MUTED, font=fnt_sm)
    by = paper_t + paper_h - pad - 20
    draw_button(canvas, tx, by, 80, 20, "Отмена", fnt_sm)
    draw_button(canvas, paper_l + paper_w - pad - 100, by, 100, 20, "Создать", fnt_sm)
    return canvas


def main() -> None:
    out = Path(os.environ.get("ARTIFACTS", "/opt/cursor/artifacts"))
    out.mkdir(parents=True, exist_ok=True)
    for w, h in [(400, 300), (480, 360)]:
        path = out / f"stamp_clipboard_{w}x{h}.png"
        render(w, h).save(path)
        print(path)


if __name__ == "__main__":
    main()

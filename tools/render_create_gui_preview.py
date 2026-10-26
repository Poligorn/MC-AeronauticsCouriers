#!/usr/bin/env python3
"""Offline preview of board (tablet monitor) and stamp layouts using generated sprites."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"

FRAME_BORDER = 6
ANTENNA_OVERHANG_TOP = 16
GEAR_OVERHANG_LEFT = 12
ANTENNA_W, ANTENNA_H = 28, 22
GEAR_W, GEAR_H = 22, 36

# Tablet text palette (ARGB for PIL fill on separate overlay — we draw hex colors)
C_HEADER = (0xFF, 0x7A, 0x2F)
C_MAIN = (0xFF, 0xAF, 0x55)
C_MUTED = (0xC8, 0x7A, 0x3A)
C_WARN = (0xFF, 0x9E, 0x00)


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


def frame_pos(screen_w: int, screen_h: int, frame_w: int, frame_h: int) -> tuple[int, int]:
    total_w = frame_w + GEAR_OVERHANG_LEFT
    total_h = frame_h + ANTENNA_OVERHANG_TOP
    fx = max(GEAR_OVERHANG_LEFT + 2, (screen_w - total_w) // 2 + GEAR_OVERHANG_LEFT)
    fy = max(ANTENNA_OVERHANG_TOP + 2, (screen_h - total_h) // 2 + ANTENNA_OVERHANG_TOP)
    return fx, fy


def draw_monitor(canvas: Image.Image, fx: int, fy: int, fw: int, fh: int) -> tuple[int, int, int, int]:
    gear_x = fx - GEAR_OVERHANG_LEFT
    gear_y = fy + (fh - GEAR_H) // 2
    canvas.alpha_composite(load("tablet_decor_gear"), (gear_x, gear_y))
    nine_slice(canvas, load("tablet_frame"), fx, fy, fw, fh, (6, 6, 6, 6))
    sx, sy = fx + FRAME_BORDER, fy + FRAME_BORDER
    sw, sh = fw - FRAME_BORDER * 2, fh - FRAME_BORDER * 2
    grid = load("tablet_grid")
    gw, gh = grid.size
    for gy in range(sy, sy + sh, gh):
        for gx in range(sx, sx + sw, gw):
            tw = min(gw, sx + sw - gx)
            th = min(gh, sy + sh - gy)
            canvas.alpha_composite(grid.crop((0, 0, tw, th)), (gx, gy))
    glow = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.rectangle((0, 0, sw, 2), fill=(255, 122, 47, 51))
    gd.rectangle((0, sh - 2, sw, sh), fill=(255, 122, 47, 51))
    gd.rectangle((0, 0, 2, sh), fill=(255, 122, 47, 51))
    gd.rectangle((sw - 2, 0, sw, sh), fill=(255, 122, 47, 51))
    canvas.alpha_composite(glow, (sx, sy))
    for y in range(sy + 3, sy + sh, 4):
        ImageDraw.Draw(canvas).line([(sx, y), (sx + sw, y)], fill=(0, 0, 0, 24))
    canvas.alpha_composite(load("tablet_decor_antenna"), (fx + 4, fy - ANTENNA_OVERHANG_TOP))
    return sx, sy, sw, sh


def draw_tablet_button(canvas: Image.Image, x: int, y: int, w: int, h: int, highlighted: bool = False) -> None:
    key = "tablet_button_highlighted" if highlighted else "tablet_button"
    nine_slice(canvas, load(key), x, y, w, h, (2, 2, 2, 2))


def draw_tablet_tab(canvas: Image.Image, x: int, y: int, w: int, selected: bool) -> None:
    patch = load("tablet_tab_selected" if selected else "tablet_tab")
    dh = 20 if selected else 16
    dy = y if selected else y + 4
    nine_slice(canvas, patch, x, dy, w, dh, (2, 2, 2, 2 if selected else 1))


def draw_list_panel(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    ImageDraw.Draw(canvas).rectangle((x, y, x + w, y + h), fill=(44, 9, 0, 204), outline=(179, 104, 51))


def draw_row(canvas: Image.Image, x: int, y: int, w: int, h: int, selected: bool = False) -> None:
    nine_slice(canvas, load("tablet_list_row"), x, y, w, h, (2, 2, 2, 2))
    if selected:
        ImageDraw.Draw(canvas).rectangle((x, y, x + w, y + h), outline=(255, 175, 85))


def try_font(size: int = 8) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def render_board(w: int, h: int, tab: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 8
    fw = min(int(w * 0.72), w - margin * 2 - GEAR_OVERHANG_LEFT)
    fh = min(int(h * 0.80), h - margin * 2 - ANTENNA_OVERHANG_TOP)
    fw = max(320, fw)
    fh = max(220, fh)
    fx, fy = frame_pos(w, h, fw, fh)
    sx, sy, sw, sh = draw_monitor(canvas, fx, fy, fw, fh)
    font = try_font(8)
    font_sm = try_font(7)
    d = ImageDraw.Draw(canvas)

    tab_w = 70
    tabs = ["Заказы", "Мои", "Тест"]
    for i, label in enumerate(tabs):
        draw_tablet_tab(canvas, sx + 4 + i * (tab_w + 4), sy + 2, tab_w, i == tab)
        color = C_MAIN if i == tab else C_MUTED
        tw = d.textlength(label, font=font)
        d.text((sx + 4 + i * (tab_w + 4) + (tab_w - tw) / 2, sy + 8), label, fill=color, font=font)

    canvas.alpha_composite(load("icon/envelope_tablet"), (sx + sw - 24, sy + 2))
    d.text((sx + 6, sy + 22), "Доска заказов", fill=C_HEADER, font=font)
    bal = "Баланс: 1 247 сп"
    d.text((sx + sw - 6 - d.textlength(bal, font=font), sy + 22), bal, fill=C_MUTED, font=font)

    if tab == 0:
        draw_list_panel(canvas, sx + 4, sy + 44, sw - 8, 96)
        rows = [
            ("#a3f2 · Станция «Север» → «Южный причал» · 842 б", "открыт · 120 сп · залог 40 · 18 мин · 12 кг · S · курьер"),
            ("#b91c · Деревня Мокрая → Аэродром · 1 204 б", "открыт · 85 сп · залог 30 · 25 мин · 8 кг · S · заказчик"),
            ("#c44e · Склад → Станция «Восток» · 310 б", "открыт · 200 сп · залог 80 · 40 мин · 24 кг · M · курьер"),
        ]
        for i, (main, meta) in enumerate(rows):
            y = sy + 46 + i * 24
            draw_row(canvas, sx + 6, y, sw - 164, 22, selected=i == 0)
            d.text((sx + 10, y + 4), main[: int((sw - 180) / 5)], fill=C_MAIN, font=font_sm)
            d.text((sx + 10, y + 13), meta, fill=C_MUTED, font=font_sm)
            draw_tablet_button(canvas, sx + sw - 156, y + 2, 68, 18)
            d.text((sx + sw - 148, y + 6), "Подробнее", fill=C_MAIN, font=font_sm)
            draw_tablet_button(canvas, sx + sw - 84, y + 2, 76, 18, highlighted=i == 0)
            d.text((sx + sw - 78, y + 6), "Принять", fill=C_WARN if i == 0 else C_MAIN, font=font_sm)
        d.text((sx + 8, sy + sh - 68), "Ранг II · срок 30 мин · груз: 8× булыжник", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, sy + sh - 56), "При провале залог 40 сп и награда 120 сп уходят заказчику.", fill=C_WARN, font=font_sm)
    elif tab == 1:
        draw_list_panel(canvas, sx + 4, sy + 44, sw - 8, 72)
        draw_row(canvas, sx + 6, sy + 46, sw - 164, 22, selected=True)
        d.text((sx + 10, sy + 50), "#d77a · везу я · Станция → Бухта · 520 б", fill=C_MAIN, font=font_sm)
        d.text((sx + 10, sy + 59), "в пути · 90 сп · залог 20 · 12 мин · 6 кг · S", fill=C_MUTED, font=font_sm)
        draw_tablet_button(canvas, sx + sw - 156, sy + 48, 68, 18)
        draw_tablet_button(canvas, sx + sw - 84, sy + 48, 76, 18)
        d.text((sx + sw - 72, sy + 52), "Отказ", fill=C_MAIN, font=font_sm)
    else:
        draw_tablet_button(canvas, sx + 8, sy + 48, 220, 20)
        d.text((sx + 16, sy + 52), "Выдать лицензию (ранг I)", fill=C_MAIN, font=font_sm)
        draw_tablet_button(canvas, sx + 8, sy + 72, 220, 20)
        d.text((sx + 16, sy + 76), "+500 сп на счёт", fill=C_MAIN, font=font_sm)
        draw_tablet_button(canvas, sx + 8, sy + 96, 220, 20)
        d.text((sx + 16, sy + 100), "Выдать штамп курьера", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, sy + 124), "Выбран: #a3f2", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, sy + 170), "Сначала «Подробнее» у заказа, затем forcespawn / forcedeliver.", fill=C_MUTED, font=font_sm)

    return canvas


def draw_window(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    nine_slice(canvas, load("panel"), x, y, w, h, (4, 6, 4, 4))


def render_stamp(w: int, h: int) -> Image.Image:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 40
    px, py = margin, margin
    pw, ph = w - margin * 2, h - margin * 2
    draw_window(canvas, px, py, pw, ph)
    nine_slice(canvas, load("title_label"), px + 8, py + 6, pw - 16, 18, (6, 4, 6, 4))
    nine_slice(canvas, load("inset"), px + 6, py + 68, 170, 40, (3, 3, 3, 3))
    for i in range(6):
        canvas.alpha_composite(load("container_slot"), (px + 8 + i * 18, py + 70))
    for col in range(3):
        nine_slice(canvas, load("text_field"), px + 8 + col * 100, py + 120, 90, 20, (2, 2, 2, 2))
    for col in range(4):
        nine_slice(canvas, load("text_field"), px + 8 + col * 72, py + 180, 64, 20, (2, 2, 2, 2))
    nine_slice(canvas, load("button"), px + pw - 180, py + ph - 32, 80, 20, (3, 4, 3, 3))
    nine_slice(canvas, load("button"), px + pw - 92, py + ph - 32, 80, 20, (3, 4, 3, 3))
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

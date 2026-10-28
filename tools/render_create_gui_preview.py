#!/usr/bin/env python3
"""Offline preview of board (tablet monitor) and stamp layouts using generated sprites."""

from __future__ import annotations

import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"

FRAME_BORDER = 30
CONTENT_INSET = 14
ANTENNA_W, ANTENNA_H = 24, 84
ANTENNA_BASE_H = FRAME_BORDER
ROW_ACTION_RESERVE = 158
GRID_STEP = 9
SCREEN_FILL = (0x0A, 0x02, 0x00)
GRID_LINE = (0x24, 0x10, 0x06)

C_MAIN = (0xFF, 0xAF, 0x55)
C_BRIGHT = (0xFF, 0xD2, 0x5D)
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


def nine_slice_hollow(canvas: Image.Image, patch: Image.Image, x: int, y: int, w: int, h: int,
                        border: tuple[int, int, int, int]) -> None:
    """Brass bezel only — never stretch the (transparent) center over the screen."""
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


def frame_pos(screen_w: int, screen_h: int, frame_w: int, frame_h: int) -> tuple[int, int]:
    over = ANTENNA_H - ANTENNA_BASE_H
    fx = max(2, (screen_w - frame_w) // 2)
    block_top = max(2, (screen_h - frame_h - over) // 2)
    fy = block_top + over
    return fx, fy


def draw_screen_background(canvas: Image.Image, x: int, y: int, w: int, h: int) -> None:
    d = ImageDraw.Draw(canvas)
    d.rectangle((x, y, x + w, y + h), fill=SCREEN_FILL)
    for gx in range(x, x + w, GRID_STEP):
        d.line([(gx, y), (gx, y + h)], fill=GRID_LINE, width=1)
    for gy in range(y, y + h, GRID_STEP):
        d.line([(x, gy), (x + w, gy)], fill=GRID_LINE, width=1)


def draw_monitor(canvas: Image.Image, fx: int, fy: int, fw: int, fh: int) -> tuple[int, int, int, int]:
    sx, sy = fx + FRAME_BORDER, fy + FRAME_BORDER
    sw, sh = fw - FRAME_BORDER * 2, fh - FRAME_BORDER * 2
    draw_screen_background(canvas, sx, sy, sw, sh)
    nine_slice_hollow(canvas, load("tablet_frame"), fx, fy, fw, fh, (FRAME_BORDER, FRAME_BORDER, FRAME_BORDER, FRAME_BORDER))
    ax = fx + FRAME_BORDER // 2
    ay = fy - (ANTENNA_H - ANTENNA_BASE_H)
    canvas.alpha_composite(load("tablet_decor_antenna").resize((ANTENNA_W, ANTENNA_H), Image.Resampling.NEAREST), (ax, ay))
    return sx, sy, sw, sh


def pixel_in_dark_range(rgb: tuple[int, int, int]) -> bool:
    r, g, b = rgb
    return 0x08 <= r <= 0x28 and 0x00 <= g <= 0x14 and 0x00 <= b <= 0x0A


def verify_empty_screen_darkness(img: Image.Image, sx: int, sy: int, sw: int, sh: int) -> list[tuple[tuple[int, int], tuple[int, int, int], bool]]:
    """Sample empty screen pockets (below list, above bottom hints)."""
    im = img.convert("RGB")
    y0 = sy + 44 + 96 + 6
    y1 = sy + sh - 78
    points = [
        (sx + sw // 5, y0 + 8),
        (sx + sw // 3, y0 + 20),
        (sx + sw // 2, y0 + 12),
        (sx + 2 * sw // 3, y0 + 24),
        (sx + 4 * sw // 5, y0 + 16),
        (sx + sw // 4, (y0 + y1) // 2),
        (sx + sw // 2, (y0 + y1) // 2 + 6),
        (sx + 3 * sw // 4, (y0 + y1) // 2 - 4),
        (sx + 40, y1 - 12),
        (sx + sw - 48, y1 - 8),
    ]
    results = []
    for pt in points:
        rgb = im.getpixel(pt)
        results.append((pt, rgb, pixel_in_dark_range(rgb)))
    return results


def ellipsize(d: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_w: float) -> str:
    if d.textlength(text, font=font) <= max_w:
        return text
    ell = "…"
    while text and d.textlength(text + ell, font=font) > max_w:
        text = text[:-1]
    return text + ell if text else ell


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
    d = ImageDraw.Draw(canvas)
    d.rectangle((x, y, x + w, y + h), fill=(0x3E, 0x26, 0x14), outline=(0xB3, 0x68, 0x33))
    if selected:
        d.rectangle((x, y, x + w, y + h), outline=(255, 175, 85))


def try_font(size: int = 8) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ):
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def render_board(w: int, h: int, tab: int) -> tuple[Image.Image, tuple[int, int, int, int]]:
    canvas = Image.new("RGBA", (w, h), (32, 32, 48, 255))
    margin = 8
    over = ANTENNA_H - ANTENNA_BASE_H
    fw = min(int(w * 0.72), w - margin * 2)
    fh = min(int(h * 0.80), h - margin * 2 - over)
    fw = max(320, fw)
    fh = max(220, fh)
    fx, fy = frame_pos(w, h, fw, fh)
    cavity_x, cavity_y, cavity_w, cavity_h = draw_monitor(canvas, fx, fy, fw, fh)
    sx = cavity_x + CONTENT_INSET
    sy = cavity_y + CONTENT_INSET
    sw = cavity_w - CONTENT_INSET * 2
    sh = cavity_h - CONTENT_INSET * 2
    font = try_font(8)
    font_sm = try_font(7)
    d = ImageDraw.Draw(canvas)

    tab_w = 64
    tab_y = 2
    tab_h = 20
    heading_gap = 7
    heading_y = sy + tab_y + tab_h + heading_gap
    list_top = heading_y + 10
    bottom_pad = 12
    scroll_h = 12
    content_bottom = sy + sh - bottom_pad
    headings = ["Доска заказов", "Мои заказы", "Профиль курьера", "Тестовые заказы"]
    tabs = ["Заказы", "Мои", "Профиль", "Тест"]
    tab_x = sx
    for i, label in enumerate(tabs):
        draw_tablet_tab(canvas, tab_x, sy + tab_y, tab_w, i == tab)
        color = C_BRIGHT if i == tab else C_MUTED
        tw = d.textlength(label, font=font)
        d.text((tab_x + (tab_w - tw) / 2, sy + 8), label, fill=color, font=font)
        tab_x += tab_w + 4
    bal = "Баланс: 1 247 сп"
    mail_x = sx + sw - 24
    bal_right = mail_x - 8
    bal_w = d.textlength(bal, font=font)
    bal_left = tab_x + 4
    bal_x = max(bal_left, bal_right - bal_w)
    d.text((bal_x, sy + 8), ellipsize(d, bal, font, bal_right - bal_left), fill=C_MAIN, font=font)
    canvas.alpha_composite(load("icon/envelope_tablet"), (mail_x, sy + tab_y))
    d.text((sx, heading_y), headings[tab], fill=C_MAIN, font=font)

    def visible_rows(scroll_counter: bool) -> int:
        bottom = content_bottom - 4
        if scroll_counter:
            bottom -= scroll_h
        return max(3, (bottom - list_top) // 24)

    text_left = sx + 8
    text_max = sw - ROW_ACTION_RESERVE - 8

    if tab == 0:
        vis = visible_rows(scroll_counter=False)
        list_h = vis * 24
        draw_list_panel(canvas, sx + 4, list_top - 2, sw - 8, list_h + 2)
        rows = [
            ("#a3f2 · Станция «Север» → «Южный причал» · 842 б", "открыт · 120 сп · залог 40 · 18 мин · 12 кг · S · курьер"),
            ("#b91c · Деревня Мокрая → Аэродром · 1 204 б", "открыт · 85 сп · залог 30 · 25 мин · 8 кг · S · заказчик"),
            ("#c44e · Склад → Станция «Восток» · 310 б", "открыт · 200 сп · залог 80 · 40 мин · 24 кг · M · курьер"),
        ]
        for i, (main, meta) in enumerate(rows[:vis]):
            y = list_top + i * 24
            draw_row(canvas, sx + 6, y - 1, sw - ROW_ACTION_RESERVE - 6, 22, selected=i == 0)
            d.text((text_left, y + 3), ellipsize(d, main, font_sm, text_max), fill=C_MAIN, font=font_sm)
            d.text((text_left, y + 12), ellipsize(d, meta, font_sm, text_max), fill=C_MUTED, font=font_sm)
            draw_tablet_button(canvas, sx + sw - 84, y + 1, 76, 18, highlighted=i == 0)
            d.text((sx + sw - 78, y + 5), "Принять", fill=C_WARN if i == 0 else C_MAIN, font=font_sm)
    elif tab == 1:
        vis = visible_rows(scroll_counter=True)
        list_h = vis * 24
        draw_list_panel(canvas, sx + 4, list_top - 2, sw - 8, list_h + 2)
        for i in range(min(vis, 8)):
            y = list_top + i * 24
            main = f"#mine{i:02d} · заказ {i} · Станция → Бухта · 520 б"
            meta = "в пути · 90 сп · залог 20 · 12 мин · 6 кг · S"
            draw_row(canvas, sx + 6, y - 1, sw - ROW_ACTION_RESERVE - 6, 22, selected=i == 0)
            d.text((text_left, y + 3), ellipsize(d, main, font_sm, text_max), fill=C_MAIN, font=font_sm)
            d.text((text_left, y + 12), ellipsize(d, meta, font_sm, text_max), fill=C_MUTED, font=font_sm)
            if i == 0:
                draw_tablet_button(canvas, sx + sw - 84, y + 1, 76, 18)
                d.text((sx + sw - 72, y + 5), "Отказ", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, content_bottom - scroll_h), "10–14 / 14", fill=C_MUTED, font=font_sm)
    elif tab == 2:
        top = list_top
        bottom = content_bottom
        box_h = max(48, bottom - top)
        inner_w = sw - 16
        box_w = max(96, inner_w * 42 // 100)
        box_x = sx + 8
        text_x = box_x + box_w + 12
        stats_w = max(48, sx + sw - 8 - text_x)
        draw_list_panel(canvas, box_x, top, box_w, box_h)
        cx = box_x + box_w // 2
        sil_h = int(box_h * 0.82)
        sil_w = int(sil_h * 0.35)
        foot = top + int(box_h * (1.0 - 0.06))
        sil_h = int(box_h * 0.85)
        head = foot - sil_h
        d.ellipse((cx - sil_w // 4, head, cx + sil_w // 4, head + sil_h // 5), fill=(0x87, 0x3B, 0x00))
        d.rectangle((cx - sil_w // 2, head + sil_h // 5, cx + sil_w // 2, foot), fill=(0x6B, 0x2E, 0x00))
        d.rectangle((cx - sil_w // 2 - 4, head + sil_h // 3, cx - sil_w // 4, foot - 8), fill=(0x5A, 0x28, 0x00))
        d.rectangle((cx + sil_w // 4, head + sil_h // 3, cx + sil_w // 2 + 4, foot - 8), fill=(0x5A, 0x28, 0x00))
        ty = top + 4
        d.text((text_x, ty), "Tester2", fill=C_BRIGHT, font=font)
        ty += 14
        d.rectangle((text_x, ty, text_x + 14, ty + 14), outline=(0xB3, 0x68, 0x33))
        d.text((text_x + 20, ty + 4), "Ранг: I Стажёр", fill=C_MAIN, font=font_sm)
        ty += 22
        for line in [
            "Успешных заказов: 3",
            "Проваленных: 1",
            "Ср. награда (успешн.): 0 сп",
            "Средняя дистанция: 62 б",
            "Успешность: 75%",
        ]:
            shown = line
            if d.textlength(shown, font=font_sm) > stats_w:
                while shown and d.textlength(shown + "…", font=font_sm) > stats_w:
                    shown = shown[:-1]
                shown = shown + "…"
            d.text((text_x, ty), shown, fill=C_MAIN, font=font_sm)
            ty += 11
    else:
        y = list_top
        draw_tablet_button(canvas, sx + 8, y, 220, 20)
        d.text((sx + 16, y + 4), "Выдать лицензию (ранг I)", fill=C_MAIN, font=font_sm)
        draw_tablet_button(canvas, sx + 8, y + 24, 220, 20)
        d.text((sx + 16, y + 28), "+500 сп на счёт", fill=C_MAIN, font=font_sm)
        draw_tablet_button(canvas, sx + 8, y + 48, 220, 20)
        d.text((sx + 16, y + 52), "Выдать штамп курьера", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, y + 72), "Выбран: #a3f2", fill=C_MAIN, font=font_sm)
        d.text((sx + 8, y + 118), "Сначала «Подробнее» у заказа, затем forcespawn / forcedeliver.", fill=C_MUTED, font=font_sm)

    return canvas, (cavity_x, cavity_y, cavity_w, cavity_h)


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
    for width, height in [(400, 240), (480, 300), (560, 400)]:
        for tab in range(4):
            path = out / f"board_tab{tab}_{width}x{height}.png"
            img, screen = render_board(width, height, tab)
            img.save(path)
            print(path)
        profile_path = out / f"board_profile_{width}x{height}.png"
        img, _ = render_board(width, height, 2)
        img.save(profile_path)
        print(profile_path)
        if width == 480 and height == 300:
            img, screen = render_board(width, height, 0)
            sx, sy, sw, sh = screen
            print("--- pixel verify board_tab0_480x300 (empty screen) ---")
            for pt, rgb, ok in verify_empty_screen_darkness(img, sx, sy, sw, sh):
                print(f"  {pt} #{rgb[0]:02X}{rgb[1]:02X}{rgb[2]:02X} {'OK' if ok else 'FAIL'}")
            print("--- pixel verify board_profile_480x300 (profile labels) ---")
            img_p, screen_p = render_board(width, height, 2)
            sx, sy, sw, sh = screen_p
            d = ImageDraw.Draw(img_p)
            sample = img_p.getpixel((sx + sw // 2 + 20, sy + 50))
            print(f"  profile text sample #{sample[0]:02X}{sample[1]:02X}{sample[2]:02X} (expect orange)")
    for width, height in [(400, 300), (480, 360)]:
        path = out / f"stamp_window_{width}x{height}.png"
        render_stamp(width, height).save(path)
        print(path)


if __name__ == "__main__":
    main()

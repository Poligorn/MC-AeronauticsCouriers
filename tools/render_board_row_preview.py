#!/usr/bin/env python3
"""
Offline board-row previews aligned with BoardRowMetrics / BoardRowLayout (Java).
Regenerate after changing row geometry in BoardRowMetrics.java.
"""

from __future__ import annotations

import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
METRICS_JAVA = ROOT / "src/main/java/dev/poligorn/aerocouriers/client/board/BoardRowMetrics.java"
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"
OUT = Path("/opt/cursor/artifacts")

BG = (0x2C, 0x09, 0x00)
ROW = (0x3E, 0x26, 0x14)
OUTLINE = (0xB3, 0x68, 0x33)
TEXT = (0xFF, 0xAF, 0x55)
MUTED = (0xC8, 0x7A, 0x3A)

ICON = 18
GAP = 2
DETAILS_MAX = 72
DETAILS_MIN = 40
DETAILS_PAD = 10
SECONDARY_W = 76
LIST_PAD_RIGHT = 8


def load_metrics() -> dict[str, int]:
    text = METRICS_JAVA.read_text(encoding="utf-8")
    out: dict[str, int] = {}
    for name in (
        "PAD_TOP",
        "PAD_BOTTOM",
        "LINE1_HEIGHT",
        "LINE_GAP",
        "LINE2_ICON",
        "ICON_TEXT_GAP",
        "PAIR_GAP",
        "ID_STATUS_GAP",
        "BUTTON_HEIGHT",
    ):
        m = re.search(rf"public static final int {name} = (\d+);", text)
        if m:
            out[name] = int(m.group(1))
    return out


def row_height(m: dict[str, int]) -> int:
    return m["PAD_TOP"] + m["LINE1_HEIGHT"] + m["LINE_GAP"] + m["LINE2_ICON"] + m["PAD_BOTTOM"]


def clip(font: ImageFont.FreeTypeFont | ImageFont.ImageFont, raw: str, width: int) -> str:
    if width <= 0:
        return ""
    shown = raw
    while shown and font.getlength(shown) > width:
        shown = shown[:-1]
    if shown != raw:
        while font.getlength(shown + "…") > width and shown:
            shown = shown[:-1]
        shown = shown + "…"
    return shown


def layout_for_row(font, sw: int, details_label: str, has_secondary: bool) -> dict:
    right = sw - LIST_PAD_RIGHT
    secondary_x = right - SECONDARY_W
    details_right = secondary_x - GAP if has_secondary else right
    text_room = DETAILS_MAX - DETAILS_PAD
    shown = clip(font, details_label, text_room)
    details_w = min(DETAILS_MAX, max(DETAILS_MIN, int(font.getlength(shown)) + DETAILS_PAD))
    if int(font.getlength(shown)) + DETAILS_PAD > details_w:
        shown = clip(font, details_label, details_w - DETAILS_PAD)
        details_w = max(DETAILS_MIN, int(font.getlength(shown)) + DETAILS_PAD)
    details_x = details_right - details_w
    crate_x = details_x - GAP - ICON
    map_x = crate_x - GAP - ICON
    text_right = map_x - 4
    return {
        "details_x": details_x,
        "details_w": details_w,
        "details_shown": shown,
        "map_x": map_x,
        "crate_x": crate_x,
        "secondary_x": secondary_x,
        "text_right": text_right,
    }


def load_icon(stem: str) -> Image.Image:
    return Image.open(SPRITES / "icon" / f"{stem}.png").convert("RGBA")


def draw_stat(draw: ImageDraw.ImageDraw, base: Image.Image, icons: dict, key: str, value: str, x: int, y: int, m: dict) -> int:
    ic = icons[key]
    base.paste(ic, (x, y), ic)
    text_x = x + m["LINE2_ICON"] + m["ICON_TEXT_GAP"]
    draw.text((text_x, y), value, fill=MUTED)
    return text_x + int(draw.textlength(value)) + m["PAIR_GAP"]


def draw_row(
    base: Image.Image,
    draw: ImageDraw.ImageDraw,
    font,
    m: dict,
    y: int,
    sw: int,
    line1: str,
    id_part: str,
    status: str,
    customer: str,
    reward: str,
    collateral: str,
    time: str,
    my_order: bool,
    icons: dict[str, Image.Image],
    details_label: str,
    has_secondary: bool,
) -> None:
    rh = row_height(m)
    lay = layout_for_row(font, sw, details_label, has_secondary)
    draw.rectangle((8, y, sw - 8, y + rh), fill=ROW, outline=OUTLINE)

    line1_y = y + m["PAD_TOP"]
    line2_y = y + m["PAD_TOP"] + m["LINE1_HEIGHT"] + m["LINE_GAP"]
    btn_y = y + (rh - m["BUTTON_HEIGHT"]) // 2

    x = 14
    draw.text((x, line1_y), id_part, fill=TEXT)
    x += int(draw.textlength(id_part)) + m["ID_STATUS_GAP"]
    status_shown = clip(font, status, 88)
    draw.text((x, line1_y), status_shown, fill=MUTED)
    x += int(draw.textlength(status_shown)) + 4
    base.paste(icons["village_tablet"], (x, line1_y), icons["village_tablet"])
    x += m["LINE2_ICON"] + 3
    name_w = max(8, lay["text_right"] - x)
    name_shown = clip(font, customer, name_w)
    draw.text((x, line1_y), name_shown, fill=TEXT)

    stat_x = 14
    stat_x = draw_stat(draw, base, icons, "coin_tablet", reward, stat_x, line2_y, m)
    stat_x = draw_stat(draw, base, icons, "lock_tablet", collateral, stat_x, line2_y, m)
    stat_x = draw_stat(draw, base, icons, "clock_tablet", time, stat_x, line2_y, m)
    if my_order:
        base.paste(icons["star_badge_tablet"], (stat_x + 2, line2_y), icons["star_badge_tablet"])

    for key, bx in (("map_pin_tablet", lay["map_x"]), ("crate_tablet", lay["crate_x"])):
        base.paste(icons[key], (bx, btn_y), icons[key])
    dx, dw = lay["details_x"], lay["details_w"]
    draw.rectangle((dx, btn_y, dx + dw, btn_y + m["BUTTON_HEIGHT"]), outline=OUTLINE)
    inner = dw - DETAILS_PAD
    label = clip(font, lay["details_shown"], inner)
    draw.text((dx + 5, btn_y + 5), label, fill=TEXT)


def render(filename: str, stretch: float, details_label: str) -> None:
    m = load_metrics()
    rh = row_height(m)
    width, height = 720, 20 + rh * 2 + 16
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    except OSError:
        font = ImageFont.load_default()

    icons = {name: load_icon(name) for name in (
        "coin_tablet", "lock_tablet", "clock_tablet", "star_badge_tablet",
        "map_pin_tablet", "crate_tablet", "village_tablet",
    )}

    customer = "Александр" if stretch <= 1.0 else "Александр-Константинопольский"
    long_details = details_label if stretch <= 1.0 else "Подробнее…" * 2
    draw_row(
        img, draw, font, m, 16, width - 16, "", "#a3f2", "открыт", customer,
        "10", "0", "28м", stretch <= 1.0, icons, long_details if stretch > 1 else details_label, True,
    )
    draw_row(
        img, draw, font, m, 16 + rh + 8, width - 16, "", "#b91c", "в пути", "Деревня Сосновка",
        "120", "15", "6м", False, icons, details_label, True,
    )
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / filename)


def render_tooltips_demo() -> None:
    m = load_metrics()
    rh = row_height(m)
    width, height = 720, 200
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    except OSError:
        font = ImageFont.load_default()
    icons = {name: load_icon(name) for name in (
        "coin_tablet", "lock_tablet", "clock_tablet", "star_badge_tablet",
        "map_pin_tablet", "crate_tablet", "village_tablet",
    )}
    draw_row(
        img, draw, font, m, 24, width - 16, "", "#a3f2", "принят", "Игрок",
        "10", "0", "28м", False, icons, "Подробнее", True,
    )
    draw.rectangle((420, 8, 700, 78), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.multiline_text(
        (430, 14),
        "Погрузка: 357 70 1469\nДоставка: 405 76 1416\nДистанция: 512 блоков\nНажмите, чтобы открыть карту",
        fill=TEXT,
        spacing=4,
    )
    draw.rectangle((420, 88, 700, 188), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.text((430, 94), "Груз · 3.2 / 256", fill=TEXT)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "board_row_tooltips_preview.png")


if __name__ == "__main__":
    render("board_row_preview_ru_ru.png", 1.0, "Подробнее")
    render("board_row_preview_long_strings.png", 1.4, "Подробнее")
    render_tooltips_demo()
    print(f"Wrote previews to {OUT} (row height {row_height(load_metrics())}px)")

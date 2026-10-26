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

FONT_CANDIDATES = (
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    "/usr/share/fonts/truetype/noto/NotoSans-Regular.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
)


def load_font(size: int = 11) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if Path(path).is_file():
            font = ImageFont.truetype(path, size)
            # Fail fast if Cyrillic is missing
            if font.getbbox("Абв")[2] > 0:
                return font
    raise RuntimeError("No Cyrillic-capable font found; install fonts-dejavu-core")


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
        "CUSTOMER_FACE",
        "FACE_NAME_GAP",
    ):
        m = re.search(rf"public static final int {name} = (\d+);", text)
        if m:
            out[name] = int(m.group(1))
    return out


def row_height(m: dict[str, int]) -> int:
    return m["PAD_TOP"] + m["LINE1_HEIGHT"] + m["LINE_GAP"] + m["LINE2_ICON"] + m["PAD_BOTTOM"]


def text_width(font, text: str) -> int:
    return int(font.getlength(text))


def clip(font, raw: str, width: int) -> str:
    if width <= 0:
        return ""
    shown = raw
    while shown and text_width(font, shown) > width:
        shown = shown[:-1]
    if shown != raw:
        while text_width(font, shown + "…") > width and shown:
            shown = shown[:-1]
        shown = shown + "…"
    return shown


def layout_for_row(sw: int, has_secondary: bool) -> dict:
    right = sw - LIST_PAD_RIGHT
    secondary_x = right - SECONDARY_W
    icons_right = secondary_x - GAP if has_secondary else right
    crate_x = icons_right - ICON
    map_x = crate_x - GAP - ICON
    text_right = map_x - 4
    return {
        "map_x": map_x,
        "crate_x": crate_x,
        "secondary_x": secondary_x,
        "text_right": text_right,
    }


def load_icon(stem: str) -> Image.Image:
    return Image.open(SPRITES / "icon" / f"{stem}.png").convert("RGBA")


def icon_scaled(icons: dict[str, Image.Image], key: str, size: int) -> Image.Image:
    return icons[key].resize((size, size), Image.Resampling.NEAREST)


def draw_stat(
    draw: ImageDraw.ImageDraw,
    base: Image.Image,
    font,
    icons: dict[str, Image.Image],
    key: str,
    value: str,
    x: int,
    y: int,
    m: dict[str, int],
) -> int:
    icon_size = m["LINE2_ICON"]
    ic = icon_scaled(icons, key, icon_size)
    base.paste(ic, (x, y), ic)
    text_x = x + ic.width + m["ICON_TEXT_GAP"]
    text_y = y + max(0, ic.height - 9)
    draw.text((text_x, text_y), value, font=font, fill=MUTED)
    return text_x + text_width(font, value) + m["PAIR_GAP"]


def draw_row(
    base: Image.Image,
    draw: ImageDraw.ImageDraw,
    font,
    m: dict[str, int],
    y: int,
    sw: int,
    id_part: str,
    status: str,
    customer: str,
    reward: str,
    collateral: str,
    time: str,
    my_order: bool,
    icons: dict[str, Image.Image],
    has_secondary: bool,
) -> None:
    rh = row_height(m)
    lay = layout_for_row(sw, has_secondary)
    draw.rectangle((8, y, sw - 8, y + rh), fill=ROW, outline=OUTLINE)

    line1_y = y + m["PAD_TOP"]
    line2_y = y + m["PAD_TOP"] + m["LINE1_HEIGHT"] + m["LINE_GAP"]
    btn_y = y + (rh - m["BUTTON_HEIGHT"]) // 2

    x = 14
    draw.text((x, line1_y), id_part, font=font, fill=TEXT)
    x += text_width(font, id_part) + m["ID_STATUS_GAP"]
    status_shown = clip(font, status, 88)
    draw.text((x, line1_y), status_shown, font=font, fill=MUTED)
    x += text_width(font, status_shown) + 4
    face = icon_scaled(icons, "village_tablet", m["CUSTOMER_FACE"])
    base.paste(face, (x, line1_y), face)
    x += face.width + m["FACE_NAME_GAP"]
    name_w = max(8, lay["text_right"] - x)
    name_shown = clip(font, customer, name_w)
    draw.text((x, line1_y), name_shown, font=font, fill=TEXT)

    stat_x = 14
    stat_x = draw_stat(draw, base, font, icons, "coin_tablet", reward, stat_x, line2_y, m)
    stat_x = draw_stat(draw, base, font, icons, "lock_tablet", collateral, stat_x, line2_y, m)
    stat_x = draw_stat(draw, base, font, icons, "clock_tablet", time, stat_x, line2_y, m)
    if my_order:
        star = icon_scaled(icons, "star_badge_tablet", m["LINE2_ICON"])
        base.paste(star, (stat_x + 2, line2_y), star)

    for key, bx in (("map_pin_tablet", lay["map_x"]), ("crate_tablet", lay["crate_x"])):
        ic = icon_scaled(icons, key, ICON)
        base.paste(ic, (bx, btn_y), ic)
    if has_secondary:
        sx_btn = lay["secondary_x"]
        draw.rectangle((sx_btn, btn_y, sx_btn + SECONDARY_W, btn_y + m["BUTTON_HEIGHT"]), outline=OUTLINE)
        draw.text((sx_btn + 8, btn_y + 5), "Принять", font=font, fill=TEXT)


def render(filename: str, stretch: float) -> None:
    font = load_font(11)
    m = load_metrics()
    rh = row_height(m)
    width, height = 720, 20 + rh * 2 + 16
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)

    icons = {name: load_icon(name) for name in (
        "coin_tablet", "lock_tablet", "clock_tablet", "star_badge_tablet",
        "map_pin_tablet", "crate_tablet", "village_tablet",
    )}

    customer = "Александр" if stretch <= 1.0 else "Александр-Константинопольский"
    draw_row(
        img, draw, font, m, 16, width - 16, "#a3f2", "открыт", customer,
        "10", "0", "28м", stretch <= 1.0, icons, True,
    )
    draw_row(
        img, draw, font, m, 16 + rh + 8, width - 16, "#b91c", "в пути", "Деревня Сосновка",
        "120", "15", "6м", False, icons, True,
    )
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / filename)


def render_tooltips_demo() -> None:
    font = load_font(11)
    m = load_metrics()
    rh = row_height(m)
    width, height = 720, 220
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)
    icons = {name: load_icon(name) for name in (
        "coin_tablet", "lock_tablet", "clock_tablet", "star_badge_tablet",
        "map_pin_tablet", "crate_tablet", "village_tablet",
    )}
    draw_row(
        img, draw, font, m, 24, width - 16, "#a3f2", "принят", "Игрок",
        "10", "0", "28м", False, icons, "Подробнее", True,
    )
    draw.rectangle((400, 8, 700, 78), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.multiline_text(
        (410, 14),
        "Погрузка: 357 70 1469\nДоставка: 405 76 1416\nДистанция: 512 блоков\nНажмите, чтобы открыть карту",
        font=font,
        fill=TEXT,
        spacing=4,
    )
    # Cargo tooltip mock (BoardCargoClientTooltip: 18px slots, 9 cols)
    tx, ty = 400, 88
    tw, th = 300, 120
    draw.rectangle((tx, ty, tx + tw, ty + th), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.text((tx + 8, ty + 6), "Груз", font=font, fill=TEXT)
    slot = 18
    pad = 4
    grid_y = ty + 22
    colors = (
        (0x6B, 0x8E, 0x23), (0x8B, 0x45, 0x13), (0x4A, 0x6F, 0xA5),
        (0x9A, 0x4A, 0x4A), (0x7A, 0x5A, 0xC4), (0xC4, 0xA0, 0x35),
    )
    for i, color in enumerate(colors):
        col = i % 9
        row = i // 9
        sx = tx + pad + col * slot
        sy = grid_y + row * slot
        draw.rectangle((sx, sy, sx + 16, sy + 16), fill=color, outline=OUTLINE)
        draw.text((sx + 4, sy + 2), str((i + 1) * 8), font=font, fill=(255, 255, 255))
    draw.text((tx + pad, grid_y + slot + 2), "3.2 / 256", font=font, fill=TEXT)
    draw.text((tx + pad, grid_y + slot + 16), "+2 ещё", font=font, fill=MUTED)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "board_row_tooltips_preview.png")


if __name__ == "__main__":
    render("board_row_preview_ru_ru.png", 1.0)
    render("board_row_preview_long_strings.png", 1.4)
    render_tooltips_demo()
    print(f"Wrote previews to {OUT} (row height {row_height(load_metrics())}px)")

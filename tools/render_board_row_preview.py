#!/usr/bin/env python3
"""Offline preview of compact board rows (ru + long-string stress test)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"
OUT = Path("/opt/cursor/artifacts")

BG = (0x2C, 0x09, 0x00)
ROW = (0x3E, 0x26, 0x14)
OUTLINE = (0xB3, 0x68, 0x33)
TEXT = (0xFF, 0xAF, 0x55)
MUTED = (0xC8, 0x7A, 0x3A)


def load_icon(stem: str) -> Image.Image:
    return Image.open(SPRITES / "icon" / f"{stem}.png").convert("RGBA")


def draw_row(
    base: Image.Image,
    draw: ImageDraw.ImageDraw,
    y: int,
    width: int,
    line1: str,
    reward: str,
    collateral: str,
    time: str,
    my_order: bool,
    icons: dict[str, Image.Image],
    details_label: str,
) -> None:
    draw.rectangle((8, y, width - 8, y + 22), fill=ROW, outline=OUTLINE)
    draw.text((14, y + 3), line1, fill=TEXT)
    x = 14
    ly = y + 13
    for key, val in (("coin_tablet", reward), ("lock_tablet", collateral), ("clock_tablet", time)):
        ic = icons[key]
        base.paste(ic, (x, ly - 1), ic)
        x += 9
        draw.text((x, ly), val, fill=MUTED)
        x += len(val) * 6 + 8
    if my_order:
        base.paste(icons["star_badge_tablet"], (width - 28, ly - 1), icons["star_badge_tablet"])
    rx = width - 200
    for key in ("map_pin_tablet", "crate_tablet"):
        base.paste(icons[key], (rx, y + 3), icons[key])
        rx += 20
    draw.rectangle((rx, y + 3, rx + 62, y + 19), outline=OUTLINE)
    draw.text((rx + 4, y + 6), details_label, fill=TEXT)


def render(filename: str, stretch: float, details_label: str) -> None:
    width, height = 720, 120
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
        draw.font = font
    except OSError:
        draw.font = ImageFont.load_default()

    names = (
        "coin_tablet",
        "lock_tablet",
        "clock_tablet",
        "star_badge_tablet",
        "map_pin_tablet",
        "crate_tablet",
    )
    icons = {name: load_icon(name) for name in names}

    def s(text: str) -> str:
        if stretch <= 1.0:
            return text
        return text + ("·" * int(4 * stretch))

    customer = "Александр" if stretch <= 1.0 else "Александр-Константинопольский"
    draw_row(
        img,
        draw,
        20,
        width,
        s(f"#a3f2  открыт  [■] {customer}"),
        "10",
        "0",
        s("28м"),
        stretch <= 1.0,
        icons,
        details_label,
    )
    draw_row(
        img,
        draw,
        52,
        width,
        s("#b91c  в пути  [■] Деревня Сосновка"),
        "120",
        "15",
        s("6м"),
        False,
        icons,
        details_label,
    )
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / filename)


def render_tooltips_demo() -> None:
    width, height = 720, 200
    img = Image.new("RGBA", (width, height), BG)
    draw = ImageDraw.Draw(img)
    icons = {name: load_icon(name) for name in ("map_pin_tablet", "crate_tablet", "coin_tablet")}
    draw_row(img, draw, 24, width, "#a3f2  принят  [■] Игрок", "10", "0", "28м", False, icons, "Подробнее")
    draw.rectangle((420, 8, 700, 78), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.multiline_text(
        (430, 14),
        "Погрузка: 357 70 1469\nДоставка: 405 76 1416\nДистанция: 512 блоков\nНажмите, чтобы открыть карту",
        fill=TEXT,
        spacing=4,
    )
    draw.rectangle((420, 88, 700, 188), fill=(0x1A, 0x0A, 0x04), outline=OUTLINE)
    draw.text((430, 94), "Груз · 3.2 / 256", fill=TEXT)
    for i in range(6):
        x = 430 + (i % 3) * 20
        y = 110 + (i // 3) * 20
        draw.rectangle((x, y, x + 16, y + 16), outline=OUTLINE)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "board_row_tooltips_preview.png")


if __name__ == "__main__":
    render("board_row_preview_ru_ru.png", 1.0, "Подробнее")
    render("board_row_preview_long_strings.png", 1.4, "Подробнее…")
    render_tooltips_demo()
    print(f"Wrote previews to {OUT}")

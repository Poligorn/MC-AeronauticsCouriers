#!/usr/bin/env python3
"""Preview: post station window (tablet)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path("/opt/cursor/artifacts/station_window_preview.png")
BG = (0x14, 0x04, 0x00, 255)
ROW = (0x3E, 0x26, 0x14, 255)
TEXT = (0xF8, 0xF0, 0xDC, 255)
MUTED = (0xCD, 0xBC, 0xA8, 255)


def main() -> None:
    w, h = 460, 300
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
        font_b = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 14)
    except OSError:
        font = font_b = ImageFont.load_default()

    px, py, pw, ph = 30, 24, 400, 252
    d.rectangle((px, py, px + pw, py + ph), outline=(0x6C, 0x35, 0x28))
    d.text((px + 8, py + 8), "Почтовая станция «Северный причал»", fill=TEXT, font=font_b)
    d.text((px + 8, py + 30), "Репутация 28 · до +20% к награде", fill=MUTED, font=font)
    d.text((px + 8, py + 48), "Открытые заказы деревни с этой станции", fill=TEXT, font=font)
    y = py + 68
    for line in [
        "#A1B2  Северный причал → Южный рынок · 42 sp",
        "#C3D4  Северный причал → Лесная застава · 38 sp",
    ]:
        d.rectangle((px + 8, y, px + pw - 8, y + 18), fill=ROW)
        d.text((px + 12, y + 3), line, fill=TEXT, font=font)
        y += 22
    d.rectangle((px + 8, py + ph - 26, px + 108, py + ph - 8), fill=ROW)
    d.text((px + 18, py + ph - 22), "Доска заказов", fill=TEXT, font=font)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()

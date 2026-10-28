#!/usr/bin/env python3
"""Preview: post station window with board-style rows."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"
OUT = Path("/opt/cursor/artifacts/station_window_preview.png")
BG = (0x0A, 0x02, 0x00, 255)
ROW = (0x3E, 0x26, 0x14, 255)
TEXT = (0xF8, 0xF0, 0xDC, 255)
MUTED = (0xCD, 0xBC, 0xA8, 255)
ORANGE = (0xFF, 0xAF, 0x55, 255)


def load(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def main() -> None:
    w, h = 480, 340
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
        font_b = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 13)
    except OSError:
        font = font_b = ImageFont.load_default()

    px, py, pw, ph = 20, 16, 440, 308
    d.rectangle((px, py, px + pw, py + ph), outline=(0x6C, 0x35, 0x28))
    d.text((px + 8, py + 8), "Почтовая станция «Северный причал»", fill=TEXT, font=font_b)
    d.text((px + 8, py + 28), "Репутация 28 · до +20% к награде", fill=MUTED, font=font)
    d.text((px + 8, py + 48), "Открытые заказы деревни с этой станции", fill=TEXT, font=font)

    coin = load("icon/coin_tablet").resize((14, 14))
    lock = load("icon/lock_tablet").resize((14, 14))
    clock = load("icon/clock_tablet").resize((14, 14))
    village = load("icon/village_tablet").resize((8, 8))
    map_pin = load("icon/map_pin_tablet").resize((16, 16))
    crate = load("icon/crate_tablet").resize((16, 16))
    btn = load("tablet_button").resize((18, 18))

    y = py + 66
    for short, customer, route, reward, collateral, mins in [
        ("A1B2", "Ясная Поляна", "→ Южный рынок", "42", "84", "55"),
        ("C3D4", "Ясная Поляна", "→ Лесная застава", "38", "76", "52"),
    ]:
        d.rectangle((px + 8, y, px + pw - 8, y + 32), fill=ROW)
        img.alpha_composite(village, (px + 52, y + 4))
        d.text((px + 14, y + 3), f"#{short}", fill=TEXT, font=font)
        d.text((px + 62, y + 3), customer, fill=TEXT, font=font)
        sx = px + 14
        sy2 = y + 16
        img.alpha_composite(coin, (sx, sy2))
        d.text((sx + 16, sy2 + 1), reward, fill=MUTED, font=font)
        sx += 48
        img.alpha_composite(lock, (sx, sy2))
        d.text((sx + 16, sy2 + 1), collateral, fill=MUTED, font=font)
        sx += 48
        img.alpha_composite(clock, (sx, sy2))
        d.text((sx + 16, sy2 + 1), f"{mins}m", fill=MUTED, font=font)
        ix = px + pw - 56
        img.alpha_composite(btn, (ix, y + 7))
        img.alpha_composite(map_pin, (ix + 1, y + 8))
        img.alpha_composite(btn, (ix + 22, y + 7))
        img.alpha_composite(crate, (ix + 23, y + 8))
        y += 36

    btn_y = py + ph - 26
    d.rectangle((px + 8, btn_y, px + 108, btn_y + 20), fill=ROW)
    d.text((px + 18, btn_y + 4), "Доска заказов", fill=ORANGE, font=font)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()

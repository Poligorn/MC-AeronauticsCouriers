#!/usr/bin/env python3
"""Preview: tablet header bell + notifications list panel."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"
OUT = Path("/opt/cursor/artifacts/notifications_preview.png")

BG = (0x14, 0x04, 0x00, 255)
ROW = (0x3E, 0x26, 0x14, 255)
TEXT = (0xF8, 0xF0, 0xDC, 255)
MUTED = (0xCD, 0xBC, 0xA8, 255)
BADGE = (0x43, 0x1A, 0x0F, 255)


def load(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def main() -> None:
    w, h = 480, 320
    img = Image.new("RGBA", (w, h), BG)
    d = ImageDraw.Draw(img)

    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
        font_b = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 14)
    except OSError:
        font = ImageFont.load_default()
        font_b = font

    # Header strip (tabs + balance + bell + mail)
    tab_y = 24
    d.rectangle((8, tab_y, 120, tab_y + 20), fill=ROW)
    d.text((16, tab_y + 4), "Заказы", fill=TEXT, font=font)
    d.text((140, tab_y + 4), "Баланс: 1 240 сп", fill=TEXT, font=font)
    bell = load("icon/bell_tablet")
    mail = load("icon/envelope_tablet")
    btn = load("tablet_button")
    bell_x, mail_x = w - 56, w - 28
    img.alpha_composite(btn.resize((20, 20)), (bell_x, tab_y))
    img.alpha_composite(bell, (bell_x + 2, tab_y + 2))
    img.alpha_composite(btn.resize((20, 20)), (mail_x, tab_y))
    img.alpha_composite(mail, (mail_x + 2, tab_y + 2))
    d.rectangle((bell_x + 10, tab_y - 2, bell_x + 22, tab_y + 8), fill=BADGE)
    d.text((bell_x + 12, tab_y - 1), "3", fill=TEXT, font=font)

    # List panel
    panel_x, panel_y, panel_w, panel_h = 40, 70, 400, 220
    d.rectangle((panel_x, panel_y, panel_x + panel_w, panel_y + panel_h), fill=(0x14, 0x04, 0x00, 200))
    d.text((panel_x + 8, panel_y + 8), "Уведомления", fill=TEXT, font=font_b)

    rows = [
        ("2m", "Заказ A1B2 принял курьер Steve"),
        ("15m", "Контейнер заказа C3D4 ждёт в 120 64 -200"),
        ("1h", "Заказ E5F6 доставлен: +80 сп, +12 XP"),
    ]
    y = panel_y + 32
    row_bg = load("tablet_list_row")
    for age, line in rows:
        img.alpha_composite(row_bg.resize((panel_w - 16, 20)), (panel_x + 8, y))
        d.text((panel_x + 12, y + 4), age, fill=MUTED, font=font)
        d.text((panel_x + 48, y + 4), line, fill=TEXT, font=font)
        y += 24

    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(OUT)


if __name__ == "__main__":
    main()

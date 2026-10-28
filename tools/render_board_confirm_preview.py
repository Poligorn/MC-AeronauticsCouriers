#!/usr/bin/env python3
"""Offline preview of the tablet accept-confirmation modal."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path("/opt/cursor/artifacts")
FILL = (0x0A, 0x02, 0x00)
PANEL = (0x3E, 0x26, 0x14)
OUTLINE = (0xB3, 0x68, 0x33)
TEXT = (0xFF, 0xAF, 0x55)
WARN = (0xFF, 0x9E, 0x00)
MUTED = (0xC8, 0x7A, 0x3A)
MAX_W = 320


def wrap(draw, font, text, width):
    words = text.split()
    lines = []
    line = ""
    for word in words:
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) <= width:
            line = trial
        else:
            if line:
                lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def main() -> None:
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    w, h = 480, 360
    img = Image.new("RGB", (w, h), (0, 0, 0))
    draw = ImageDraw.Draw(img)
    body = [
        "Принять заказ?",
        "Заказ #a3f2",
        "Заказчик: Ясная Поляна",
        "Маршрут: Ясная Поляна → Дюны · 820 блоков",
        "Награда: 92 сп",
        "Залог (удержится при принятии): 0 сп",
        "Срок: 60 мин · осталось 58 мин",
        "Требуемый ранг: I Стажёр",
    ]
    risk = (
        "Риск: при провале деревенского заказа залог 0 сп сгорает, груз уничтожается, "
        "награда 92 сп не выплачивается"
    )
    damage = (
        "При повреждении контейнера (взрыв, пираты, бой) часть груза может выпасть в мир "
        "ещё до провала заказа."
    )
    lines = []
    for i, part in enumerate(body):
        if i == 0:
            lines.append((part, TEXT, True))
        else:
            lines.extend((ln, TEXT, False) for ln in wrap(draw, font, part, MAX_W))
    lines.append(("", TEXT, False))
    for ln in wrap(draw, font, risk, MAX_W):
        lines.append((ln, WARN, False))
    for ln in wrap(draw, font, damage, MAX_W):
        lines.append((ln, WARN, False))

    line_h = 13
    content_h = sum(line_h for _ in lines) + 24
    box_w, box_h = 340, content_h + 40
    box_x, box_y = (w - box_w) // 2, (h - box_h) // 2
    draw.rectangle((box_x, box_y, box_x + box_w, box_y + box_h), fill=FILL, outline=OUTLINE, width=2)
    y = box_y + 10
    for text, color, bold in lines:
        if not text:
            y += 4
            continue
        f = font
        draw.text((box_x + 10, y), text, font=f, fill=color)
        y += line_h
    btn_y = box_y + box_h - 28
    draw.rectangle((box_x + 8, btn_y, box_x + 158, btn_y + 20), fill=PANEL, outline=OUTLINE)
    draw.text((box_x + 24, btn_y + 5), "Принять заказ", font=font, fill=TEXT)
    draw.rectangle((box_x + 174, btn_y, box_x + 324, btn_y + 20), fill=PANEL, outline=OUTLINE)
    draw.text((box_x + 220, btn_y + 5), "Отмена", font=font, fill=MUTED)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / "board_accept_confirm_480x300.png")
    print("wrote", OUT / "board_accept_confirm_480x300.png")


if __name__ == "__main__":
    main()

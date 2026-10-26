#!/usr/bin/env python3
"""Generate original CSI-inspired GUI sprites (palette only; no pack assets copied)."""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"

# Palette (Create Style Interface inspired)
BLACK = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)
PANEL = (0x87, 0x5A, 0x4D, 255)
LIGHT = (0xAA, 0x78, 0x6A, 255)
DARK = (0x4A, 0x2D, 0x31, 255)
SIDE = (0x5C, 0x3A, 0x39, 255)
INSET = (0x6B, 0x45, 0x3E, 255)
TAB_OFF = (0x41, 0x2B, 0x25, 255)
BTN_DIS = (0x41, 0x2B, 0x25, 255)
BTN_DIS_TOP = (0x52, 0x3A, 0x33, 255)
BTN_DIS_BOT = (0x24, 0x16, 0x18, 255)
GOLD = (0xF7, 0xCB, 0x6C, 255)
GOLD_BASE = (0x6C, 0x35, 0x28, 255)
CREAM = (0xF8, 0xF8, 0xEC, 255)
CREAM_SHADE = (0xCD, 0xBC, 0xA8, 255)
FIELD_BORDER = (0x69, 0x71, 0x78, 255)
TRACK_OUT = (0x2C, 0x1C, 0x1B, 255)
TRACK_IN = (0x1C, 0x11, 0x11, 255)
SCROLL_MID = (0x9B, 0x6C, 0x61, 255)
TRANSPARENT = (0, 0, 0, 0)
PAPER = (0xE8, 0xD9, 0xB5, 255)
PAPER_EDGE = (0xCD, 0xBC, 0xA8, 255)
PAPER_OUTLINE = (0xA0, 0x8A, 0x68, 255)
PAPER_FIELD = (0xF8, 0xF0, 0xDC, 255)
PAPER_FIELD_BORDER = (0x8A, 0x74, 0x58, 255)
METAL_HI = (0xC9, 0xC9, 0xC9, 255)
METAL = (0x8A, 0x8A, 0x8A, 255)
METAL_DK = (0x4F, 0x4F, 0x4F, 255)
BRASS_HI = (0xF7, 0xCB, 0x6C, 255)
BRASS = (0xC4, 0x7C, 0x25, 255)
BRASS_DK = (0x6C, 0x35, 0x28, 255)


def save_sprite(name: str, img: Image.Image, nine_slice: dict | None) -> None:
    path = OUT / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    if nine_slice:
        meta = {
            "gui": {
                "scaling": {
                    "type": "nine_slice",
                    "width": nine_slice["width"],
                    "height": nine_slice["height"],
                    "border": nine_slice["border"],
                }
            }
        }
        (OUT / f"{name}.png.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


def fill_rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, color: tuple[int, ...]) -> None:
    ImageDraw.Draw(img).rectangle((x0, y0, x1 - 1, y1 - 1), fill=color)


def draw_button_body(
    w: int,
    h: int,
    fill: tuple[int, ...],
    top: tuple[int, ...],
    bottom: tuple[int, ...],
    side: tuple[int, ...],
    outline: tuple[int, ...],
) -> Image.Image:
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 1, w - 1, h - 1, fill)
    fill_rect(img, 1, 1, w - 1, 3, top)
    fill_rect(img, 1, h - 3, w - 1, h - 1, bottom)
    fill_rect(img, 1, 1, 2, h - 1, side)
    # outline
    for x in range(w):
        img.putpixel((x, 0), outline)
        img.putpixel((x, h - 1), outline)
    for y in range(h):
        img.putpixel((0, y), outline)
        img.putpixel((w - 1, y), outline)
    return img


def gen_buttons() -> None:
    border = {"left": 3, "top": 4, "right": 3, "bottom": 3}
    ns = {"width": 200, "height": 20, "border": border}
    save_sprite(
        "button",
        draw_button_body(200, 20, PANEL, LIGHT, DARK, SIDE, BLACK),
        ns,
    )
    save_sprite(
        "button_highlighted",
        draw_button_body(200, 20, PANEL, LIGHT, DARK, SIDE, WHITE),
        ns,
    )
    dis_border = {"left": 3, "top": 4, "right": 3, "bottom": 2}
    save_sprite(
        "button_disabled",
        draw_button_body(200, 20, BTN_DIS, BTN_DIS_TOP, BTN_DIS_BOT, SIDE, BLACK),
        {"width": 200, "height": 20, "border": dis_border},
    )


def draw_tab(w: int, h: int, fill: tuple[int, ...], bevel: bool, outline: tuple[int, ...]) -> Image.Image:
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 1, w - 1, h, fill)
    if bevel:
        fill_rect(img, 1, 1, w - 1, 3, LIGHT)
        fill_rect(img, 1, h - 3, w - 1, h, DARK)
        fill_rect(img, 1, 1, 2, h, SIDE)
    for x in range(w):
        img.putpixel((x, 0), outline)
    for y in range(h):
        img.putpixel((0, y), outline)
        img.putpixel((w - 1, y), outline)
    return img


def gen_tabs() -> None:
    border = {"left": 2, "top": 2, "right": 2, "bottom": 0}
    ns = {"width": 130, "height": 24, "border": border}
    # unselected: 4px shorter (transparent bottom)
    tab_off = Image.new("RGBA", (130, 24), TRANSPARENT)
    body = draw_tab(130, 20, TAB_OFF, False, BLACK)
    tab_off.alpha_composite(body, (0, 0))
    save_sprite("tab", tab_off, ns)
    save_sprite("tab_selected", draw_tab(130, 24, PANEL, True, BLACK), ns)
    save_sprite("tab_highlighted", draw_tab(130, 24, PANEL, True, WHITE), ns)


def gen_panel() -> None:
    w, h = 64, 64
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 1, w - 1, h - 1, PANEL)
    fill_rect(img, 1, 1, w - 1, 3, LIGHT)
    fill_rect(img, 1, h - 3, w - 1, h - 1, DARK)
    fill_rect(img, 1, 1, 3, h - 1, LIGHT)
    fill_rect(img, w - 3, 1, w - 1, h - 1, DARK)
    # gold trim along top inner edge
    fill_rect(img, 2, 2, w - 2, 4, GOLD_BASE)
    fill_rect(img, 2, 3, w - 2, 5, GOLD)
    for x in range(w):
        img.putpixel((x, 0), BLACK)
        img.putpixel((x, h - 1), BLACK)
    for y in range(h):
        img.putpixel((0, y), BLACK)
        img.putpixel((w - 1, y), BLACK)
    save_sprite(
        "panel",
        img,
        {"width": 64, "height": 64, "border": {"left": 4, "top": 6, "right": 4, "bottom": 4}},
    )


def gen_inset() -> None:
    w, h = 32, 32
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 0, 0, w, h, INSET)
    fill_rect(img, 0, 0, w, 2, DARK)
    fill_rect(img, 0, 0, 2, h, SIDE)
    fill_rect(img, w - 2, 0, w, h, LIGHT)
    fill_rect(img, 0, h - 2, w, h, LIGHT)
    save_sprite(
        "inset",
        img,
        {"width": 32, "height": 32, "border": {"left": 3, "top": 3, "right": 3, "bottom": 3}},
    )


def gen_title_label() -> None:
    w, h = 128, 18
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 2, w - 1, h - 1, CREAM)
    fill_rect(img, 2, h - 3, w - 2, h - 1, CREAM_SHADE)
    fill_rect(img, w - 3, 3, w - 1, h - 2, CREAM_SHADE)
    # simple rope curl top-left (original)
    d = ImageDraw.Draw(img)
    d.arc((0, 0, 8, 10), 200, 340, fill=(0xB5, 0x93, 0x70, 255), width=1)
    for x in range(w):
        img.putpixel((x, 1), BLACK)
        img.putpixel((x, h - 1), BLACK)
    for y in range(2, h - 1):
        img.putpixel((0, y), BLACK)
        img.putpixel((w - 1, y), BLACK)
    save_sprite(
        "title_label",
        img,
        {"width": 128, "height": 18, "border": {"left": 6, "top": 4, "right": 6, "bottom": 4}},
    )


def gen_text_field(focused: bool) -> None:
    w, h = 200, 20
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    border = WHITE if focused else FIELD_BORDER
    fill_rect(img, 1, 1, w - 1, h - 1, (0, 0, 0, 255))
    for x in range(w):
        img.putpixel((x, 0), border)
        img.putpixel((x, h - 1), border)
    for y in range(h):
        img.putpixel((0, y), border)
        img.putpixel((w - 1, y), border)
    name = "text_field_highlighted" if focused else "text_field"
    save_sprite(name, img, {"width": 200, "height": 20, "border": {"left": 2, "top": 2, "right": 2, "bottom": 2}})


def gen_scroller() -> None:
    track = Image.new("RGBA", (6, 32), TRANSPARENT)
    fill_rect(track, 0, 0, 6, 32, TRACK_OUT)
    fill_rect(track, 1, 1, 5, 31, TRACK_IN)
    save_sprite("scroller_track", track, {"width": 6, "height": 32, "border": {"left": 1, "top": 2, "right": 1, "bottom": 2}})

    thumb = Image.new("RGBA", (6, 9), TRANSPARENT)
    fill_rect(thumb, 0, 0, 6, 9, PANEL)
    fill_rect(thumb, 0, 0, 6, 2, LIGHT)
    fill_rect(thumb, 0, 7, 6, 9, SCROLL_MID)
    fill_rect(thumb, 0, 8, 6, 9, DARK)
    save_sprite("scroller", thumb, {"width": 6, "height": 9, "border": {"left": 1, "top": 2, "right": 1, "bottom": 2}})


def gen_slot() -> None:
    img = Image.new("RGBA", (18, 18), TRANSPARENT)
    fill_rect(img, 0, 0, 18, 18, INSET)
    fill_rect(img, 0, 0, 18, 2, DARK)
    fill_rect(img, 0, 0, 2, 18, SIDE)
    fill_rect(img, 16, 0, 18, 18, LIGHT)
    fill_rect(img, 0, 16, 18, 18, LIGHT)
    save_sprite("container_slot", img, None)


def gen_paper_sheet() -> None:
    w, h = 48, 48
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 1, w - 1, h - 1, PAPER)
    fill_rect(img, 2, h - 4, w - 2, h - 1, PAPER_EDGE)
    fill_rect(img, w - 4, 2, w - 1, h - 2, PAPER_EDGE)
    for x in range(w):
        img.putpixel((x, 0), PAPER_OUTLINE)
        img.putpixel((x, h - 1), PAPER_OUTLINE)
    for y in range(h):
        img.putpixel((0, y), PAPER_OUTLINE)
        img.putpixel((w - 1, y), PAPER_OUTLINE)
    save_sprite(
        "paper_sheet",
        img,
        {"width": 48, "height": 48, "border": {"left": 5, "top": 5, "right": 5, "bottom": 5}},
    )


def gen_paper_field(focused: bool) -> None:
    w, h = 200, 20
    border_c = WHITE if focused else PAPER_FIELD_BORDER
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 1, 1, w - 1, h - 1, PAPER_FIELD)
    fill_rect(img, 2, h - 3, w - 2, h - 1, PAPER_EDGE)
    for x in range(w):
        img.putpixel((x, 0), border_c)
        img.putpixel((x, h - 1), border_c)
    for y in range(h):
        img.putpixel((0, y), border_c)
        img.putpixel((w - 1, y), border_c)
    name = "paper_field_highlighted" if focused else "paper_field"
    save_sprite(name, img, {"width": 200, "height": 20, "border": {"left": 2, "top": 2, "right": 2, "bottom": 2}})


def gen_clipboard_clip() -> None:
    w, h = 128, 38
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    d = ImageDraw.Draw(img)
    # Wide base plate bolted to the wooden frame
    fill_rect(img, 8, 24, w - 8, h - 1, METAL_DK)
    fill_rect(img, 10, 25, w - 10, h - 2, METAL)
    fill_rect(img, 10, 25, w - 10, 28, METAL_HI)
    fill_rect(img, 10, h - 4, w - 10, h - 2, (0x35, 0x35, 0x35, 255))
    for rx in (34, w - 34):
        d.ellipse((rx - 4, 28, rx + 4, 36), fill=METAL_DK, outline=BLACK)
        d.ellipse((rx - 2, 30, rx + 2, 34), fill=METAL_HI)
        d.point((rx, 32), fill=BLACK)
    # Side jaws gripping the stack
    fill_rect(img, 4, 20, 16, h - 4, METAL_DK)
    fill_rect(img, w - 16, 20, w - 4, h - 4, METAL_DK)
    fill_rect(img, 6, 21, 14, h - 5, METAL)
    fill_rect(img, w - 14, 21, w - 6, h - 5, METAL)
    # Brass spring block under the lever
    fill_rect(img, 22, 16, w - 22, 26, BRASS_DK)
    fill_rect(img, 26, 17, w - 26, 25, BRASS)
    fill_rect(img, 32, 18, w - 32, 22, BRASS_HI)
    # Raised lever with rounded top
    d.rounded_rectangle((38, 8, w - 38, 20), radius=5, fill=BRASS_DK, outline=BLACK, width=1)
    d.rounded_rectangle((40, 9, w - 40, 18), radius=4, fill=BRASS)
    d.rounded_rectangle((44, 10, w - 44, 15), radius=3, fill=BRASS_HI)
    # Finger ring / hole at the top
    cx = w // 2
    d.ellipse((cx - 9, 0, cx + 9, 14), fill=METAL_DK, outline=BLACK, width=1)
    d.ellipse((cx - 5, 3, cx + 5, 11), fill=(0x55, 0x55, 0x55, 255), outline=BLACK)
    px = img.load()
    for yy in range(5, 10):
        for xx in range(cx - 2, cx + 3):
            if (xx - cx) ** 2 + (yy - 7) ** 2 <= 6:
                px[xx, yy] = TRANSPARENT
    d = ImageDraw.Draw(img)
    d.rectangle((8, 24, w - 8, h - 2), outline=BLACK)
    save_sprite("clipboard_clip", img, None)


def gen_paper_slot() -> None:
    img = Image.new("RGBA", (18, 18), TRANSPARENT)
    fill_rect(img, 0, 0, 18, 18, PAPER_FIELD)
    fill_rect(img, 0, 0, 18, 2, PAPER_OUTLINE)
    fill_rect(img, 0, 0, 2, 18, PAPER_OUTLINE)
    fill_rect(img, 16, 0, 18, 18, PAPER_EDGE)
    fill_rect(img, 0, 16, 18, 18, PAPER_EDGE)
    save_sprite("paper_slot", img, None)


def gen_envelope() -> None:
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(img)
    d.rectangle((1, 4, 14, 13), fill=PANEL, outline=BLACK)
    d.polygon([(1, 4), (8, 9), (15, 4)], fill=LIGHT, outline=BLACK)
    d.line([(1, 13), (8, 8)], fill=DARK, width=1)
    d.line([(15, 13), (8, 8)], fill=DARK, width=1)
    save_sprite("icon/envelope", img, None)


def hx(color: str) -> tuple[int, int, int, int]:
    c = color.lstrip("#")
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), 255)


# --- Order tablet monitor (board screen only; original pixels) ---
TB_HI = hx("#FFE97C")
TB_MID = hx("#FFD25D")
TB_MED = hx("#E8BB5C")
TB_SH = hx("#B87E3C")
TB_DK = hx("#8B5C33")
TB_INNER = hx("#693826")
TB_SCREEN = hx("#2C0900")
TB_GRID = hx("#431A0F")
TB_OUTLINE = hx("#B36833")
TB_ROW = hx("#3E2614")
TB_TAB_SEL = hx("#431A0F")
TB_BTN_HOVER = hx("#1F0F02")
TB_HOVER_OUT = hx("#FF9E00")
TB_SEL_OUT = hx("#FFAF55")
TB_SCROLL = hx("#FF7A2F")
TB_TRACK = hx("#1A0A04")
TB_COPPER_HI = hx("#E3826C")
TB_COPPER = hx("#C87456")
TB_COPPER_DK = hx("#A75A40")
TB_COPPER_EDGE = hx("#794631")
TB_GEAR_HI = hx("#82613A")
TB_GEAR = hx("#70522E")
TB_GEAR_DK = hx("#5A4424")
TB_ORANGE_ICON = hx("#FFAF55")
TB_ORANGE_ICON_DK = hx("#C87A3A")


def _outline_rect(img: Image.Image, x0: int, y0: int, x1: int, y1: int, outline: tuple[int, ...]) -> None:
    px = img.load()
    for x in range(x0, x1):
        px[x, y0] = outline
        px[x, y1 - 1] = outline
    for y in range(y0, y1):
        px[x0, y] = outline
        px[x1 - 1, y] = outline


def _rivet(img: Image.Image, cx: int, cy: int) -> None:
    d = ImageDraw.Draw(img)
    d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=TB_MED, outline=BLACK)
    d.point((cx - 1, cy - 1), fill=TB_HI)


def gen_tablet_frame() -> None:
    """8px nine-slice: 1 black + 6 brass + 1 inner bevel; transparent screen hole."""
    w, h = 64, 64
    border = 8
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 0, 0, w, h, BLACK)
    # 6px brass band (pixels 1..6 from each edge)
    fill_rect(img, 1, 1, w - 1, h - 1, TB_MID)
    fill_rect(img, 1, 1, w - 1, 3, TB_HI)
    fill_rect(img, 1, 1, 3, h - 1, TB_HI)
    fill_rect(img, 2, 2, w - 2, 4, TB_MED)
    fill_rect(img, 2, 2, 4, h - 2, TB_MED)
    fill_rect(img, w - 3, 1, w - 1, h - 1, TB_DK)
    fill_rect(img, 1, h - 3, w - 1, h - 1, TB_DK)
    fill_rect(img, w - 4, 3, w - 2, h - 3, TB_SH)
    fill_rect(img, 3, h - 4, w - 3, h - 2, TB_SH)
    # inner dark bevel (pixel row/col 7)
    fill_rect(img, 7, 7, w - 7, h - 7, TB_INNER)
    fill_rect(img, 8, 8, w - 8, h - 8, TRANSPARENT)
    for cx, cy in ((4, 4), (w - 5, 4), (4, h - 5), (w - 5, h - 5)):
        _rivet(img, cx, cy)
    save_sprite(
        "tablet_frame",
        img,
        {"width": 64, "height": 64, "border": {"left": border, "top": border, "right": border, "bottom": border}},
    )


def gen_tablet_grid() -> None:
    """9×9 tile for optional blit; board screen draws grid procedurally to avoid stretch stripes."""
    size = 9
    img = Image.new("RGBA", (size, size), TB_SCREEN)
    px = img.load()
    for y in range(size):
        for x in range(size):
            if x == 0 or y == 0:
                px[x, y] = TB_GRID
    save_sprite("tablet_grid", img, None)


def _tablet_button(w: int, h: int, fill: tuple[int, ...] | None, outline: tuple[int, ...]) -> Image.Image:
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    if fill is not None:
        fill_rect(img, 1, 1, w - 1, h - 1, fill)
    _outline_rect(img, 0, 0, w, h, outline)
    return img


def gen_tablet_buttons() -> None:
    border = {"left": 2, "top": 2, "right": 2, "bottom": 2}
    ns = {"width": 200, "height": 20, "border": border}
    save_sprite("tablet_button", _tablet_button(200, 20, None, TB_OUTLINE), ns)
    save_sprite("tablet_button_highlighted", _tablet_button(200, 20, TB_BTN_HOVER, TB_HOVER_OUT), ns)
    save_sprite("tablet_button_disabled", _tablet_button(200, 20, (0x12, 0x08, 0x04, 255), hx("#873B00")), ns)


def _tablet_tab(w: int, h: int, fill: tuple[int, ...] | None, outline: tuple[int, ...]) -> Image.Image:
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    if fill is not None:
        fill_rect(img, 1, 1, w - 1, h, fill)
    for x in range(w):
        img.putpixel((x, 0), outline)
    for y in range(h):
        img.putpixel((0, y), outline)
        img.putpixel((w - 1, y), outline)
    return img


def gen_tablet_tabs() -> None:
    border = {"left": 2, "top": 2, "right": 2, "bottom": 0}
    ns = {"width": 130, "height": 24, "border": border}
    tab_off = Image.new("RGBA", (130, 24), TRANSPARENT)
    tab_off.alpha_composite(_tablet_tab(130, 20, None, TB_OUTLINE), (0, 0))
    save_sprite("tablet_tab", tab_off, ns)
    save_sprite("tablet_tab_selected", _tablet_tab(130, 24, TB_TAB_SEL, TB_SEL_OUT), ns)
    save_sprite("tablet_tab_highlighted", _tablet_tab(130, 24, TB_BTN_HOVER, TB_HOVER_OUT), ns)


def gen_tablet_list_row() -> None:
    w, h = 32, 22
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    fill_rect(img, 0, 0, w, h, TB_ROW)
    _outline_rect(img, 0, 0, w, h, TB_OUTLINE)
    save_sprite(
        "tablet_list_row",
        img,
        {"width": 32, "height": 22, "border": {"left": 2, "top": 2, "right": 2, "bottom": 2}},
    )


def gen_tablet_scroller() -> None:
    track = Image.new("RGBA", (6, 32), TRANSPARENT)
    fill_rect(track, 0, 0, 6, 32, TB_TRACK)
    _outline_rect(track, 0, 0, 6, 32, TB_INNER)
    save_sprite(
        "tablet_scroller_track",
        track,
        {"width": 6, "height": 32, "border": {"left": 1, "top": 2, "right": 1, "bottom": 2}},
    )
    thumb = Image.new("RGBA", (6, 9), TRANSPARENT)
    fill_rect(thumb, 0, 0, 6, 9, TB_SCROLL)
    fill_rect(thumb, 0, 0, 6, 2, hx("#FFAF55"))
    fill_rect(thumb, 0, 7, 6, 9, TB_COPPER_DK)
    save_sprite(
        "tablet_scroller",
        thumb,
        {"width": 6, "height": 9, "border": {"left": 1, "top": 2, "right": 1, "bottom": 2}},
    )


def gen_tablet_antenna() -> None:
    w, h = 10, 28
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    d = ImageDraw.Draw(img)
    # brass base on frame top
    fill_rect(img, 0, h - 4, w, h, TB_SH)
    fill_rect(img, 1, h - 4, w - 1, h - 1, TB_MID)
    fill_rect(img, 1, h - 3, w - 1, h - 2, TB_HI)
    # copper rod
    fill_rect(img, 4, 6, 6, h - 4, TB_COPPER_DK)
    fill_rect(img, 3, 7, 7, h - 5, TB_COPPER)
    fill_rect(img, 3, 7, 4, h - 6, TB_COPPER_HI)
    # ring + ball tip
    d.ellipse((1, 0, 9, 6), outline=TB_COPPER, width=1)
    d.ellipse((3, 1, 7, 5), fill=TB_COPPER_HI, outline=TB_COPPER_EDGE)
    d.ellipse((4, 0, 6, 2), fill=TB_COPPER_HI, outline=BLACK)
    save_sprite("tablet_decor_antenna", img, None)


def gen_tablet_gear() -> None:
    import math

    w, h = 28, 28
    img = Image.new("RGBA", (w, h), TRANSPARENT)
    d = ImageDraw.Draw(img)
    cx, cy = w - 8, h // 2
    r_outer = 13
    r_inner = 5
    teeth = 8
    pts = []
    for i in range(teeth * 2):
        ang = math.pi * 2 * i / (teeth * 2) - math.pi / 2
        r = r_outer if i % 2 == 0 else r_outer - 4
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    d.polygon(pts, fill=hx("#614B2E"), outline=BLACK)
    d.ellipse((cx - r_inner, cy - r_inner, cx + r_inner, cy + r_inner), fill=TB_GEAR_DK, outline=BLACK)
    d.ellipse((cx - r_inner + 1, cy - r_inner + 1, cx + r_inner - 1, cy + r_inner - 1), fill=TB_GEAR)
    d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=TB_GEAR_HI)
    d.arc((cx - 12, cy - 12, cx + 2, cy + 12), 80, 280, fill=hx("#82613A"), width=3)
    save_sprite("tablet_decor_gear", img, None)


def gen_envelope_tablet() -> None:
    img = Image.new("RGBA", (16, 16), TRANSPARENT)
    d = ImageDraw.Draw(img)
    d.rectangle((1, 4, 14, 13), fill=TB_ROW, outline=TB_OUTLINE)
    d.polygon([(1, 4), (8, 9), (15, 4)], fill=TB_ORANGE_ICON, outline=TB_OUTLINE)
    d.line([(1, 13), (8, 8)], fill=TB_ORANGE_ICON_DK, width=1)
    d.line([(15, 13), (8, 8)], fill=TB_ORANGE_ICON_DK, width=1)
    save_sprite("icon/envelope_tablet", img, None)


def gen_tablet() -> None:
    gen_tablet_frame()
    gen_tablet_grid()
    gen_tablet_buttons()
    gen_tablet_tabs()
    gen_tablet_list_row()
    gen_tablet_scroller()
    gen_tablet_antenna()
    gen_tablet_gear()
    gen_envelope_tablet()


def main() -> None:
    gen_buttons()
    gen_tabs()
    gen_panel()
    gen_inset()
    gen_title_label()
    gen_text_field(False)
    gen_text_field(True)
    gen_scroller()
    gen_slot()
    gen_paper_sheet()
    gen_paper_field(False)
    gen_paper_field(True)
    gen_clipboard_clip()
    gen_paper_slot()
    gen_envelope()
    gen_tablet()
    print(f"Wrote sprites to {OUT}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Layout mocks for villages-0.3.0-audit.md sections A/B.

These are not runClient frames. Station block uses the current stub textures
(vanilla cartography table). Jade mock reconstructs the default tooltip
without a Couriers plugin.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SPRITES = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites"
CARGO = ROOT / "src/main/resources/assets/aerocouriers/textures/block"
VANILLA = Path("/tmp/mc-tex")
OUT = Path("/opt/cursor/artifacts/villages-audit")

BG_WORLD = (0x6B, 0xA0, 0xC8, 255)
BG_DARK = (0x0A, 0x02, 0x00, 255)
ROW = (0x3E, 0x26, 0x14, 255)
TEXT = (0xF8, 0xF0, 0xDC, 255)
MUTED = (0xCD, 0xBC, 0xA8, 255)
ORANGE = (0xFF, 0xAF, 0x55, 255)
GOLD = (0xFF, 0xD7, 0x6A, 255)
JADE_BG = (0x10, 0x10, 0x10, 220)
JADE_TITLE = (0xFF, 0xAA, 0x00, 255)
JADE_BODY = (0xA0, 0xA0, 0xA0, 255)
JADE_MOD = (0x55, 0x55, 0xFF, 255)
WARN = (0xE0, 0x70, 0x50, 255)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    path = Path("/usr/share/fonts/truetype/dejavu") / name
    try:
        return ImageFont.truetype(str(path), size)
    except OSError:
        return ImageFont.load_default()


def load_sprite(name: str) -> Image.Image:
    return Image.open(SPRITES / f"{name}.png").convert("RGBA")


def nearest(img: Image.Image, scale: int) -> Image.Image:
    return img.resize((img.width * scale, img.height * scale), Image.Resampling.NEAREST)


def cube_faces(top: Image.Image, front: Image.Image, side: Image.Image, scale: int = 10) -> Image.Image:
    """Flattened three-face cube: top, front, right — same layout as render_container_preview."""
    top, front, side = (nearest(im.convert("RGBA"), scale) for im in (top, front, side))
    s = top.width
    canvas = Image.new("RGBA", (s * 2 + 24, s * 2 + 24), (0, 0, 0, 0))
    canvas.alpha_composite(top, (s // 2 + 12, 8))
    canvas.alpha_composite(front, (8, s // 2 + 12))
    canvas.alpha_composite(side, (s + 16, s // 2 + 12))
    return canvas


def caption(draw: ImageDraw.ImageDraw, text: str, xy: tuple[int, int], *, warn: bool = False) -> None:
    draw.text(xy, text, fill=WARN if warn else MUTED, font=font(12))


def station_block_stub() -> None:
    top = Image.open(VANILLA / "cartography_table_top.png").convert("RGBA")
    front = Image.open(VANILLA / "cartography_table_side3.png").convert("RGBA")
    side = Image.open(VANILLA / "cartography_table_side1.png").convert("RGBA")
    cube = cube_faces(top, front, side, 12)
    img = Image.new("RGBA", (720, 520), BG_WORLD)
    d = ImageDraw.Draw(img)
    d.rectangle((16, 16, 704, 504), outline=(0x2A, 0x4A, 0x6A), width=2)
    d.text((28, 24), "aerocouriers:post_station  —  текущая модель в мире", fill=(0x12, 0x24, 0x38, 255), font=font(16, True))
    d.text((28, 48), "Заглушка: parent minecraft:block/orientable, текстуры ванильного стола картографа (16×16).", fill=(0x12, 0x24, 0x38, 255), font=font(13))
    img.alpha_composite(cube, ((720 - cube.width) // 2, 80))
    d.text((28, 430), "top  = minecraft:block/cartography_table_top", fill=(0x12, 0x24, 0x38, 255), font=font(12))
    d.text((28, 448), "front = minecraft:block/cartography_table_side3   (грань facing)", fill=(0x12, 0x24, 0x38, 255), font=font(12))
    d.text((28, 466), "side  = minecraft:block/cartography_table_side1", fill=(0x12, 0x24, 0x38, 255), font=font(12))
    d.text((28, 484), "Это не кадр runClient: раскладка граней из JSON модели. В мире блок неотличим от стола картографа.", fill=WARN, font=font(12))
    img.convert("RGB").save(OUT / "station-block-in-world-stub.png")


def village_structure() -> None:
    planks = nearest(Image.open(VANILLA / "spruce_planks.png").convert("RGBA"), 8)
    station_top = nearest(Image.open(VANILLA / "cartography_table_top.png").convert("RGBA"), 8)
    lantern = nearest(Image.open(VANILLA / "lantern.png").convert("RGBA").crop((0, 0, 16, 16)), 8)
    cell = 128
    pad = 48
    img = Image.new("RGBA", (pad * 2 + cell * 3, pad * 2 + cell * 3 + 80), (0x3A, 0x2A, 0x1C, 255))
    d = ImageDraw.Draw(img)
    d.text((pad, 12), "Структура village/post_station.nbt  —  вид сверху, 3×3×3", fill=TEXT, font=font(16, True))
    labels = {
        (0, 1): "jigsaw\nwest_up\nbuilding_entrance",
        (1, 1): "post_station\nfacing=west\nvillage=true",
        (2, 0): "fence + lantern",
        (2, 2): "fence + lantern",
    }
    for x in range(3):
        for z in range(3):
            px = pad + x * cell
            pz = pad + 40 + z * cell
            tile = station_top if (x, z) == (1, 1) else planks
            img.paste(tile.resize((cell - 8, cell - 8), Image.Resampling.NEAREST), (px + 4, pz + 4))
            if (x, z) in ((2, 0), (2, 2)):
                img.alpha_composite(lantern.resize((40, 40), Image.Resampling.NEAREST), (px + 44, pz + 16))
            d.rectangle((px, pz, px + cell, pz + cell), outline=(0x8A, 0x6A, 0x48), width=2)
            d.text((px + 6, pz + 6), f"({x},z={z})", fill=GOLD, font=font(11, True))
            if (x, z) in labels:
                d.multiline_text((px + 8, pz + 70), labels[(x, z)], fill=TEXT, font=font(11), spacing=1)
    d.text((pad, pad + 40 + cell * 3 + 8), "Пол — spruce_planks; (0,1) на y=0 — jigsaw (final_state structure_void). Не runClient.", fill=MUTED, font=font(12))
    img.convert("RGB").save(OUT / "station-village-structure-topdown.png")


def station_screen(operator: bool) -> None:
    w, h = 560, 400
    img = Image.new("RGBA", (w, h), BG_DARK)
    d = ImageDraw.Draw(img)
    px, py, pw, ph = 24, 18, 512, 364
    d.rectangle((px, py, px + pw, py + ph), outline=(0x6C, 0x35, 0x28), width=2)
    d.text((px + 10, py + 8), "Почтовая станция «Северный причал»", fill=TEXT, font=font(14, True))
    y = py + 30
    if operator:
        d.rectangle((px + 10, y, px + pw - 92, y + 20), outline=MUTED)
        d.text((px + 14, y + 4), "Северный причал", fill=TEXT, font=font(12))
        d.rectangle((px + pw - 86, y, px + pw - 10, y + 20), fill=ROW)
        d.text((px + pw - 78, y + 4), "Rename", fill=ORANGE, font=font(12))
        y += 28
    d.text((px + 10, y), "Reputation 28 (tier 2) · up to +20% reward · next tier at 50", fill=MUTED, font=font(12))
    y += 18
    d.text((px + 10, y), "Open NPC orders from this station", fill=TEXT, font=font(12, True))
    d.text((px + pw - 150, y), "New orders in 7:12", fill=ORANGE, font=font(12))
    y += 20

    lock = load_sprite("icon/lock_tablet").resize((14, 14))
    clock = load_sprite("icon/clock_tablet").resize((14, 14))
    village = load_sprite("icon/village_tablet").resize((8, 8))
    map_pin = load_sprite("icon/map_pin_tablet").resize((16, 16))
    crate = load_sprite("icon/crate_tablet").resize((16, 16))
    rare = load_sprite("icon/rare_cargo_tablet").resize((14, 14))
    btn = load_sprite("tablet_button").resize((18, 18))
    coin = Image.new("RGBA", (14, 14), (0, 0, 0, 0))
    ImageDraw.Draw(coin).ellipse((1, 1, 12, 12), fill=GOLD)

    rows = [
        ("a1b2c3d4", "OPEN", "Северный причал", "42", "—", "55", False),
        ("c3d4e5f6", "OPEN", "Северный причал", "96", "192", "48", True),
        ("empty", "", "", "", "", "", False),
    ]
    for short, status, customer, reward, collateral, mins, is_rare in rows[:2]:
        d.rectangle((px + 8, y, px + pw - 8, y + 36), fill=ROW)
        d.text((px + 14, y + 4), f"#{short}  {status}", fill=TEXT, font=font(12))
        img.alpha_composite(village, (px + 168, y + 6))
        d.text((px + 180, y + 4), customer, fill=TEXT, font=font(12))
        sx, sy2 = px + 14, y + 18
        img.alpha_composite(coin, (sx, sy2))
        d.text((sx + 16, sy2 + 1), reward, fill=MUTED, font=font(11))
        sx += 52
        if collateral != "—":
            img.alpha_composite(lock, (sx, sy2))
            d.text((sx + 16, sy2 + 1), collateral, fill=MUTED, font=font(11))
            sx += 52
        img.alpha_composite(clock, (sx, sy2))
        d.text((sx + 16, sy2 + 1), f"{mins}m", fill=MUTED, font=font(11))
        if is_rare:
            img.alpha_composite(rare, (sx + 56, sy2))
        ix = px + pw - 56
        img.alpha_composite(btn, (ix, y + 9))
        img.alpha_composite(map_pin, (ix + 1, y + 10))
        img.alpha_composite(btn, (ix + 22, y + 9))
        img.alpha_composite(crate, (ix + 23, y + 10))
        y += 40

    d.text((px + 14, y + 4), "No open village orders here right now.", fill=MUTED, font=font(12))
    btn_y = py + ph - 28
    d.rectangle((px + 10, btn_y, px + 130, btn_y + 20), fill=ROW)
    d.text((px + 22, btn_y + 4), "Job board", fill=ORANGE, font=font(12))
    d.rectangle((px + pw - 78, btn_y, px + pw - 10, btn_y + 20), fill=ROW)
    d.text((px + pw - 62, btn_y + 4), "Done", fill=ORANGE, font=font(12))
    who = "оператор (canRename): EditBox + Rename" if operator else "игрок: без поля имени"
    d.text((px + 140, btn_y + 4), who, fill=MUTED, font=font(11))
    name = "station-screen-operator-layout.png" if operator else "station-screen-player-layout.png"
    img.convert("RGB").save(OUT / name)


def jade_box(title: str, lines: list[tuple[str, tuple[int, int, int, int]]], footer: str) -> Image.Image:
    pad, line_h = 10, 16
    width = 360
    height = pad * 2 + 18 + line_h * len(lines) + 22
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, width - 1, height - 1), radius=4, fill=JADE_BG, outline=(0x50, 0x50, 0x50, 255))
    d.text((pad, 6), title, fill=JADE_TITLE, font=font(13, True))
    y = 26
    for text, color in lines:
        d.text((pad, y), text, fill=color, font=font(12))
        y += line_h
    d.text((pad, height - 18), footer, fill=JADE_MOD, font=font(11))
    return img


def jade_current_container() -> None:
    top = Image.open(CARGO / "cargo_container_top.png").convert("RGBA")
    door = Image.open(CARGO / "cargo_container_door.png").convert("RGBA")
    side = Image.open(CARGO / "cargo_container_side.png").convert("RGBA")
    cube = cube_faces(top, door, side, 8)
    tip = jade_box(
        "Sealed Cargo Container (S)",
        [
            ("Harvest: Diamond Pickaxe", JADE_BODY),
            ("(no Couriers plugin — contract data absent)", WARN),
        ],
        "aerocouriers",
    )
    img = Image.new("RGBA", (720, 420), (0x4A, 0x5A, 0x48, 255))
    d = ImageDraw.Draw(img)
    d.text((20, 12), "Текущий Jade на контейнере S (реконструкция, плагина нет)", fill=TEXT, font=font(16, True))
    img.alpha_composite(cube, (40, 50))
    img.alpha_composite(tip, (340, 90))
    d.text((20, 360), "BE хранит только contractId. Вес, номер заказа, курьер, дедлайн в подсказке не появляются.", fill=TEXT, font=font(12))
    d.text((20, 380), "Не кадр runClient и не проверено на плоте Sable. Jade 15.10.6 в этом прогоне не запускался.", fill=WARN, font=font(12))
    img.convert("RGB").save(OUT / "jade-container-s-current-mock.png")


def jade_current_station() -> None:
    top = Image.open(VANILLA / "cartography_table_top.png").convert("RGBA")
    front = Image.open(VANILLA / "cartography_table_side3.png").convert("RGBA")
    side = Image.open(VANILLA / "cartography_table_side1.png").convert("RGBA")
    cube = cube_faces(top, front, side, 8)
    tip = jade_box(
        "Северный причал",
        [
            ("Post Station", JADE_BODY),
            ("(Nameable custom name from BE NBT «name»)", MUTED),
            ("Reputation / order count / refresh: not shown", WARN),
        ],
        "aerocouriers",
    )
    img = Image.new("RGBA", (720, 400), BG_WORLD)
    d = ImageDraw.Draw(img)
    d.text((20, 12), "Текущий Jade на станции (реконструкция)", fill=(0x12, 0x24, 0x38, 255), font=font(16, True))
    img.alpha_composite(cube, (40, 50))
    img.alpha_composite(tip, (340, 80))
    d.text((20, 350), "PostStationBlockEntity implements Nameable — ванильный Jade, скорее всего, покажет имя станции.", fill=(0x12, 0x24, 0x38, 255), font=font(12))
    d.text((20, 370), "Репутация живёт в StationAtlas, не в BE. Не проверено в runClient.", fill=WARN, font=font(12))
    img.convert("RGB").save(OUT / "jade-station-current-mock.png")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    station_block_stub()
    village_structure()
    station_screen(operator=True)
    station_screen(operator=False)
    jade_current_container()
    jade_current_station()
    # Keep the older mock name as an alias of the operator layout.
    Image.open(OUT / "station-screen-operator-layout.png").save(OUT / "station-window-layout-mock.png")
    for p in sorted(OUT.glob("*.png")):
        im = Image.open(p)
        print(f"{p.name} {im.size}")


if __name__ == "__main__":
    main()

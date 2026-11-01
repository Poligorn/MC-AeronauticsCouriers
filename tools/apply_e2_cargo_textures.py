#!/usr/bin/env python3
"""Apply approved E2 cargo textures and generate XL 2×2×3 segment models.

Textures are copied 1:1 from the E2 zip. Door faces become regular sides.
XL UV uses the 16-unit atlas space (same convention as L on 32×32):
a 32×48 side maps 8 UV = 16 px horizontally and 16/3 UV = 16 px vertically.
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BLOCK_TEX = ROOT / "src/main/resources/assets/aerocouriers/textures/block"
BLOCK_MODELS = ROOT / "src/main/resources/assets/aerocouriers/models/block"
ITEM_MODELS = ROOT / "src/main/resources/assets/aerocouriers/models/item"
BLOCKSTATES = ROOT / "src/main/resources/assets/aerocouriers/blockstates"

SRC_RAW = Path("/tmp/e2_textures/raw")

DOOR_FILES = (
    "cargo_container_door.png",
    "cargo_container_l_door.png",
    "cargo_container_m_door_lower.png",
    "cargo_container_m_door_upper.png",
)

COPY_NAMES = (
    "cargo_container_bottom.png",
    "cargo_container_top.png",
    "cargo_container_side.png",
    "cargo_container_side_waybill.png",
    "cargo_container_m_bottom.png",
    "cargo_container_m_top.png",
    "cargo_container_m_side_lower.png",
    "cargo_container_m_side_lower_waybill.png",
    "cargo_container_m_side_upper.png",
    "cargo_container_l_bottom.png",
    "cargo_container_l_top.png",
    "cargo_container_l_side.png",
    "cargo_container_l_side_waybill.png",
    "cargo_container_xl_bottom.png",
    "cargo_container_xl_top.png",
    "cargo_container_xl_side.png",
    "cargo_container_xl_side_waybill.png",
)

Y_ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
V_STEP = 16.0 / 3.0


def r(value: float) -> float | int:
    rounded = round(value, 4)
    if abs(rounded - round(rounded)) < 1e-9:
        return int(round(rounded))
    return rounded


def uv4(u0: float, v0: float, u1: float, v1: float) -> list[float | int]:
    return [r(u0), r(v0), r(u1), r(v1)]


def side_row(y: int) -> tuple[float, float]:
    # Image top is y=2. 16-unit V covers 48 px.
    row_from_top = 2 - y
    v0 = row_from_top * V_STEP
    return v0, v0 + V_STEP


def face(uv: list[float | int], texture: str, cull: str | None = None) -> dict:
    out: dict = {"uv": uv, "texture": texture}
    if cull:
        out["cullface"] = cull
    return out


def dump(path: Path, data: dict) -> None:
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


def copy_textures() -> None:
    if not SRC_RAW.is_dir():
        raise SystemExit(f"missing E2 raw textures: {SRC_RAW}")
    BLOCK_TEX.mkdir(parents=True, exist_ok=True)
    for name in COPY_NAMES:
        src = SRC_RAW / name
        if not src.is_file():
            raise SystemExit(f"missing {src}")
        shutil.copy2(src, BLOCK_TEX / name)
        print("copied", name)
    for name in DOOR_FILES:
        dest = BLOCK_TEX / name
        if dest.exists():
            dest.unlink()
            print("deleted", name)


def patch_s_m_l_models() -> None:
    for name in ("cargo_container_s.json", "cargo_container_s_waybill.json"):
        path = BLOCK_MODELS / name
        data = json.loads(path.read_text(encoding="utf-8"))
        data["textures"]["north"] = "aerocouriers:block/cargo_container_side"
        dump(path, data)

    for name in (
        "cargo_container_m_lower.json",
        "cargo_container_m_lower_waybill.json",
        "cargo_container_m_upper.json",
    ):
        path = BLOCK_MODELS / name
        data = json.loads(path.read_text(encoding="utf-8"))
        data["textures"].pop("door", None)
        north = data["elements"][0]["faces"]["north"]
        north["texture"] = "#side"
        dump(path, data)

    for path in sorted(BLOCK_MODELS.glob("cargo_container_l*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        textures = data.get("textures", {})
        if "door" in textures:
            del textures["door"]
        for element in data.get("elements", []):
            for face_data in element.get("faces", {}).values():
                if face_data.get("texture") == "#door":
                    face_data["texture"] = "#side"
        dump(path, data)


def xl_side_uv(col: int, y: int) -> list[float | int]:
    v0, v1 = side_row(y)
    return uv4(col * 8, v0, col * 8 + 8, v1)


def xl_model(x: int, y: int, z: int, waybill: bool) -> dict:
    # Exterior UVs match L: texture authored as seen from outside that face.
    north = xl_side_uv(1 - x, y)  # looking -Z: image left = +X
    south = xl_side_uv(x, y)  # looking +Z: image left = -X
    east = xl_side_uv(1 - z, y)  # looking +X: image left = +Z
    west = xl_side_uv(z, y)  # looking -X: image left = -Z
    top = uv4(x * 8, z * 8, x * 8 + 8, z * 8 + 8)
    bottom = uv4(x * 8, (1 - z) * 8, x * 8 + 8, (1 - z) * 8 + 8)

    east_tex = "#side_w" if waybill and x == 1 and y == 0 and z == 0 else "#side"

    faces = {
        "north": face(north, "#side", "north" if z == 0 else None),
        "south": face(south, "#side", "south" if z == 1 else None),
        "east": face(east, east_tex, "east" if x == 1 else None),
        "west": face(west, "#side", "west" if x == 0 else None),
        "up": face(top, "#top" if y == 2 else "#side", "up" if y == 2 else None),
        "down": face(bottom, "#bottom" if y == 0 else "#side", "down" if y == 0 else None),
    }

    textures = {
        "particle": "aerocouriers:block/cargo_container_xl_side",
        "side": "aerocouriers:block/cargo_container_xl_side",
        "top": "aerocouriers:block/cargo_container_xl_top",
        "bottom": "aerocouriers:block/cargo_container_xl_bottom",
    }
    if waybill:
        textures["side_w"] = "aerocouriers:block/cargo_container_xl_side_waybill"

    return {
        "parent": "minecraft:block/block",
        "textures": textures,
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}],
    }


def write_xl_models() -> None:
    for y in (0, 1, 2):
        for z in (0, 1):
            for x in (0, 1):
                dump(BLOCK_MODELS / f"cargo_container_xl_seg{x}{y}{z}.json", xl_model(x, y, z, False))
    dump(BLOCK_MODELS / "cargo_container_xl_seg100_waybill.json", xl_model(1, 0, 0, True))


def remap_xz(wx: int, wz: int, facing: str) -> tuple[int, int]:
    if facing == "north":
        return wx, wz
    if facing == "east":
        return wz, 1 - wx
    if facing == "south":
        return 1 - wx, 1 - wz
    if facing == "west":
        return 1 - wz, wx
    raise ValueError(facing)


def xl_index(x: int, y: int, z: int) -> int:
    if y < 2:
        return x | (y << 1) | (z << 2)
    return 8 | x | (z << 1)


def write_xl_blockstate() -> None:
    variants: dict[str, dict] = {}
    for facing, rot in Y_ROT.items():
        for y in (0, 1, 2):
            for z in (0, 1):
                for x in (0, 1):
                    mx, mz = remap_xz(x, z, facing)
                    my = y
                    segment = xl_index(x, y, z)
                    model = f"aerocouriers:block/cargo_container_xl_seg{mx}{my}{mz}"
                    waybill_model = (
                        "aerocouriers:block/cargo_container_xl_seg100_waybill"
                        if mx == 1 and my == 0 and mz == 0
                        else model
                    )
                    for fill in range(9):
                        key = f"facing={facing},segment={segment},fill={fill},waybill=false"
                        variants[key] = {"model": model, "y": rot}
                        key_w = f"facing={facing},segment={segment},fill={fill},waybill=true"
                        variants[key_w] = {"model": waybill_model, "y": rot}
    dump(BLOCKSTATES / "cargo_container_xl.json", {"variants": variants})


def write_item_models() -> None:
    dump(
        BLOCK_MODELS / "cargo_container_xl.json",
        {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": "aerocouriers:block/cargo_container_xl_side",
                "side": "aerocouriers:block/cargo_container_xl_side",
                "side_w": "aerocouriers:block/cargo_container_xl_side_waybill",
                "top": "aerocouriers:block/cargo_container_xl_top",
                "bottom": "aerocouriers:block/cargo_container_xl_bottom",
            },
            "elements": [
                {
                    "from": [3, 0.5, 3],
                    "to": [13, 15.5, 13],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#side_w"},
                        "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                    },
                }
            ],
        },
    )
    dump(ITEM_MODELS / "cargo_container_xl.json", {"parent": "aerocouriers:block/cargo_container_xl"})

    dump(
        ITEM_MODELS / "cargo_container_m.json",
        {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": "aerocouriers:block/cargo_container_m_side_lower",
                "lower": "aerocouriers:block/cargo_container_m_side_lower",
                "lower_w": "aerocouriers:block/cargo_container_m_side_lower_waybill",
                "upper": "aerocouriers:block/cargo_container_m_side_upper",
                "top": "aerocouriers:block/cargo_container_m_top",
                "bottom": "aerocouriers:block/cargo_container_m_bottom",
            },
            "elements": [
                {
                    "from": [4, 0, 4],
                    "to": [12, 8, 12],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#lower"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#lower"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#lower_w"},
                        "west": {"uv": [0, 0, 16, 16], "texture": "#lower"},
                        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                    },
                },
                {
                    "from": [4, 8, 4],
                    "to": [12, 16, 12],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#upper"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#upper"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#upper"},
                        "west": {"uv": [0, 0, 16, 16], "texture": "#upper"},
                        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                    },
                },
            ],
        },
    )

    # Inventory L: full atlas on one cube, clipboard on the east face (E2 3D).
    dump(
        BLOCK_MODELS / "cargo_container_l.json",
        {
            "parent": "minecraft:block/block",
            "textures": {
                "particle": "aerocouriers:block/cargo_container_l_side",
                "side": "aerocouriers:block/cargo_container_l_side",
                "side_w": "aerocouriers:block/cargo_container_l_side_waybill",
                "top": "aerocouriers:block/cargo_container_l_top",
                "bottom": "aerocouriers:block/cargo_container_l_bottom",
            },
            "elements": [
                {
                    "from": [0, 0, 0],
                    "to": [16, 16, 16],
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#side_w"},
                        "west": {"uv": [0, 0, 16, 16], "texture": "#side"},
                        "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                        "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                    },
                }
            ],
        },
    )


def main() -> None:
    copy_textures()
    patch_s_m_l_models()
    write_xl_models()
    write_xl_blockstate()
    write_item_models()


if __name__ == "__main__":
    main()

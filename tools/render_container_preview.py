#!/usr/bin/env python3
"""Isometric-ish previews and face sheet for cargo container textures (8x nearest)."""

from __future__ import annotations

from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/aerocouriers/textures/block"
ART = ROOT / "artifacts"
SCALE = 8
FACE = 16


def load(name: str) -> Image.Image:
    return Image.open(TEX / name).convert("RGBA")


def scale(img: Image.Image) -> Image.Image:
    return img.resize((FACE * SCALE, FACE * SCALE), Image.Resampling.NEAREST)


def compose_cube(
    top: Image.Image,
    bottom: Image.Image,
    north: Image.Image,
    south: Image.Image,
    east: Image.Image,
    west: Image.Image,
) -> Image.Image:
    """Simple 3-face isometric layout (top + north + east)."""
    s = SCALE
    w, h = FACE * s, FACE * s
    canvas = Image.new("RGBA", (w * 2 + 8, h * 2 + 8), (0x40, 0x44, 0x48, 255))
    # top diamond placement (flattened)
    canvas.paste(scale(top), (w // 2 + 4, 4))
    canvas.paste(scale(north), (4, h // 2 + 4))
    canvas.paste(scale(east), (w + 8, h // 2 + 4))
    return canvas


def face_sheet() -> Image.Image:
    names = [
        "cargo_container_top.png",
        "cargo_container_bottom.png",
        "cargo_container_side.png",
        "cargo_container_side_waybill.png",
    ]
    s = SCALE
    canvas = Image.new("RGBA", (FACE * s * len(names), FACE * s + 24), (0x30, 0x34, 0x38, 255))
    for i, name in enumerate(names):
        canvas.paste(scale(load(name)), (i * FACE * s, 12))
    return canvas


def main() -> None:
    ART.mkdir(parents=True, exist_ok=True)
    top = load("cargo_container_top.png")
    bottom = load("cargo_container_bottom.png")
    side = load("cargo_container_side.png")
    side_wb = load("cargo_container_side_waybill.png")

    plain = compose_cube(top, bottom, side, side, side, side)
    plain.save(ART / "container_preview_plain.png")

    with_wb = compose_cube(top, bottom, side, side, side_wb, side)
    with_wb.save(ART / "container_preview_waybill.png")

    face_sheet().save(ART / "container_face_sheet.png")
    print("wrote previews to", ART)


if __name__ == "__main__":
    main()

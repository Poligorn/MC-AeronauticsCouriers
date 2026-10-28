#!/usr/bin/env python3
"""Renders a static preview of the navigation HUD box for docs/artifacts."""

from __future__ import annotations

from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit("Pillow required: pip install pillow")

OUT = Path(__file__).resolve().parents[1] / "docs" / "artifacts" / "nav_hud_preview.png"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    w, h = 118, 42
    img = Image.new("RGBA", (w, h), (16, 16, 16, 160))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, w - 1, 0], fill=(139, 105, 20, 255))
    font = ImageFont.load_default()
    draw.text((6, 6), "Order #a1b2", fill=(232, 212, 139, 255), font=font)
    draw.text((6, 18), "842 m", fill=(204, 204, 204, 255), font=font)
    cx, cy = w - 28, h // 2 + 2
    draw.polygon([(cx, cy - 12), (cx - 4, cy), (cx + 4, cy)], fill=(255, 208, 96, 255))
    img.save(OUT)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()

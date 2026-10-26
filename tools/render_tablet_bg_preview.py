#!/usr/bin/env python3
"""Crop preview of TabletGui.drawScreenBackground colors (mirrors TabletGui.java constants)."""
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    raise SystemExit("pip install pillow")

SCREEN_FILL = (0x14, 0x04, 0x00)
GRID_LINE = (0x1C, 0x08, 0x04)
OLD_FILL = (0x2C, 0x09, 0x00)
OLD_GRID = (0x43, 0x1A, 0x0F)
STEP = 9
W, H = 280, 160


def draw_grid(fill, line):
    img = Image.new("RGB", (W, H), fill)
    px = img.load()
    for gx in range(0, W, STEP):
        for gy in range(H):
            px[gx, gy] = line
    for gy in range(0, H, STEP):
        for gx in range(W):
            px[gx, gy] = line
    return img


def main():
    out = Path("/opt/cursor/artifacts/tablet_bg_preview.png")
    new = draw_grid(SCREEN_FILL, GRID_LINE)
    old = draw_grid(OLD_FILL, OLD_GRID)
    combo = Image.new("RGB", (W * 2 + 8, H + 24), (32, 32, 32))
    combo.paste(old, (0, 12))
    combo.paste(new, (W + 8, 12))
    out.parent.mkdir(parents=True, exist_ok=True)
    combo.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Before/after crop of TabletGui screen fill + grid (mirrors TabletGui.java constants)."""
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    raise SystemExit("pip install pillow")

# PR #14 (too flat)
BEFORE_FILL = (0x14, 0x04, 0x00)
BEFORE_GRID = (0x1C, 0x08, 0x04)
# This branch
AFTER_FILL = (0x1E, 0x08, 0x03)
AFTER_GRID = (0x3A, 0x14, 0x0A)
STEP = 9
W, H = 200, 120


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
    out = Path("/opt/cursor/artifacts/tablet_bg_before_after.png")
    before = draw_grid(BEFORE_FILL, BEFORE_GRID)
    after = draw_grid(AFTER_FILL, AFTER_GRID)
    pad = 8
    label_h = 18
    combo = Image.new("RGB", (W * 2 + pad * 3, H + label_h + pad * 2), (48, 48, 48))
    draw = ImageDraw.Draw(combo)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 11)
    except OSError:
        font = ImageFont.load_default()
    draw.text((pad, 2), "before (#14)", fill=(200, 200, 200), font=font)
    draw.text((W + pad * 2, 2), "after (PR #15)", fill=(200, 200, 200), font=font)
    combo.paste(before, (pad, label_h + pad))
    combo.paste(after, (W + pad * 2, label_h + pad))
    out.parent.mkdir(parents=True, exist_ok=True)
    combo.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

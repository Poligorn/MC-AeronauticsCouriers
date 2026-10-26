#!/usr/bin/env python3
"""Small crop of TabletGui screen fill + grid (mirrors TabletGui.java)."""
from pathlib import Path

from PIL import Image

FILL = (0x0A, 0x02, 0x00)
GRID = (0x24, 0x10, 0x06)
STEP = 9
W, H = 120, 90


def main() -> None:
    img = Image.new("RGB", (W, H), FILL)
    px = img.load()
    for gx in range(0, W, STEP):
        for gy in range(H):
            px[gx, gy] = GRID
    for gy in range(0, H, STEP):
        for gx in range(W):
            px[gx, gy] = GRID
    out = Path("/opt/cursor/artifacts/tablet_bg_crop_120x90.png")
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()

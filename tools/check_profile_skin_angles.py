#!/usr/bin/env python3
"""Profile preview: clamped look angles and depth sanity for large windows."""

from __future__ import annotations

import math
import sys

MAX_COMPONENT = 1.0
MOUSE_DIV = 40.0


def clamp(v: float) -> float:
    return max(-MAX_COMPONENT, min(MAX_COMPONENT, v))


def angle_x(anchor_x: float, mouse_x: float) -> float:
    return clamp(math.atan((anchor_x - mouse_x) / MOUSE_DIV))


def angle_y(anchor_y: float, mouse_y: float) -> float:
    return clamp(math.atan((anchor_y - mouse_y) / MOUSE_DIV))


def check_extreme_mouse() -> None:
    anchor_x, anchor_y = 500.0, 200.0
    for mx in (-10_000, anchor_x, 10_000):
        ax = angle_x(anchor_x, mx)
        yaw = abs(ax * 40.0)
        if yaw > 40.0 + 1e-3:
            raise SystemExit(f"yaw {yaw} > 40 at mouseX={mx}")
    for my in (-10_000, anchor_y, 10_000):
        ay = angle_y(anchor_y, my)
        pitch = abs(ay * 20.0)
        if pitch > 20.0 + 1e-3:
            raise SystemExit(f"pitch {pitch} > 20 at mouseY={my}")


def check_depth_at_1000x700() -> None:
    """Rough Z extent at largest preview scale (mirrors ProfileSkinRenderer)."""
    box_h = 493
    scale = round(box_h * 0.85 / 2.05)
    z_extent = scale * 0.25 * 2.05
    anchor_z = 150.0
    if anchor_z - z_extent < 10.0:
        raise SystemExit(f"near clip risk: anchorZ - extent = {anchor_z - z_extent}")
    print(f"1000x700: scale={scale} zHalfExtent≈{z_extent:.1f} anchorZ={anchor_z}")


def main() -> int:
    check_extreme_mouse()
    check_depth_at_1000x700()
    print("profile skin angles: ok")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

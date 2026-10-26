#!/usr/bin/env python3
"""Numeric margin check for profile skin preview (mirrors ProfileSkinPreviewLayout)."""

from __future__ import annotations

import sys

PLAYER_VISUAL_HEIGHT_BLOCKS = 2.05
BODY_HEIGHT_FRAC = 0.85
FEET_MARGIN_FRAC = 0.06
HEAD_MARGIN_FRAC = 0.07
FRAME_BORDER = 8
CONTENT_INSET = 9
ANTENNA_OVERHANG_TOP = 22
GEAR_OVERHANG_LEFT = 14
TAB_Y = 2
TAB_H = 20
HEADING_GAP = 7
SCREEN_BOTTOM_PAD = 12


def panel_w(width: int) -> int:
    margin = 24 + GEAR_OVERHANG_LEFT
    return min(max(int(width * 0.72), 400), max(400, width - margin))


def panel_h(height: int) -> int:
    margin = 24 + ANTENNA_OVERHANG_TOP
    return min(max(int(height * 0.80), 280), max(280, height - margin))


def frame_top(screen_h: int, frame_h: int) -> int:
    total_h = frame_h + ANTENNA_OVERHANG_TOP
    return max(ANTENNA_OVERHANG_TOP + 2, (screen_h - total_h) // 2 + ANTENNA_OVERHANG_TOP)


def list_top(screen_top: int) -> int:
    return screen_top + TAB_Y + TAB_H + HEADING_GAP + 10


def profile_box_h(screen_top: int, screen_h: int) -> int:
    bottom = screen_top + screen_h - SCREEN_BOTTOM_PAD
    return max(48, bottom - list_top(screen_top))


def layout(panel_top: int, box_h: int, entity_scale: float = 1.0) -> tuple[int, int, int, int]:
    target_body = box_h * BODY_HEIGHT_FRAC
    scale = max(28, min(220, round(target_body / PLAYER_VISUAL_HEIGHT_BLOCKS)))
    render_scale = scale / entity_scale
    feet_y = panel_top + box_h * (1.0 - FEET_MARGIN_FRAC)
    head_top = round(feet_y - render_scale * PLAYER_VISUAL_HEIGHT_BLOCKS)
    feet_bottom = round(feet_y)
    return scale, head_top, feet_bottom, box_h


def margins_ok(panel_top: int, box_h: int, head_top: int, feet_bottom: int) -> bool:
    head_margin = head_top - panel_top
    feet_margin = panel_top + box_h - feet_bottom
    return head_margin >= round(box_h * HEAD_MARGIN_FRAC) and feet_margin >= round(box_h * FEET_MARGIN_FRAC)


def check_window(width: int, height: int) -> None:
    ph = panel_h(height)
    pw = panel_w(width)
    screen_top = frame_top(height, ph) + FRAME_BORDER + CONTENT_INSET
    screen_h = ph - FRAME_BORDER * 2 - CONTENT_INSET * 2
    top = list_top(screen_top)
    box_h = profile_box_h(screen_top, screen_h)
    scale, head_top, feet_bottom, _ = layout(top, box_h)
    ok = margins_ok(top, box_h, head_top, feet_bottom)
    print(
        f"{width}x{height}: panelTop={top} boxH={box_h} scale={scale} "
        f"headTop={head_top} feetBottom={feet_bottom} ok={ok}"
    )
    if not ok:
        raise SystemExit(1)


def main() -> int:
    for w, h in ((400, 240), (480, 300), (560, 400), (1000, 700)):
        check_window(w, h)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

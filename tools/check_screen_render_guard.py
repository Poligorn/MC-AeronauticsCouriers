#!/usr/bin/env python3
"""Guards against Screen render-order bugs on NeoForge 1.21.1."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/main/java/dev/poligorn/aerocouriers/client"

RENDER_METHOD = re.compile(
    r"public\s+void\s+render\s*\(\s*GuiGraphics\s+\w+[^)]*\)\s*\{(.*)",
    re.DOTALL,
)

CHROME_BEFORE_SUPER = (
    "WaybillCreateGui.drawWoodenFrame",
    "WaybillCreateGui.drawPaperSheet",
    "TabletGui.drawScreenBackground",
    "TabletGui.drawFrameChrome",
    "TabletGui.drawListPanel",
)


def main() -> int:
    failures: list[str] = []
    for path in sorted(CLIENT.glob("*Screen.java")):
        text = path.read_text(encoding="utf-8")
        if "renderWithTooltip" in text:
            failures.append(
                f"{path.relative_to(ROOT)}: Screen must not call renderWithTooltip; use super.render() instead"
            )
        match = RENDER_METHOD.search(text)
        if not match:
            continue
        body = match.group(1)
        super_idx = body.find("super.render(")
        if super_idx < 0:
            continue
        prefix = body[:super_idx]
        for token in CHROME_BEFORE_SUPER:
            if token in prefix:
                failures.append(
                    f"{path.relative_to(ROOT)}: draw {token} in renderBackground, not before super.render()"
                )
    if failures:
        print("Screen render guard failed:", file=sys.stderr)
        for line in failures:
            print(f"  {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

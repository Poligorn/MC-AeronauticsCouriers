#!/usr/bin/env python3
"""Fail if a Screen subclass calls renderWithTooltip (1.21.1: it calls render() and recurses)."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/main/java/dev/poligorn/aerocouriers/client"


def main() -> int:
    failures: list[str] = []
    for path in sorted(CLIENT.glob("*Screen.java")):
        text = path.read_text(encoding="utf-8")
        if "renderWithTooltip" in text:
            failures.append(
                f"{path.relative_to(ROOT)}: Screen must not call renderWithTooltip; use super.render() instead"
            )
    if failures:
        print("Screen render guard failed:", file=sys.stderr)
        for line in failures:
            print(f"  {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

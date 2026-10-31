#!/usr/bin/env python3
"""Cork-board GUI sprites are shipped from cork_board_assets_v2.

This script used to generate an in-repo atlas. It is now a no-op so a rebuild
cannot overwrite the hand-authored 1:1 GUI-pixel sprites under
src/main/resources/assets/aerocouriers/textures/gui/sprites/cork/.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/aerocouriers/textures/gui/sprites/cork"


def main() -> int:
    count = len(list(OUT.rglob("*.png"))) if OUT.is_dir() else 0
    print(
        "gen_cork_board_textures.py is disabled. "
        f"Keeping {count} shipped cork sprites in {OUT.relative_to(ROOT)}."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

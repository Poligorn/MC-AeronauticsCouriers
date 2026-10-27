#!/usr/bin/env python3
"""Fails if en_us and ru_ru lang files differ in keys or obvious language mix-ups."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/aerocouriers/lang"
EN_PATH = LANG / "en_us.json"
RU_PATH = LANG / "ru_ru.json"

CYRILLIC = re.compile(r"[\u0400-\u04FF]")
# Same value in RU as EN is OK for these (brands, coords, symbols).
IDENTICAL_OK = frozenset(
    {
        "key.categories.aerocouriers",
        "ui.aerocouriers.open",
        "ui.aerocouriers.create.coord.x",
        "ui.aerocouriers.create.coord.y",
        "ui.aerocouriers.create.coord.z",
        "ui.aerocouriers.create.no_station",
        "ui.aerocouriers.create.no_waypoint",
        "ui.aerocouriers.profile.dash",
    }
)
LATIN_WORD = re.compile(r"[A-Za-z]{4,}")


def load(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict):
        raise TypeError(f"{path}: expected JSON object")
    return data


def main() -> int:
    failures: list[str] = []
    en = load(EN_PATH)
    ru = load(RU_PATH)
    en_keys = set(en)
    ru_keys = set(ru)

    only_en = sorted(en_keys - ru_keys)
    only_ru = sorted(ru_keys - en_keys)
    if only_en:
        failures.append(f"Keys only in en_us.json ({len(only_en)}): " + ", ".join(only_en[:20]))
    if only_ru:
        failures.append(f"Keys only in ru_ru.json ({len(only_ru)}): " + ", ".join(only_ru[:20]))

    for key, value in en.items():
        if CYRILLIC.search(value):
            failures.append(f"en_us.json[{key}] contains Cyrillic: {value!r}")

    for key in sorted(en_keys & ru_keys):
        if key in IDENTICAL_OK:
            continue
        ev, rv = en[key], ru[key]
        if ev == rv and LATIN_WORD.search(rv):
            failures.append(f"ru_ru.json[{key}] still English (same as en_us): {rv!r}")

    if failures:
        print("Lang parity check failed:\n", file=sys.stderr)
        for line in failures:
            print(f"  - {line}", file=sys.stderr)
        return 1

    print(f"OK: {len(en_keys)} keys match in en_us.json and ru_ru.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
